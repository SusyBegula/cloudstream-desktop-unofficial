import React, { useEffect, useState } from 'react';
import { Bookmark, History, Trash2, Play } from 'lucide-react';
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
        const bRes = await api.getBookmarks();
        const hRes = await api.getHistory();
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
    <div style={{ paddingBottom: '60px' }}>
      {/* Page Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '28px' }}>
        <div>
          <h1 style={{ fontSize: '2rem', fontWeight: 800 }} className="text-gradient">
            My Library
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
            Manage your bookmarked titles and watch history
          </p>
        </div>

        {/* Tab Switcher */}
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            className={`btn ${activeTab === 'bookmarks' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setActiveTab('bookmarks')}
          >
            <Bookmark size={18} /> Bookmarks ({bookmarks.length})
          </button>
          <button
            className={`btn ${activeTab === 'history' ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => setActiveTab('history')}
          >
            <History size={18} /> History ({history.length})
          </button>
        </div>
      </div>

      {loading ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '100px 0' }}>
          <div className="spinner" />
          <p style={{ marginTop: '16px', color: 'var(--text-muted)' }}>Loading library...</p>
        </div>
      ) : activeTab === 'bookmarks' ? (
        bookmarks.length === 0 ? (
          <div className="glass-panel" style={{ padding: '48px', textAlign: 'center', margin: '40px 0' }}>
            <Bookmark size={40} style={{ color: 'var(--text-subtle)', marginBottom: '16px' }} />
            <h3 style={{ fontSize: '1.2rem', marginBottom: '8px' }}>No Bookmarks Yet</h3>
            <p style={{ color: 'var(--text-muted)' }}>Save movies and TV series to quickly find them here.</p>
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
                <button
                  className="btn btn-secondary btn-icon"
                  style={{
                    position: 'absolute',
                    top: '10px',
                    right: '10px',
                    width: '32px',
                    height: '32px',
                    background: 'rgba(0,0,0,0.7)',
                    borderColor: 'rgba(255,255,255,0.2)',
                  }}
                  onClick={(e) => handleDeleteBookmark(e, b)}
                  title="Remove Bookmark"
                >
                  <Trash2 size={14} style={{ color: 'var(--accent-pink)' }} />
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
        <div className="glass-panel" style={{ padding: '48px', textAlign: 'center', margin: '40px 0' }}>
          <History size={40} style={{ color: 'var(--text-subtle)', marginBottom: '16px' }} />
          <h3 style={{ fontSize: '1.2rem', marginBottom: '8px' }}>No Watch History</h3>
          <p style={{ color: 'var(--text-muted)' }}>Titles you play will automatically appear here with your watch progress.</p>
        </div>
      ) : (
        <div>
          <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: '16px' }}>
            <button className="btn btn-secondary" onClick={handleClearHistory} style={{ fontSize: '0.85rem' }}>
              <Trash2 size={16} /> Clear All History
            </button>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {history.map((h, idx) => {
              const progressPct =
                h.durationMs > 0 ? Math.min(100, Math.round((h.positionMs / h.durationMs) * 100)) : 0;
              return (
                <div
                  key={idx}
                  className="glass-panel animate-fade-in"
                  onClick={() => onSelectMedia(h.provider, h.url)}
                  style={{
                    padding: '16px 20px',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    cursor: 'pointer',
                    gap: '16px',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px', flex: 1 }}>
                    <img
                      src={h.posterUrl || 'https://via.placeholder.com/100x150'}
                      alt={h.name}
                      style={{ width: '48px', height: '64px', borderRadius: 'var(--radius-sm)', objectFit: 'cover' }}
                    />
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 700, fontSize: '1.05rem', marginBottom: '4px' }}>{h.name}</div>
                      <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)', marginBottom: '8px' }}>
                        {h.provider} {h.season && `• S${h.season} E${h.episode}`}
                      </div>

                      {/* Watch Progress Bar */}
                      <div style={{ width: '100%', maxWidth: '300px', height: '4px', background: 'rgba(255,255,255,0.1)', borderRadius: '2px', overflow: 'hidden' }}>
                        <div
                          style={{
                            width: `${progressPct}%`,
                            height: '100%',
                            background: 'linear-gradient(90deg, var(--accent-primary), var(--accent-cyan))',
                          }}
                        />
                      </div>
                    </div>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                    <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--accent-cyan)' }}>
                      {progressPct}%
                    </span>
                    <button className="btn btn-primary btn-icon" title="Resume Playback">
                      <Play size={16} fill="#fff" />
                    </button>
                    <button
                      className="btn btn-secondary btn-icon"
                      onClick={(e) => handleDeleteHistoryItem(e, h)}
                      title="Remove Item"
                    >
                      <Trash2 size={16} style={{ color: 'var(--accent-pink)' }} />
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
