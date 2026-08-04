import React, { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import * as dashjs from 'dashjs';
import {
  Play,
  Pause,
  Volume2,
  VolumeX,
  Maximize,
  Minimize,
  Settings,
  Subtitles,
  SkipForward,
  SkipBack,
  ArrowLeft,
  Star,
} from 'lucide-react';
import type { ExtractorLinkDto, PlayableStreamDto, SubtitleFileDto } from '../api/types';

// How long to wait after the user stops dragging the seek bar before restarting the transcode
// at the new position — otherwise every tick of a drag across untranscoded territory would fire
// its own resolve + ffmpeg restart.
const SEEK_DEBOUNCE_MS = 400;

interface VideoPlayerProps {
  streamInfo: PlayableStreamDto | null;
  availableLinks: ExtractorLinkDto[];
  subtitles: SubtitleFileDto[];
  selectedLink: ExtractorLinkDto | null;
  onSelectLink: (link: ExtractorLinkDto) => void;
  title: string;
  subtitleText?: string;
  startPositionMs?: number;
  onBack: () => void;
  onNextEpisode?: () => void;
  onPrevEpisode?: () => void;
  onProgressUpdate?: (positionMs: number, durationMs: number) => void;
  /** Re-resolves the current link starting at [startSeconds] — used when seeking past what a
   * transcoded stream (see PlayableStreamDto.durationSeconds) has produced so far. */
  onResolveAt?: (startSeconds: number) => Promise<PlayableStreamDto>;
  /** The source name (ExtractorLinkDto.source) remembered for this show, if any — drives the
   * "Remember choice" control's filled/outline star state. */
  preferredSourceName?: string | null;
  onSetPreferredSource?: (sourceName: string) => void;
  onClearPreferredSource?: () => void;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  streamInfo,
  availableLinks,
  subtitles,
  selectedLink,
  onSelectLink,
  title,
  subtitleText,
  startPositionMs = 0,
  onBack,
  onNextEpisode,
  onPrevEpisode,
  onProgressUpdate,
  onResolveAt,
  preferredSourceName,
  onSetPreferredSource,
  onClearPreferredSource,
}) => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const hlsRef = useRef<Hls | null>(null);
  const dashRef = useRef<dashjs.MediaPlayerClass | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const [isPlaying, setIsPlaying] = useState<boolean>(true);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(0);
  const [volume, setVolume] = useState<number>(1);
  const [isMuted, setIsMuted] = useState<boolean>(false);
  const [isFullscreen, setIsFullscreen] = useState<boolean>(false);
  const [showControls, setShowControls] = useState<boolean>(true);
  const [showQualityMenu, setShowQualityMenu] = useState<boolean>(false);
  const [showSubtitleMenu, setShowSubtitleMenu] = useState<boolean>(false);
  const [selectedSubtitle, setSelectedSubtitle] = useState<SubtitleFileDto | null>(null);

  // Virtual timeline for transcoded streams: the growing HLS playlist only knows about the
  // portion transcoded so far, so we track the *real* total duration and the offset the
  // currently-loaded stream's local time 0 represents, letting the seek bar show/seek across
  // the whole runtime immediately (like a normal VOD) instead of just what's been produced.
  const [internalStream, setInternalStream] = useState<PlayableStreamDto | null>(streamInfo);
  const [sessionStartOffsetSec, setSessionStartOffsetSec] = useState<number>(startPositionMs / 1000);
  const [knownDurationSec, setKnownDurationSec] = useState<number | null>(streamInfo?.durationSeconds ?? null);
  const [isSeekPending, setIsSeekPending] = useState<boolean>(false);
  const isInitialLoadRef = useRef<boolean>(true);

  const controlsTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const seekDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // A fresh link selection from the parent replaces the whole session (offset back to the
  // resume position, duration back to whatever the new stream reports).
  useEffect(() => {
    isInitialLoadRef.current = true;
    setInternalStream(streamInfo);
    setSessionStartOffsetSec(startPositionMs / 1000);
    setKnownDurationSec(streamInfo?.durationSeconds ?? null);
  }, [streamInfo]);

  // Show the full known duration immediately, rather than waiting for the first timeupdate tick.
  useEffect(() => {
    if (knownDurationSec != null) setDuration(knownDurationSec);
  }, [knownDurationSec]);


  // Auto-hide controls timer
  const handleMouseMove = () => {
    setShowControls(true);
    if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
    controlsTimeoutRef.current = setTimeout(() => {
      if (isPlaying) setShowControls(false);
    }, 3500);
  };

  // Initialize Video & HLS/DASH — keyed on internalStream (not the streamInfo prop directly) so
  // a reseek-triggered re-resolve (new proxyUrl, same "session") reloads exactly like a fresh
  // link selection does, just without re-applying the original resume position.
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !internalStream) return;

    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }
    if (dashRef.current) {
      dashRef.current.destroy();
      dashRef.current = null;
    }

    const isHls =
      internalStream.kind === 'HLS' ||
      internalStream.mimeType.includes('mpegurl') ||
      internalStream.proxyUrl.includes('.m3u8');

    const isDash =
      internalStream.kind === 'DASH' ||
      internalStream.mimeType.includes('dash+xml') ||
      internalStream.proxyUrl.includes('.mpd');

    const applyResumePosition = () => {
      if (isInitialLoadRef.current && startPositionMs > 0) {
        video.currentTime = startPositionMs / 1000;
      }
      isInitialLoadRef.current = false;
    };

    // Only our own transcoder produces a growing "event" playlist — every other HLS source
    // (untouched, browser-compatible streams) is a normal VOD playlist and should behave exactly
    // as before.
    const isTranscodedGrowingPlaylist = internalStream.durationSeconds != null;

    if (isHls && Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        // Growing "event" playlists (our transcoder) aren't a real live edge — low-latency mode
        // fights the buffer instead of just letting it build up, which caused extra stalling.
        // Normal HLS sources keep the original low-latency behavior.
        lowLatencyMode: !isTranscodedGrowingPlaylist,
      });
      hlsRef.current = hls;
      hls.loadSource(internalStream.proxyUrl);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        applyResumePosition();
        video.play().catch(() => setIsPlaying(false));
      });

      hls.on(Hls.Events.ERROR, (_, data) => {
        if (data.fatal) {
          console.error('Fatal HLS error:', data);
        }
      });
    } else if (isDash) {
      const dash = dashjs.MediaPlayer().create();
      dashRef.current = dash;
      dash.initialize(video, internalStream.proxyUrl, false);

      dash.on(dashjs.MediaPlayer.events.CAN_PLAY, () => {
        applyResumePosition();
        video.play().catch(() => setIsPlaying(false));
      });

      dash.on(dashjs.MediaPlayer.events.ERROR, (data) => {
        console.error('Fatal DASH error:', data);
      });
    } else {
      video.src = internalStream.proxyUrl;
      video.onloadedmetadata = () => {
        applyResumePosition();
        video.play().catch(() => setIsPlaying(false));
      };
    }

    return () => {
      if (hlsRef.current) {
        hlsRef.current.destroy();
        hlsRef.current = null;
      }
      if (dashRef.current) {
        dashRef.current.destroy();
        dashRef.current = null;
      }
    };
  }, [internalStream]);

  // Video event handlers & position progress reporting. For transcoded streams (knownDurationSec
  // set), currentTime/duration are reported in *global* timeline seconds (sessionStartOffsetSec +
  // the active stream's local time) so the seek bar/progress-save code never needs to know a
  // reseek happened underneath it. For every other (already browser-compatible) stream,
  // video.currentTime is already the real absolute position — reported as-is, unchanged from
  // before this feature existed.
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const onTimeUpdate = () => {
      const globalTime = knownDurationSec != null ? sessionStartOffsetSec + video.currentTime : video.currentTime;
      const totalDuration = knownDurationSec ?? video.duration;
      setCurrentTime(globalTime);
      setDuration(totalDuration || 0);
      if (onProgressUpdate && totalDuration > 0) {
        onProgressUpdate(globalTime * 1000, totalDuration * 1000);
      }
    };

    const onPlay = () => setIsPlaying(true);
    const onPause = () => setIsPlaying(false);

    video.addEventListener('timeupdate', onTimeUpdate);
    video.addEventListener('play', onPlay);
    video.addEventListener('pause', onPause);

    return () => {
      video.removeEventListener('timeupdate', onTimeUpdate);
      video.removeEventListener('play', onPlay);
      video.removeEventListener('pause', onPause);
    };
  }, [onProgressUpdate, sessionStartOffsetSec, knownDurationSec]);

  const togglePlay = () => {
    const video = videoRef.current;
    if (!video) return;
    if (isPlaying) {
      video.pause();
    } else {
      video.play();
    }
  };

  const performOutOfBufferSeek = async (targetGlobal: number) => {
    if (!onResolveAt) return;
    setIsSeekPending(true);
    try {
      const newStream = await onResolveAt(targetGlobal);
      isInitialLoadRef.current = false; // this is a reseek, not the original resume position
      setSessionStartOffsetSec(targetGlobal);
      setInternalStream(newStream);
    } catch (err) {
      console.error('Failed to seek to a not-yet-transcoded position:', err);
    } finally {
      setIsSeekPending(false);
    }
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    const video = videoRef.current;
    if (!video) return;
    const targetGlobal = parseFloat(e.target.value);

    // Any drag tick supersedes a previously scheduled restart — only the position the user
    // settles on should actually trigger one.
    if (seekDebounceRef.current) {
      clearTimeout(seekDebounceRef.current);
      seekDebounceRef.current = null;
    }

    // Not a transcoded/growing stream — no virtual timeline involved, seek exactly as before.
    if (knownDurationSec == null) {
      video.currentTime = targetGlobal;
      setCurrentTime(targetGlobal);
      return;
    }

    const localTarget = targetGlobal - sessionStartOffsetSec;
    const seekable = video.seekable;
    const withinBuffered =
      seekable.length > 0 && localTarget >= seekable.start(0) && localTarget <= seekable.end(seekable.length - 1);

    setCurrentTime(targetGlobal); // reflect the drag immediately either way

    if (withinBuffered) {
      // Already transcoded — instant seek, no restart needed.
      video.currentTime = localTarget;
      return;
    }

    // Past what's been transcoded so far (or before the current session's start). Debounce
    // restarting the transcode until the drag settles, rather than firing a resolve + ffmpeg
    // restart on every tick while scrubbing across untranscoded territory.
    seekDebounceRef.current = setTimeout(() => {
      seekDebounceRef.current = null;
      performOutOfBufferSeek(targetGlobal);
    }, SEEK_DEBOUNCE_MS);
  };

  // Don't let a pending debounced seek fire (and setState) after the component's gone.
  useEffect(() => {
    return () => {
      if (seekDebounceRef.current) clearTimeout(seekDebounceRef.current);
    };
  }, []);

  const handleVolumeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const video = videoRef.current;
    if (!video) return;
    const val = parseFloat(e.target.value);
    video.volume = val;
    setVolume(val);
    setIsMuted(val === 0);
  };

  const toggleMute = () => {
    const video = videoRef.current;
    if (!video) return;
    video.muted = !isMuted;
    setIsMuted(!isMuted);
  };

  const toggleFullscreen = () => {
    if (!containerRef.current) return;
    if (!document.fullscreenElement) {
      containerRef.current.requestFullscreen();
      setIsFullscreen(true);
    } else {
      document.exitFullscreen();
      setIsFullscreen(false);
    }
  };

  const formatTime = (seconds: number) => {
    if (isNaN(seconds) || seconds < 0) return '00:00';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    if (h > 0) {
      return `${h}:${m < 10 ? '0' : ''}${m}:${s < 10 ? '0' : ''}${s}`;
    }
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  return (
    <div
      ref={containerRef}
      onMouseMove={handleMouseMove}
      style={{
        position: 'fixed',
        inset: 0,
        backgroundColor: '#000',
        zIndex: 9999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        userSelect: 'none',
      }}
    >
      {/* HTML5 Video Element */}
      <video
        ref={videoRef}
        onClick={togglePlay}
        style={{ width: '100%', height: '100%', objectFit: 'contain' }}
        crossOrigin="anonymous"
      >
        {selectedSubtitle && (
          <track
            kind="subtitles"
            src={selectedSubtitle.url}
            srcLang={selectedSubtitle.lang}
            label={selectedSubtitle.lang}
            default
          />
        )}
      </video>

      {/* Seek-beyond-buffer overlay: shown while the backend restarts the transcode from the new position */}
      {isSeekPending && (
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '16px',
            backgroundColor: 'rgba(0,0,0,0.55)',
            pointerEvents: 'none',
          }}
        >
          <div className="spinner" />
          <p style={{ color: '#fff', fontSize: '0.9rem' }}>Seeking...</p>
        </div>
      )}

      {/* Top Overlay Bar */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          padding: '20px 28px',
          background: 'linear-gradient(to bottom, rgba(0,0,0,0.85) 0%, transparent 100%)',
          display: 'flex',
          alignItems: 'center',
          gap: '16px',
          opacity: showControls ? 1 : 0,
          transition: 'opacity 0.3s ease',
          pointerEvents: showControls ? 'auto' : 'none',
        }}
      >
        <button className="btn btn-secondary btn-icon" onClick={onBack} title="Back">
          <ArrowLeft size={20} />
        </button>
        <div>
          <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#fff' }}>{title}</h2>
          {subtitleText && (
            <p style={{ fontSize: '0.88rem', color: 'var(--text-muted)' }}>{subtitleText}</p>
          )}
        </div>
      </div>

      {/* Center Controls (Play / Prev / Next) when paused or hovered */}
      {showControls && (
        <div
          style={{
            position: 'absolute',
            display: 'flex',
            alignItems: 'center',
            gap: '24px',
            pointerEvents: 'auto',
          }}
        >
          {onPrevEpisode && (
            <button className="btn btn-secondary btn-icon" onClick={onPrevEpisode} title="Previous Episode">
              <SkipBack size={22} />
            </button>
          )}
          <button
            className="btn btn-primary btn-icon"
            style={{ width: 64, height: 64 }}
            onClick={togglePlay}
          >
            {isPlaying ? <Pause size={30} /> : <Play size={30} style={{ marginLeft: 4 }} />}
          </button>
          {onNextEpisode && (
            <button className="btn btn-secondary btn-icon" onClick={onNextEpisode} title="Next Episode">
              <SkipForward size={22} />
            </button>
          )}
        </div>
      )}

      {/* Bottom Overlay Controls */}
      <div
        style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          padding: '24px 28px',
          background: 'linear-gradient(to top, rgba(0,0,0,0.9) 0%, transparent 100%)',
          display: 'flex',
          flexDirection: 'column',
          gap: '12px',
          opacity: showControls ? 1 : 0,
          transition: 'opacity 0.3s ease',
          pointerEvents: showControls ? 'auto' : 'none',
        }}
      >
        {/* Progress Bar */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <input
            type="range"
            min={0}
            max={duration || 100}
            value={currentTime}
            onChange={handleSeek}
            disabled={isSeekPending}
            style={{
              width: '100%',
              height: '6px',
              accentColor: 'var(--accent-primary)',
              cursor: isSeekPending ? 'default' : 'pointer',
            }}
          />
        </div>

        {/* Control Bar Actions */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            <button className="btn btn-secondary btn-icon" onClick={togglePlay}>
              {isPlaying ? <Pause size={18} /> : <Play size={18} />}
            </button>

            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <button className="btn btn-secondary btn-icon" onClick={toggleMute}>
                {isMuted || volume === 0 ? <VolumeX size={18} /> : <Volume2 size={18} />}
              </button>
              <input
                type="range"
                min={0}
                max={1}
                step={0.05}
                value={isMuted ? 0 : volume}
                onChange={handleVolumeChange}
                style={{ width: '80px', accentColor: 'var(--accent-primary)' }}
              />
            </div>

            <span style={{ fontSize: '0.88rem', color: 'var(--text-muted)', fontFamily: 'monospace' }}>
              {formatTime(currentTime)} / {formatTime(duration)}
            </span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', position: 'relative' }}>
            {/* Subtitles Button */}
            {subtitles.length > 0 && (
              <div style={{ position: 'relative' }}>
                <button
                  className="btn btn-secondary btn-icon"
                  onClick={() => {
                    setShowSubtitleMenu(!showSubtitleMenu);
                    setShowQualityMenu(false);
                  }}
                  title="Subtitles"
                  style={{ color: selectedSubtitle ? 'var(--accent-cyan)' : 'inherit' }}
                >
                  <Subtitles size={18} />
                </button>

                {showSubtitleMenu && (
                  <div
                    className="glass-panel"
                    style={{
                      position: 'absolute',
                      bottom: '50px',
                      right: 0,
                      width: '200px',
                      maxHeight: '240px',
                      overflowY: 'auto',
                      padding: '8px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '4px',
                      zIndex: 100,
                    }}
                  >
                    <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', padding: '4px 8px' }}>
                      SUBTITLES
                    </div>
                    <button
                      className={`btn btn-secondary ${!selectedSubtitle ? 'btn-primary' : ''}`}
                      style={{ justifyContent: 'flex-start', padding: '6px 12px', fontSize: '0.85rem' }}
                      onClick={() => {
                        setSelectedSubtitle(null);
                        setShowSubtitleMenu(false);
                      }}
                    >
                      Off
                    </button>
                    {subtitles.map((sub, idx) => (
                      <button
                        key={idx}
                        className={`btn btn-secondary ${selectedSubtitle?.url === sub.url ? 'btn-primary' : ''}`}
                        style={{ justifyContent: 'flex-start', padding: '6px 12px', fontSize: '0.85rem' }}
                        onClick={() => {
                          setSelectedSubtitle(sub);
                          setShowSubtitleMenu(false);
                        }}
                      >
                        {sub.lang}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Stream Quality Selector */}
            {availableLinks.length > 0 && (
              <div style={{ position: 'relative' }}>
                <button
                  className="btn btn-secondary"
                  onClick={() => {
                    setShowQualityMenu(!showQualityMenu);
                    setShowSubtitleMenu(false);
                  }}
                  style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                >
                  <Settings size={16} />
                  <span>{selectedLink?.name || 'Quality'}</span>
                </button>

                {showQualityMenu && (
                  <div
                    className="glass-panel"
                    style={{
                      position: 'absolute',
                      bottom: '50px',
                      right: 0,
                      width: '240px',
                      maxHeight: '260px',
                      overflowY: 'auto',
                      padding: '8px',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: '4px',
                      zIndex: 100,
                    }}
                  >
                    <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', padding: '4px 8px' }}>
                      STREAM SOURCES
                    </div>
                    {availableLinks.map((link, idx) => (
                      <button
                        key={idx}
                        className={`btn btn-secondary ${selectedLink?.url === link.url ? 'btn-primary' : ''}`}
                        style={{ justifyContent: 'flex-start', padding: '6px 12px', fontSize: '0.85rem', textAlign: 'left' }}
                        onClick={() => {
                          onSelectLink(link);
                          setShowQualityMenu(false);
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: 600 }}>{link.name}</div>
                          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                            {link.source} • {link.quality}p
                          </div>
                        </div>
                      </button>
                    ))}

                    {selectedLink && (onSetPreferredSource || onClearPreferredSource) && (
                      <>
                        <div style={{ height: 1, background: 'rgba(255,255,255,0.1)', margin: '4px 0' }} />
                        <button
                          className="btn btn-secondary"
                          style={{
                            justifyContent: 'flex-start',
                            padding: '6px 12px',
                            fontSize: '0.85rem',
                            color: preferredSourceName === selectedLink.source ? 'var(--accent-cyan)' : 'inherit',
                          }}
                          onClick={() => {
                            if (preferredSourceName === selectedLink.source) {
                              onClearPreferredSource?.();
                            } else {
                              onSetPreferredSource?.(selectedLink.source);
                            }
                          }}
                          title="Remember this source so future episodes of this title default to it"
                        >
                          <Star size={14} fill={preferredSourceName === selectedLink.source ? 'currentColor' : 'none'} />
                          <span>
                            {preferredSourceName === selectedLink.source ? 'Remembered for this title' : 'Remember this choice'}
                          </span>
                        </button>
                      </>
                    )}
                  </div>
                )}
              </div>
            )}

            <button className="btn btn-secondary btn-icon" onClick={toggleFullscreen} title="Fullscreen">
              {isFullscreen ? <Minimize size={18} /> : <Maximize size={18} />}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default VideoPlayer;
