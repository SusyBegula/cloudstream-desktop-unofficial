import React, { useEffect, useRef, useState } from 'react';
import { Play, Plus, Check, ArrowLeft, ChevronDown, Search } from 'lucide-react';
import type { EpisodeDto, ExtractorLinkDto, LoadResponseDto, SubtitleFileDto, WatchHistoryEntryDto } from '../api/types';
import api from '../api/client';

export interface StartPlaybackParams {
  title: string;
  subtitleText?: string;
  availableLinks: ExtractorLinkDto[];
  subtitles: SubtitleFileDto[];
  provider: string;
  episodeDataUrl: string;
  /** The show/movie's own page URL — distinct from episodeDataUrl — so watch history can be
   * matched back to "has this title been seen before" regardless of which episode played. */
  seriesUrl: string;
  posterUrl?: string;
  season?: number;
  episode?: number;
  startPositionMs?: number;
}

interface DetailsScreenProps {
  provider: string;
  url: string;
  onBack: () => void;
  onStartPlayback: (params: StartPlaybackParams) => void;
}

// A show/movie counts as "completed" past this percent — Continue Watching then advances to the
// next episode (if any) instead of resuming inside the one already finished.
const COMPLETED_PERCENT = 95;

/** The most recently watched episode/position for this title, or null if never watched. */
function getContinueTarget(
  history: WatchHistoryEntryDto[],
  provider: string,
  seriesUrl: string,
  episodes: EpisodeDto[] | null | undefined
): { episode: EpisodeDto | null; resumeMs: number } | null {
  const matches = history.filter((h) => h.provider === provider && h.url === seriesUrl);
  if (matches.length === 0) return null;

  const latest = matches.reduce((a, b) => (a.updatedAt > b.updatedAt ? a : b));
  const percent = latest.durationMs > 0 ? (latest.positionMs / latest.durationMs) * 100 : 0;
  const completed = percent >= COMPLETED_PERCENT;

  if (!episodes || episodes.length === 0) {
    // Movie: nothing to advance to — a finished movie just plays fresh from the start again.
    return completed ? null : { episode: null, resumeMs: latest.positionMs };
  }

  const matchedIndex = episodes.findIndex(
    (e) => (e.season ?? 1) === (latest.season ?? 1) && e.episode === latest.episode
  );
  if (matchedIndex === -1) return null;

  if (completed) {
    const next = episodes[matchedIndex + 1];
    return next ? { episode: next, resumeMs: 0 } : null;
  }
  return { episode: episodes[matchedIndex], resumeMs: latest.positionMs };
}

/** Watch percent (0-100) for one specific episode, or 0 if never watched. */
function getEpisodeProgress(
  history: WatchHistoryEntryDto[],
  provider: string,
  seriesUrl: string,
  ep: EpisodeDto
): number {
  const entry = history.find(
    (h) =>
      h.provider === provider &&
      h.url === seriesUrl &&
      (h.season ?? 1) === (ep.season ?? 1) &&
      h.episode === ep.episode
  );
  if (!entry || entry.durationMs <= 0) return 0;
  return Math.min(100, Math.round((entry.positionMs / entry.durationMs) * 100));
}

interface EpisodeRowProps {
  ep: EpisodeDto;
  index: number;
  onPlay: (ep: EpisodeDto) => void;
  progressPercent: number;
}

