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
} from 'lucide-react';
import type { ExtractorLinkDto, PlayableStreamDto, SubtitleFileDto } from '../api/types';


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

  const controlsTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);


  // Auto-hide controls timer
  const handleMouseMove = () => {
    setShowControls(true);
    if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
    controlsTimeoutRef.current = setTimeout(() => {
      if (isPlaying) setShowControls(false);
    }, 3500);
  };

  // Initialize Video & HLS
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !streamInfo) return;

    if (hlsRef.current) {
      hlsRef.current.destroy();
      hlsRef.current = null;
    }
    if (dashRef.current) {
      dashRef.current.destroy();
      dashRef.current = null;
    }

    const isHls =
      streamInfo.kind === 'HLS' ||
      streamInfo.mimeType.includes('mpegurl') ||
      streamInfo.proxyUrl.includes('.m3u8');

    const isDash =
      streamInfo.kind === 'DASH' ||
      streamInfo.mimeType.includes('dash+xml') ||
      streamInfo.proxyUrl.includes('.mpd');

    if (isHls && Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
      });
      hlsRef.current = hls;
      hls.loadSource(streamInfo.proxyUrl);
      hls.attachMedia(video);

      hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (startPositionMs > 0) {
          video.currentTime = startPositionMs / 1000;
        }
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
      dash.initialize(video, streamInfo.proxyUrl, false);

      dash.on(dashjs.MediaPlayer.events.CAN_PLAY, () => {
        if (startPositionMs > 0) {
          video.currentTime = startPositionMs / 1000;
        }
        video.play().catch(() => setIsPlaying(false));
      });

      dash.on(dashjs.MediaPlayer.events.ERROR, (data) => {
        console.error('Fatal DASH error:', data);
      });
    } else {
      video.src = streamInfo.proxyUrl;
      video.onloadedmetadata = () => {
        if (startPositionMs > 0) {
          video.currentTime = startPositionMs / 1000;
        }
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
  }, [streamInfo]);

  // Video event handlers & position progress reporting
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const onTimeUpdate = () => {
      setCurrentTime(video.currentTime);
      setDuration(video.duration || 0);
      if (onProgressUpdate && video.duration > 0) {
        onProgressUpdate(video.currentTime * 1000, video.duration * 1000);
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
  }, [onProgressUpdate]);

  const togglePlay = () => {
    const video = videoRef.current;
    if (!video) return;
    if (isPlaying) {
      video.pause();
    } else {
      video.play();
    }
  };

  const handleSeek = (e: React.ChangeEvent<HTMLInputElement>) => {
    const video = videoRef.current;
    if (!video) return;
    const seekTime = parseFloat(e.target.value);
    video.currentTime = seekTime;
    setCurrentTime(seekTime);
  };

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
            style={{
              width: '100%',
              height: '6px',
              accentColor: 'var(--accent-primary)',
              cursor: 'pointer',
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
