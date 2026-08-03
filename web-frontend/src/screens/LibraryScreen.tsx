import React, { useEffect, useState } from 'react';
import { Trash2, Play, Bookmark, Clock } from 'lucide-react';
import type { BookmarkDto, WatchHistoryEntryDto } from '../api/types';
import api from '../api/client';

interface LibraryScreenProps {
  onSelectMedia: (provider: string, url: string) => void;
}

export const LibraryScreen: React.FC<LibraryScreenProps> = ({ onSelectMedia }) => {
  const [activeTab, setActiveTab] = useState<'bookmarks' | 'history'>('bookmarks');
  const [bookmarks, setBookmarks] = useState<BookmarkDto[]>([]);
  const [history, setHistory] = useState<WatchHistoryEntryDto[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    async function loadData() {
      setLoading(true);
      try {
        const [bRes, hRes] = await Promise.all([api.getBookmarks(), api.getHistory()]);
        setBookmarks(bRes.bookmarks);
        setHistory(hRes.entries);
      } catch (err) {
        console.error('Failed to load library data:', err);
      } finally {
        setLoading(false);
      }
    }
    loadData();
  }, []);

  const handleDeleteBookmark = async (e: React.MouseEvent, b: BookmarkDto) => {
    e.stopPropagation();
    try {
      await api.deleteBookmark(b.provider, b.url);
      setBookmarks(bookmarks.filter((item) => !(item.provider === b.provider && item.url === b.url)));
    } catch (err) {
      console.error('Failed to delete bookmark:', err);
    }
  };

  const handleDeleteHistoryItem = async (e: React.MouseEvent, h: WatchHistoryEntryDto) => {
    e.stopPropagation();
    try {
      await api.deleteHistory(h);
      setHistory(history.filter((item) => !(item.provider === h.provider && item.url === h.url)));
    } catch (err) {
      console.error('Failed to delete history item:', err);
    }
  };

  const handleClearHistory = async () => {
    try {
      await api.clearHistory();
      setHistory([]);
    } catch (err) {
      console.error('Failed to clear history:', err);
    }
  };

  return (
    <div style={{ padding: '90px 4% 80px 4%' }}>
      {/* Page Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '36px', flexWrap: 'wrap', gap: '16px' }}>
        <div>
          <h1 style={{ fontSize: '2.5rem', fontWeight: 900, letterSpacing: '-0.5px' }}>
            My List
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginTop: '4px' }}>
            Manage saved movies, anime, and watch history
          </p>
        </div>

        {/* Tab Switcher Pills */}
        <div style={{ display: 'flex', gap: '12px' }}>
          <button
            className={`btn ${activeTab === 'bookmarks' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setActiveTab('bookmarks')}
            style={{ padding: '8px 22px' }}
          >
            <Bookmark size={18} /> My List ({bookmarks.length})
          </button>
          <button
            className={`btn ${activeTab === 'history' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setActiveTab('history')}
            style={{ padding: '8px 22px' }}
          >
            <Clock size={18} /> Continue Watching ({history.length})
          </button>
        </div>
      </div>

      {loading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '20px' }}>
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="poster-card skeleton" />
          ))}
        </div>
      ) : activeTab === 'bookmarks' ? (
        bookmarks.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '100px 0', color: 'var(--text-muted)' }}>
            <Bookmark size={56} style={{ color: 'var(--text-subtle)', marginBottom: '16px' }} />
            <h3 style={{ fontSize: '1.4rem', fontWeight: 800, color: '#fff', marginBottom: '8px' }}>Your list is empty</h3>
            <p style={{ fontSize: '0.95rem' }}>Save titles you want to watch later.</p>
          </div>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))', gap: '20px' }}>
            {bookmarks.map((b, idx) => (
              <div
                key={idx}
                className="poster-card animate-fade-in"
                onClick={() => onSelectMedia(b.provider, b.url)}
              >
                <img src={b.posterUrl || 'https://via.placeholder.com/300x450'} alt={b.name} />
                <div className="poster-play-btn">
                  <Play size={20} fill="#000" style={{ marginLeft: 2 }} />
                </div>
                <button
                  className="btn btn-secondary btn-icon"
                  style={{
                    position: 'absolute',
                    top: '8px',
                    right: '8px',
                    width: '32px',
                    height: '32px',
                    background: 'rgba(0,0,0,0.85)',
                    border: '1px solid rgba(255,255,255,0.2)',
                    zIndex: 10,
                  }}
                  onClick={(e) => handleDeleteBookmark(e, b)}
                  title="Remove from My List"
                >
                  <Trash2 size={14} style={{ color: 'var(--netflix-red)' }} />
                </button>
                <div className="poster-overlay">
                  <div className="poster-title">{b.name}</div>
                  <div className="poster-meta">{b.provider}</div>
                </div>
              </div>
            ))}
          </div>
        )
      ) : history.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '100px 0', color: 'var(--text-muted)' }}>
          <Clock size={56} style={{ color: 'var(--text-subtle)', marginBottom: '16px' }} />
          <h3 style={{ fontSize: '1.4rem', fontWeight: 800, color: '#fff', marginBottom: '8px' }}>No watch history</h3>
          <p style={{ fontSize: '0.95rem' }}>Titles you watch will appear here.</p>
        </div>
      ) : (
        <div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '20px' }}>
            <button className="btn btn-secondary" onClick={handleClearHistory} style={{ fontSize: '0.88rem' }}>
              <Trash2 size={16} style={{ color: 'var(--netflix-red)' }} /> Clear All History
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
            {history.map((h, idx) => {
              const progressPct =
                h.durationMs > 0 ? Math.min(100, Math.round((h.positionMs / h.durationMs) * 100)) : 0;
              return (
                <div
                  key={idx}
                  className="animate-fade-in"
                  onClick={() => onSelectMedia(h.provider, h.url)}
                  style={{
                    padding: '16px 24px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    cursor: 'pointer',
                    gap: '20px',
                    backgroundColor: '#181818',
                    borderRadius: 'var(--radius-sm)',
                    border: '1px solid rgba(255,255,255,0.05)',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '20px', flex: 1 }}>
                    <img
                      src={h.posterUrl || 'https://via.placeholder.com/100x150'}
                      alt={h.name}
                      style={{ width: '56px', height: '76px', borderRadius: 'var(--radius-sm)', objectFit: 'cover' }}
                    />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 800, fontSize: '1.15rem', marginBottom: '4px', color: '#fff' }}>{h.name}</div>
                      <div style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '12px' }}>
                        {h.provider} {h.season && `• Season ${h.season} Episode ${h.episode}`}
                      </div>

                      {/* Netflix Watch Progress Bar */}
                      <div style={{ width: '100%', maxWidth: '360px', height: '4px', background: '#333333', borderRadius: '2px', overflow: 'hidden' }}>
                        <div
                          style={{
                            width: `${progressPct}%`,
                            height: '100%',
                            background: 'var(--netflix-red)',
                          }}
                        />
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <span style={{ fontSize: '0.88rem', fontWeight: 800, color: 'var(--netflix-green)' }}>
                      {progressPct}%
                    </span>
                    <button className="btn btn-netflix-white btn-icon" style={{ width: 42, height: 42 }} title="Resume Playback">
                      <Play size={18} fill="#000" style={{ marginLeft: 2 }} />
                    </button>
                    <button
                      className="btn btn-secondary btn-icon"
                      style={{ width: 42, height: 42 }}
                      onClick={(e) => handleDeleteHistoryItem(e, h)}
                      title="Remove"
                    >
                      <Trash2 size={16} style={{ color: 'var(--netflix-red)' }} />
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

export default LibraryScreen;


