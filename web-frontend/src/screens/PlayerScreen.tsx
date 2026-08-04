import React, { useEffect, useState } from 'react';
import type { ExtractorLinkDto, PlayableStreamDto, SubtitleFileDto } from '../api/types';
import api from '../api/client';
import VideoPlayer from '../components/VideoPlayer';

interface PlayerScreenProps {
  title: string;
  subtitleText?: string;
  availableLinks: ExtractorLinkDto[];
  subtitles: SubtitleFileDto[];
  provider: string;
  episodeDataUrl: string;
  /** The show/movie's own page URL — used as the watch-history key so "Continue Watching" on
   * the details page can find this title regardless of which episode was actually played. */
  seriesUrl: string;
  posterUrl?: string;
  season?: number;
  episode?: number;
  startPositionMs?: number;
  onBack: () => void;
}

export const PlayerScreen: React.FC<PlayerScreenProps> = ({
  title,
  subtitleText,
  availableLinks,
  subtitles,
  provider,
  episodeDataUrl,
  seriesUrl,
  posterUrl,
  season,
  episode,
  startPositionMs,
  onBack,
}) => {
  const [selectedLink, setSelectedLink] = useState<ExtractorLinkDto | null>(null);
  const [linkInitialized, setLinkInitialized] = useState<boolean>(false);
  const [preferredSourceName, setPreferredSourceName] = useState<string | null>(null);
  const [resolvedStream, setResolvedStream] = useState<PlayableStreamDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Pick the initial link: prefer whatever's been remembered for this show (see the "Remember
  // choice" control in VideoPlayer), falling back to the first available link if that source
  // isn't among this episode's links (or nothing's been remembered at all).
  useEffect(() => {
    let cancelled = false;

    async function pickInitialLink() {
      if (availableLinks.length === 0) {
        if (!cancelled) {
          setSelectedLink(null);
          setLinkInitialized(true);
        }
        return;
      }

      let pref: string | null = null;
      try {
        const dto = await api.getPreferredSource(provider, seriesUrl);
        pref = dto?.sourceName ?? null;
      } catch (err) {
        console.error('Failed to load preferred source:', err);
      }

      if (cancelled) return;
      setPreferredSourceName(pref);
      const match = pref ? availableLinks.find((l) => l.source === pref) : undefined;
      setSelectedLink(match || availableLinks[0]);
      setLinkInitialized(true);
    }

    pickInitialLink();
    return () => {
      cancelled = true;
    };
  }, [availableLinks, provider, seriesUrl]);

  const handleSetPreferredSource = async (sourceName: string) => {
    try {
      await api.setPreferredSource({ provider, seriesUrl, sourceName });
      setPreferredSourceName(sourceName);
    } catch (err) {
      console.error('Failed to remember source choice:', err);
    }
  };

  const handleClearPreferredSource = async () => {
    try {
      await api.clearPreferredSource(provider, seriesUrl);
      setPreferredSourceName(null);
    } catch (err) {
      console.error('Failed to forget source choice:', err);
    }
  };

  // Resolve link through Ktor proxy API /api/resolve
  useEffect(() => {
    if (!linkInitialized) return;

    if (!selectedLink) {
      setError('No playable stream links found for this title.');
      setLoading(false);
      return;
    }

    async function resolveCurrentLink() {
      setLoading(true);
      setError(null);
      try {
        const stream = await api.resolve(selectedLink!);
        setResolvedStream(stream);
      } catch (err: any) {
        console.error('Failed to resolve link:', err);
        setError(err.message || 'Failed to resolve stream URL');
      } finally {
        setLoading(false);
      }
    }

    resolveCurrentLink();
  }, [selectedLink, linkInitialized]);

  const handleProgressUpdate = (positionMs: number, durationMs: number) => {
    if (positionMs <= 0 || durationMs <= 0) return;
    // Throttle progress updates to backend
    api.patchHistory({
      provider,
      url: seriesUrl,
      name: title,
      posterUrl,
      episodeData: episodeDataUrl,
      season,
      episode,
      positionMs: Math.floor(positionMs),
      durationMs: Math.floor(durationMs),
      updatedAt: Date.now(),
    }).catch(() => {});
  };

  if (loading) {
    return (
      <div
        style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: '#000',
          zIndex: 9999,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <div className="spinner" />
        <p style={{ marginTop: '20px', color: 'var(--text-muted)' }}>
          Resolving stream link ({selectedLink?.name || 'Loading'})...
        </p>
      </div>
    );
  }

  if (error || !resolvedStream) {
    return (
      <div
        style={{
          position: 'fixed',
          inset: 0,
          backgroundColor: '#000',
          zIndex: 9999,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '24px',
          textAlign: 'center',
        }}
      >
        <p style={{ color: 'var(--accent-pink)', fontSize: '1.2rem', fontWeight: 600, marginBottom: '20px' }}>
          {error || 'Playback Error'}
        </p>
        <div style={{ display: 'flex', gap: '16px' }}>
          <button className="btn btn-secondary" onClick={onBack}>
            Go Back
          </button>
          {availableLinks.length > 1 && (
            <button
              className="btn btn-primary"
              onClick={() => {
                const nextIndex = (availableLinks.findIndex((l) => l.url === selectedLink?.url) + 1) % availableLinks.length;
                setSelectedLink(availableLinks[nextIndex]);
              }}
            >
              Try Next Link
            </button>
          )}
        </div>
      </div>
    );
  }

  return (
    <VideoPlayer
      streamInfo={resolvedStream}
      availableLinks={availableLinks}
      subtitles={subtitles}
      selectedLink={selectedLink}
      onSelectLink={setSelectedLink}
      title={title}
      subtitleText={subtitleText}
      startPositionMs={startPositionMs}
      onBack={onBack}
      onProgressUpdate={handleProgressUpdate}
      onResolveAt={(startSeconds) => api.resolve(selectedLink!, startSeconds)}
      preferredSourceName={preferredSourceName}
      onSetPreferredSource={handleSetPreferredSource}
      onClearPreferredSource={handleClearPreferredSource}
    />
  );
};

export default PlayerScreen;
