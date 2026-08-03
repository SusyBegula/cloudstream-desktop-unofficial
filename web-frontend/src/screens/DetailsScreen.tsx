import React, { useEffect, useRef, useState } from 'react';
import { Play, Plus, Check, ArrowLeft, ChevronDown } from 'lucide-react';
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

interface EpisodeRowProps {
  ep: EpisodeDto;
  index: number;
  onPlay: (ep: EpisodeDto) => void;
}

const EpisodeRow: React.FC<EpisodeRowProps> = ({ ep, index, onPlay }) => {
  const [visible, setVisible] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: '800px 0px' }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  if (!visible) {
    return <div ref={ref} style={{ height: '80px', marginBottom: '12px' }} />;
  }

  return (
    <div
      ref={ref}
      className="animate-fade-in"
      onClick={() => onPlay(ep)}
      style={{
        padding: '20px 24px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        cursor: 'pointer',
        transition: 'var(--transition-fast)',
        marginBottom: '12px',
        backgroundColor: '#181818',
        borderRadius: 'var(--radius-sm)',
        border: '1px solid rgba(255,255,255,0.05)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
        <div
          style={{
            fontSize: '1.4rem',
            fontWeight: 800,
            color: 'var(--text-muted)',
            width: '32px',
            textAlign: 'center',
          }}
        >
          {ep.episode || index + 1}
        </div>
        <div>
          <div style={{ fontWeight: 700, fontSize: '1.1rem', marginBottom: '4px', color: '#fff' }}>
            {ep.name || `Episode ${ep.episode || index + 1}`}
          </div>
          {ep.description && (
            <p style={{ fontSize: '0.88rem', color: 'var(--text-muted)', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', maxWidth: '720px' }}>
              {ep.description}
            </p>
          )}
        </div>
      </div>

      <button className="btn btn-netflix-white btn-icon" style={{ width: 44, height: 44, flexShrink: 0 }}>
        <Play size={20} fill="#000" style={{ marginLeft: 2 }} />
      </button>
    </div>
  );
};

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

  useEffect(() => {
    async function fetchDetails() {
      setLoading(true);
      setError(null);
      try {
        const [data, bRes] = await Promise.all([api.load(provider, url), api.getBookmarks()]);
        setDetails(data);
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
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '160px 0' }}>
        <div className="spinner" />
        <p style={{ marginTop: '18px', color: 'var(--text-muted)', fontWeight: 500 }}>Fetching title & episodes...</p>
      </div>
    );
  }

  if (error || !details) {
    return (
      <div style={{ padding: '140px 4% 40px 4%', textAlign: 'center', maxWidth: '600px', margin: '0 auto' }}>
        <p style={{ color: 'var(--netflix-red)', fontWeight: 700, marginBottom: '20px', fontSize: '1.2rem' }}>{error || 'Title not found'}</p>
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
    <div style={{ paddingBottom: '80px', paddingTop: '68px' }}>
      {/* Netflix Hero Cover Banner */}
      <div
        className="animate-fade-in"
        style={{
          position: 'relative',
          padding: '60px 4% 40px 4%',
          minHeight: '440px',
          display: 'flex',
          gap: '40px',
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
              filter: 'brightness(0.35)',
              zIndex: 0,
            }}
          />
        )}

        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, #141414 0%, transparent 80%)', zIndex: 1 }} />
        <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to right, #141414 0%, transparent 60%)', zIndex: 1 }} />

        {/* Back Button */}
        <button
          className="btn btn-netflix-dark"
          onClick={onBack}
          style={{ position: 'absolute', top: '24px', left: '4%', zIndex: 10, padding: '8px 16px', fontSize: '0.88rem' }}
        >
          <ArrowLeft size={18} /> Back
        </button>

        {/* Poster Card Showcase */}
        <div style={{ position: 'relative', zIndex: 5, width: '220px', flexShrink: 0, marginTop: '20px' }}>
          <img
            src={details.posterUrl || 'https://via.placeholder.com/300x450'}
            alt={details.name}
            style={{
              width: '100%',
              borderRadius: 'var(--radius-sm)',
              boxShadow: 'var(--shadow-card)',
            }}
          />
        </div>

        {/* Title Details Information */}
        <div style={{ position: 'relative', zIndex: 5, flex: 1, minWidth: '320px', marginTop: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
            <div style={{ width: 16, height: 16, borderRadius: 2, background: 'var(--netflix-red)', color: '#fff', fontSize: '0.65rem', fontWeight: 900, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              N
            </div>
            <span style={{ fontSize: '0.82rem', fontWeight: 800, letterSpacing: '2px', color: '#E5E5E5' }}>
              {details.type.toUpperCase()}
            </span>
          </div>

          <h1 style={{ fontSize: '3rem', fontWeight: 900, marginBottom: '16px', lineHeight: 1.1, letterSpacing: '-0.5px' }}>
            {details.name}
          </h1>

          <div style={{ display: 'flex', gap: '14px', alignItems: 'center', marginBottom: '20px', fontSize: '0.95rem', fontWeight: 600, flexWrap: 'wrap' }}>
            <span style={{ color: 'var(--netflix-green)', fontWeight: 800 }}>98% Match</span>
            {details.year && <span>{details.year}</span>}
            {details.showStatus && (
              <span style={{ border: '1px solid rgba(255,255,255,0.4)', padding: '1px 6px', borderRadius: 2, fontSize: '0.78rem' }}>
                {details.showStatus}
              </span>
            )}
          </div>

          <p style={{ color: '#D2D2D2', fontSize: '1.05rem', lineHeight: 1.6, marginBottom: '32px', maxWidth: '780px' }}>
            {details.plot || 'No overview available for this title.'}
          </p>

          {/* Primary Action Controls */}
          <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', alignItems: 'center' }}>
            {details.kind !== 'series' && (
              <button
                className="btn btn-netflix-white"
                onClick={() => handlePlayEpisode()}
                disabled={extractingLinks}
                style={{ padding: '14px 36px', fontSize: '1.05rem' }}
              >
                {extractingLinks ? <div className="spinner" style={{ width: 20, height: 20 }} /> : <Play size={22} fill="#000" style={{ marginLeft: 2 }} />}
                <span>{extractingLinks ? 'Extracting Streams...' : 'Play Movie'}</span>
              </button>
            )}

            <button
              className="btn btn-netflix-dark"
              onClick={toggleBookmark}
              style={{ padding: '14px 28px', fontSize: '1.05rem' }}
            >
              {isBookmarked ? <Check size={22} style={{ color: 'var(--netflix-green)' }} /> : <Plus size={22} />}
              <span>{isBookmarked ? 'In My List' : 'Add to My List'}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Episode Browser Section */}
      {(details.kind === 'series' || (details.episodes && details.episodes.length > 0)) && (
        <div style={{ padding: '0 4%' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px', flexWrap: 'wrap', gap: '16px' }}>
            <h2 style={{ fontSize: '1.8rem', fontWeight: 800 }}>Episodes</h2>

            {/* Netflix Season Selector Dropdown */}
            {seasonsList.length > 1 && (
              <div style={{ position: 'relative' }}>
                <select
                  value={selectedSeason}
                  onChange={(e) => setSelectedSeason(Number(e.target.value))}
                  style={{
                    backgroundColor: '#242424',
                    color: '#fff',
                    border: '1px solid rgba(255,255,255,0.2)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '10px 40px 10px 16px',
                    fontSize: '1rem',
                    fontWeight: 700,
                    appearance: 'none',
                    cursor: 'pointer',
                    outline: 'none',
                  }}
                >
                  {seasonsList.map((s) => (
                    <option key={s} value={s}>
                      Season {s}
                    </option>
                  ))}
                </select>
                <ChevronDown
                  size={18}
                  style={{
                    position: 'absolute',
                    right: 12,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    pointerEvents: 'none',
                    color: '#fff',
                  }}
                />
              </div>
            )}
          </div>

          {/* Episode Cards */}
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {filteredEpisodes.map((ep, i) => (
              <EpisodeRow key={i} ep={ep} index={i} onPlay={handlePlayEpisode} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default DetailsScreen;