const EpisodeRow: React.FC<EpisodeRowProps> = ({ ep, index, onPlay, progressPercent }) => {
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
      <div style={{ display: 'flex', alignItems: 'center', gap: '20px', flex: 1, minWidth: 0 }}>
        <div
          style={{
            fontSize: '1.4rem',
            fontWeight: 800,
            color: 'var(--text-muted)',
            width: '32px',
            textAlign: 'center',
            flexShrink: 0,
          }}
        >
          {ep.episode || index + 1}
        </div>

        {/* Thumbnail with watch-progress bar, Netflix-style */}
        <div
          style={{
            position: 'relative',
            width: '160px',
            height: '90px',
            flexShrink: 0,
            borderRadius: 'var(--radius-sm)',
            overflow: 'hidden',
            backgroundColor: '#000',
          }}
        >
          <img
            src={ep.posterUrl || 'https://via.placeholder.com/320x180'}
            alt={ep.name || `Episode ${ep.episode || index + 1}`}
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
          {progressPercent > 0 && (
            <div
              style={{
                position: 'absolute',
                bottom: 0,
                left: 0,
                right: 0,
                height: '4px',
                background: 'rgba(255,255,255,0.3)',
              }}
            >
              <div style={{ width: `${progressPercent}%`, height: '100%', background: 'var(--netflix-red)' }} />
            </div>
          )}
        </div>

        <div style={{ minWidth: 0 }}>
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
  const [history, setHistory] = useState<WatchHistoryEntryDto[]>([]);
  const [episodeSearchQuery, setEpisodeSearchQuery] = useState<string>('');

  useEffect(() => {
    async function fetchDetails() {
      setLoading(true);
      setError(null);
      try {
        const [data, bRes, hRes] = await Promise.all([api.load(provider, url), api.getBookmarks(), api.getHistory()]);
        setDetails(data);
        const exists = bRes.bookmarks.some((b) => b.provider === provider && b.url === url);
        setIsBookmarked(exists);
        setHistory(hRes.entries);
      } catch (err: any) {
        setError(err.message || 'Failed to load details');
      } finally {
        setLoading(false);
      }
    }
    fetchDetails();
  }, [provider, url]);

  // Auto-set selectedSeason to match available seasons
  useEffect(() => {
    if (details?.episodes && details.episodes.length > 0) {
      const seasons = Array.from(
        new Set(details.episodes.map((e) => (e.season !== undefined && e.season !== null ? e.season : 1)))
      ).sort((a, b) => a - b);
      if (seasons.length > 0 && !seasons.includes(selectedSeason)) {
        setSelectedSeason(seasons[0]);
      }
    }
  }, [details]);

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

  const handlePlayEpisode = (ep?: EpisodeDto, resumeMs: number = 0) => {
    if (!details) return;

    const targetEp = ep || (details.episodes && details.episodes.length > 0 ? details.episodes[0] : undefined);
    const dataUrl = targetEp ? targetEp.data : details.dataUrl || url;
    const epTitle = targetEp ? `S${targetEp.season || 1} E${targetEp.episode || 1}: ${targetEp.name || 'Episode'}` : details.name;

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
          onStartPlayback({
            title: details.name,
            subtitleText: targetEp ? epTitle : undefined,
            availableLinks: collectedLinks,
            subtitles: collectedSubs,
            provider,
            episodeDataUrl: dataUrl,
            seriesUrl: url,
            posterUrl: details.posterUrl || undefined,
            season: targetEp?.season ?? undefined,
            episode: targetEp?.episode ?? undefined,
            startPositionMs: resumeMs,
          });
        }
      },
      (err) => {
        console.error('WebSocket extraction error:', err);
        setExtractingLinks(false);
      }
    );
  };

  const continueTarget = details ? getContinueTarget(history, provider, url, details.episodes) : null;

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
    new Set(details.episodes?.map((e) => (e.season !== undefined && e.season !== null ? e.season : 1)) || [1])
  ).sort((a, b) => a - b);

  const filteredEpisodes = details.episodes
    ? seasonsList.length <= 1
      ? details.episodes
      : details.episodes.filter((e) => {
          const epSeason = e.season !== undefined && e.season !== null ? e.season : 1;
          return epSeason === selectedSeason;
        })
    : [];

  const searchedEpisodes = episodeSearchQuery.trim()
    ? filteredEpisodes.filter((e) => {
        const q = episodeSearchQuery.trim().toLowerCase();
        return (
          (e.name || '').toLowerCase().includes(q) ||
          (e.description || '').toLowerCase().includes(q) ||
          String(e.episode ?? '').includes(q)
        );
      })
    : filteredEpisodes;

  const firstEp = details.episodes && details.episodes.length > 0 ? details.episodes[0] : null;

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
            <button
              className="btn btn-netflix-white"
              onClick={() => handlePlayEpisode(continueTarget?.episode || firstEp || undefined, continueTarget?.resumeMs || 0)}
              disabled={extractingLinks}
              style={{ padding: '14px 36px', fontSize: '1.05rem' }}
            >
              {extractingLinks ? <div className="spinner" style={{ width: 20, height: 20 }} /> : <Play size={22} fill="#000" style={{ marginLeft: 2 }} />}
              <span>
                {extractingLinks
                  ? 'Extracting Streams...'
                  : continueTarget
                  ? continueTarget.episode
                    ? `Continue S${continueTarget.episode.season || 1} E${continueTarget.episode.episode || 1}: ${continueTarget.episode.name || 'Episode'}`
                    : 'Continue Watching'
                  : firstEp
                  ? `Play ${firstEp.name || `Episode ${firstEp.episode || 1}`}`
                  : 'Play Stream'}
              </span>
            </button>

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
      <div style={{ padding: '0 4%' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px', flexWrap: 'wrap', gap: '16px' }}>
          <h2 style={{ fontSize: '1.8rem', fontWeight: 800 }}>Episodes</h2>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
            {/* Episode Search — only useful once there's more than one episode to search through */}
            {(details.episodes?.length || 0) > 1 && (
              <div style={{ position: 'relative' }}>
                <Search
                  size={16}
                  style={{ position: 'absolute', left: 12, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }}
                />
                <input
                  type="text"
                  value={episodeSearchQuery}
                  onChange={(e) => setEpisodeSearchQuery(e.target.value)}
                  placeholder="Search episodes..."
                  style={{
                    backgroundColor: '#242424',
                    color: '#fff',
                    border: '1px solid rgba(255,255,255,0.2)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '10px 14px 10px 36px',
                    fontSize: '0.95rem',
                    width: '220px',
                    outline: 'none',
                  }}
                />
              </div>
            )}

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
        </div>

        {/* Episode Cards List */}
        {searchedEpisodes.length > 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {searchedEpisodes.map((ep, i) => (
              <EpisodeRow
                key={i}
                ep={ep}
                index={i}
                onPlay={handlePlayEpisode}
                progressPercent={getEpisodeProgress(history, provider, url, ep)}
              />
            ))}
          </div>
        ) : filteredEpisodes.length > 0 ? (
          <div style={{ padding: '36px', backgroundColor: '#181818', borderRadius: 'var(--radius-sm)', textAlign: 'center', border: '1px solid rgba(255,255,255,0.05)' }}>
            <p style={{ color: 'var(--text-muted)', fontSize: '1rem' }}>
              No episodes match "{episodeSearchQuery}".
            </p>
          </div>
        ) : (
          <div style={{ padding: '36px', backgroundColor: '#181818', borderRadius: 'var(--radius-sm)', textAlign: 'center', border: '1px solid rgba(255,255,255,0.05)' }}>
            <p style={{ color: 'var(--text-muted)', fontSize: '1rem', marginBottom: '16px' }}>
              No individual episode entries were listed by this provider.
            </p>
            <button className="btn btn-netflix-white" onClick={() => handlePlayEpisode()}>
              <Play size={18} fill="#000" /> Play Main Stream Link
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default DetailsScreen;


