import React, { useEffect, useState } from 'react';
import { Play, Bookmark, BookmarkCheck, ArrowLeft, Tag } from 'lucide-react';
import type { EpisodeDto, ExtractorLinkDto, LoadResponseDto, SubtitleFileDto } from '../api/types';
import api from '../api/client';

interface DetailsScreenProps {
  provider: string;
  url: string;
  onBack: () => void;
  onStartPlayback: (
    title: string,
    subtitleText: string | undefined,
    initialLinks: ExtractorLinkDto[],
    initialSubtitles: SubtitleFileDto[],
    providerName: string,
    episodeDataUrl: string
  ) => void;
}

export const DetailsScreen: React.FC<DetailsScreenProps> = ({
  provider,
  url,
  onBack,
  onStartPlayback,
}) => {
  const [details, setDetails] = useState<LoadResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [isBookmarked, setIsBookmarked] = useState<boolean>(false);
  const [selectedSeason, setSelectedSeason] = useState<number>(1);
  const [extractingLinks, setExtractingLinks] = useState<boolean>(false);

  // Load details from backend
  useEffect(() => {
    async function fetchDetails() {
      setLoading(true);
      setError(null);
      try {
        const data = await api.load(provider, url);
        setDetails(data);

        // Check bookmark status
        const bRes = await api.getBookmarks();
        const exists = bRes.bookmarks.some((b) => b.provider === provider && b.url === url);
        setIsBookmarked(exists);
      } catch (err: any) {
        setError(err.message || 'Failed to load details');
      } finally {
        setLoading(false);
      }
    }
    fetchDetails();
  }, [provider, url]);

  const toggleBookmark = async () => {
    if (!details) return;
    try {
      if (isBookmarked) {
        await api.deleteBookmark(provider, url);
        setIsBookmarked(false);
      } else {
        await api.putBookmark({
          provider,
          url,
          name: details.name,
          posterUrl: details.posterUrl,
          addedAt: Date.now(),
        });
        setIsBookmarked(true);
      }
    } catch (e) {
      console.error('Failed to toggle bookmark', e);
    }
  };

  const handlePlayEpisode = (ep?: EpisodeDto) => {
    if (!details) return;

    const dataUrl = ep ? ep.data : details.dataUrl || url;
    const epTitle = ep ? `S${ep.season || 1} E${ep.episode || 1}: ${ep.name || 'Episode'}` : details.name;

    setExtractingLinks(true);

    const collectedLinks: ExtractorLinkDto[] = [];
    const collectedSubs: SubtitleFileDto[] = [];

    // Connect to WebSocket link extractor
    const cancelWs = api.streamLinks(
      provider,
      dataUrl,
      (event) => {
        if (event.kind === 'link' && event.link) {
          collectedLinks.push(event.link);
        } else if (event.kind === 'subtitle' && event.subtitle) {
          collectedSubs.push(event.subtitle);
        } else if (event.kind === 'done' || event.kind === 'error') {
          cancelWs();
          setExtractingLinks(false);
          onStartPlayback(
            details.name,
            ep ? epTitle : undefined,
            collectedLinks,
            collectedSubs,
            provider,
            dataUrl
          );
        }
      },
      (err) => {
        console.error('WebSocket extraction error:', err);
        setExtractingLinks(false);
      }
    );
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '120px 0' }}>
        <div className="spinner" />
        <p style={{ marginTop: '16px', color: 'var(--text-muted)' }}>Fetching title details & episodes...</p>
      </div>
    );
  }

  if (error || !details) {
    return (
      <div className="glass-panel" style={{ padding: '36px', textAlign: 'center', margin: '40px 0' }}>
        <p style={{ color: 'var(--accent-pink)', fontWeight: 600, marginBottom: '16px' }}>{error || 'Title not found'}</p>
        <button className="btn btn-secondary" onClick={onBack}>
          <ArrowLeft size={18} /> Go Back
        </button>
      </div>
    );
  }

  const seasonsList = Array.from(
    new Set(details.episodes?.map((e) => e.season || 1) || [1])
  ).sort((a, b) => a - b);

  const filteredEpisodes =
    details.episodes?.filter((e) => (e.season || 1) === selectedSeason) || [];

  return (
    <div style={{ paddingBottom: '60px' }}>
      {/* Back Button */}
      <button className="btn btn-secondary" onClick={onBack} style={{ marginBottom: '24px' }}>
        <ArrowLeft size={18} /> Back
      </button>

      {/* Backdrop & Header Header */}
      <div
        className="glass-panel animate-fade-in"
        style={{
          position: 'relative',
          padding: '36px',
          borderRadius: 'var(--radius-lg)',
          overflow: 'hidden',
          display: 'flex',
          gap: '32px',
          flexWrap: 'wrap',
          marginBottom: '40px',
        }}
      >
        {details.backgroundPosterUrl && (
          <img
            src={details.backgroundPosterUrl}
            alt="Backdrop"
            style={{
              position: 'absolute',
              inset: 0,
              width: '100%',
              height: '100%',
              objectFit: 'cover',
              filter: 'blur(16px) brightness(0.25)',
              zIndex: 0,
            }}
          />
        )}

        {/* Poster Image */}
        <div style={{ position: 'relative', zIndex: 1, width: '220px', flexShrink: 0 }}>
          <img
            src={details.posterUrl || 'https://via.placeholder.com/300x450'}
            alt={details.name}
            style={{
              width: '100%',
              borderRadius: 'var(--radius-md)',
              boxShadow: 'var(--shadow-card)',
              border: '1px solid var(--border-glass)',
            }}
          />
        </div>

        {/* Title Details */}
        <div style={{ position: 'relative', zIndex: 1, flex: 1, minWidth: '280px' }}>
          <div style={{ display: 'flex', gap: '10px', alignItems: 'center', marginBottom: '12px' }}>
            <span className="glass-pill" style={{ color: 'var(--accent-cyan)' }}>
              {details.type.toUpperCase()}
            </span>
            {details.year && <span className="glass-pill">{details.year}</span>}
            {details.showStatus && <span className="glass-pill">{details.showStatus}</span>}
          </div>

          <h1 style={{ fontSize: '2.5rem', fontWeight: 800, marginBottom: '16px', lineHeight: 1.15 }}>
            {details.name}
          </h1>

          <p style={{ color: 'var(--text-muted)', fontSize: '1rem', lineHeight: 1.6, marginBottom: '24px', maxWidth: '750px' }}>
            {details.plot || 'No overview available for this title.'}
          </p>

          {/* Tags / Genres */}
          {details.tags && details.tags.length > 0 && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginBottom: '24px' }}>
              {details.tags.map((tag, i) => (
                <span key={i} className="glass-pill" style={{ fontSize: '0.8rem' }}>
                  <Tag size={12} /> {tag}
                </span>
              ))}
            </div>
          )}

          {/* Action Buttons */}
          <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', alignItems: 'center' }}>
            {details.kind !== 'series' && (
              <button
                className="btn btn-primary"
                onClick={() => handlePlayEpisode()}
                disabled={extractingLinks}
                style={{ padding: '12px 28px' }}
              >
                {extractingLinks ? <div className="spinner" style={{ width: 20, height: 20 }} /> : <Play size={20} fill="#fff" />}
                <span>{extractingLinks ? 'Extracting Streams...' : 'Play Movie'}</span>
              </button>
            )}

            <button
              className={`btn ${isBookmarked ? 'btn-primary' : 'btn-secondary'}`}
              onClick={toggleBookmark}
              style={{ padding: '12px 22px' }}
            >
              {isBookmarked ? <BookmarkCheck size={20} /> : <Bookmark size={20} />}
              <span>{isBookmarked ? 'In Library' : 'Bookmark'}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Episodes Section (If Series or Anime) */}
      {(details.kind === 'series' || (details.episodes && details.episodes.length > 0)) && (
        <div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '20px' }}>
            <h2 style={{ fontSize: '1.5rem', fontWeight: 700 }}>Episodes</h2>

            {/* Season Selector */}
            {seasonsList.length > 1 && (
              <div style={{ display: 'flex', gap: '8px' }}>
                {seasonsList.map((s) => (
                  <button
                    key={s}
                    className={`btn ${selectedSeason === s ? 'btn-primary' : 'btn-secondary'}`}
                    onClick={() => setSelectedSeason(s)}
                    style={{ padding: '6px 16px', fontSize: '0.88rem' }}
                  >
                    Season {s}
                  </button>
                ))}
              </div>
            )}
          </div>

          {/* Episodes List Grid */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {filteredEpisodes.map((ep, i) => (
              <div
                key={i}
                className="glass-panel animate-fade-in"
                onClick={() => handlePlayEpisode(ep)}
                style={{
                  padding: '16px 20px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  cursor: 'pointer',
                  transition: 'var(--transition-fast)',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                  <div
                    style={{
                      width: '40px',
                      height: '40px',
                      borderRadius: 'var(--radius-full)',
                      background: 'rgba(255,255,255,0.08)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      fontWeight: 700,
                      color: 'var(--accent-cyan)',
                    }}
                  >
                    {ep.episode || i + 1}
                  </div>
                  <div>
                    <div style={{ fontWeight: 700, fontSize: '1.05rem', marginBottom: '2px' }}>
                      {ep.name || `Episode ${ep.episode || i + 1}`}
                    </div>
                    {ep.description && (
                      <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', maxLines: 1 }}>
                        {ep.description}
                      </p>
                    )}
                  </div>
                </div>

                <button className="btn btn-primary btn-icon" style={{ flexShrink: 0 }}>
                  <Play size={18} fill="#fff" />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default DetailsScreen;
