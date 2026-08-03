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
  onBack: () => void;
}

export const PlayerScreen: React.FC<PlayerScreenProps> = ({
  title,
  subtitleText,
  availableLinks,
  subtitles,
  provider,
  episodeDataUrl,
  onBack,
}) => {
  const [selectedLink, setSelectedLink] = useState<ExtractorLinkDto | null>(
    availableLinks[0] || null
  );
  const [resolvedStream, setResolvedStream] = useState<PlayableStreamDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Resolve link through Ktor proxy API /api/resolve
  useEffect(() => {
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
  }, [selectedLink]);

  const handleProgressUpdate = (positionMs: number, durationMs: number) => {
    if (positionMs <= 0 || durationMs <= 0) return;
    // Throttle progress updates to backend
    api.patchHistory({
      provider,
      url: episodeDataUrl,
      name: title,
      episodeData: episodeDataUrl,
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
      onBack={onBack}
      onProgressUpdate={handleProgressUpdate}
    />
  );
};

export default PlayerScreen;
