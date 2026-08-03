import React, { useState } from 'react';
import { Home, Bookmark, Package, Search, X, Play } from 'lucide-react';
import type { ExtractorLinkDto, SearchResultDto, SubtitleFileDto } from './api/types';
import api from './api/client';
import HomeScreen from './screens/HomeScreen';
import DetailsScreen from './screens/DetailsScreen';
import PlayerScreen from './screens/PlayerScreen';
import LibraryScreen from './screens/LibraryScreen';
import ExtensionsScreen from './screens/ExtensionsScreen';

type Screen =
  | { type: 'home' }
  | { type: 'details'; provider: string; url: string }
  | {
      type: 'player';
      title: string;
      subtitleText?: string;
      availableLinks: ExtractorLinkDto[];
      subtitles: SubtitleFileDto[];
      provider: string;
      episodeDataUrl: string;
    }
  | { type: 'library' }
  | { type: 'extensions' };

export const App: React.FC = () => {
  const [currentScreen, setCurrentScreen] = useState<Screen>({ type: 'home' });

  // Global Search Modal state
  const [showSearchModal, setShowSearchModal] = useState<boolean>(false);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [searchResults, setSearchResults] = useState<SearchResultDto[]>([]);
  const [searchLoading, setSearchLoading] = useState<boolean>(false);

  const handleSearchSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchQuery.trim()) return;
    setSearchLoading(true);
    try {
      const res = await api.search(searchQuery, undefined, true);
      const combined = res.perProvider.flatMap((p) => p.results);
      setSearchResults(combined);
    } catch (err) {
      console.error('Failed to search:', err);
    } finally {
      setSearchLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', minHeight: '100vh', backgroundColor: 'var(--bg-dark)' }}>
      {/* Sidebar Navigation */}
      {currentScreen.type !== 'player' && (
        <aside
          className="glass-panel"
          style={{
            width: '240px',
            borderRadius: 0,
            borderTop: 'none',
            borderBottom: 'none',
            borderLeft: 'none',
            padding: '28px 16px',
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'space-between',
            position: 'fixed',
            top: 0,
            bottom: 0,
            left: 0,
            zIndex: 50,
          }}
        >
          <div>
            {/* Logo */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                padding: '0 12px 32px 12px',
                cursor: 'pointer',
              }}
              onClick={() => setCurrentScreen({ type: 'home' })}
            >
              <div
                style={{
                  width: '36px',
                  height: '36px',
                  borderRadius: 'var(--radius-md)',
                  background: 'linear-gradient(135deg, var(--accent-primary), var(--accent-secondary))',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: 'var(--shadow-glow)',
                }}
              >
                <Play size={20} fill="#fff" />
              </div>
              <div>
                <div style={{ fontWeight: 800, fontSize: '1.15rem', letterSpacing: '-0.5px' }}>
                  CloudStream
                </div>
                <div style={{ fontSize: '0.7rem', fontWeight: 600, color: 'var(--accent-cyan)' }}>
                  WEB CLIENT
                </div>
              </div>
            </div>

            {/* Navigation Links */}
            <nav style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <button
                className={`btn ${currentScreen.type === 'home' ? 'btn-primary' : 'btn-secondary'}`}
                style={{ justifyContent: 'flex-start', border: 'none' }}
                onClick={() => setCurrentScreen({ type: 'home' })}
              >
                <Home size={18} /> Home
              </button>

              <button
                className="btn btn-secondary"
                style={{ justifyContent: 'flex-start', border: 'none' }}
                onClick={() => setShowSearchModal(true)}
              >
                <Search size={18} /> Search
              </button>

              <button
                className={`btn ${currentScreen.type === 'library' ? 'btn-primary' : 'btn-secondary'}`}
                style={{ justifyContent: 'flex-start', border: 'none' }}
                onClick={() => setCurrentScreen({ type: 'library' })}
              >
                <Bookmark size={18} /> Library
              </button>

              <button
                className={`btn ${currentScreen.type === 'extensions' ? 'btn-primary' : 'btn-secondary'}`}
                style={{ justifyContent: 'flex-start', border: 'none' }}
                onClick={() => setCurrentScreen({ type: 'extensions' })}
              >
                <Package size={18} /> Extensions
              </button>
            </nav>
          </div>

          <div style={{ padding: '12px', fontSize: '0.78rem', color: 'var(--text-subtle)', textAlign: 'center' }}>
            CloudStream Web v2.0
          </div>
        </aside>
      )}

      {/* Main Content Viewport */}
      <main
        style={{
          flex: 1,
          marginLeft: currentScreen.type !== 'player' ? '240px' : 0,
          padding: currentScreen.type !== 'player' ? '36px 48px' : 0,
          maxWidth: currentScreen.type !== 'player' ? '1400px' : 'none',
          width: '100%',
        }}
      >
        {currentScreen.type === 'home' && (
          <HomeScreen
            onSelectMedia={(provider, url) => setCurrentScreen({ type: 'details', provider, url })}
            onNavigateToExtensions={() => setCurrentScreen({ type: 'extensions' })}
          />
        )}

        {currentScreen.type === 'details' && (
          <DetailsScreen
            provider={currentScreen.provider}
            url={currentScreen.url}
            onBack={() => setCurrentScreen({ type: 'home' })}
            onStartPlayback={(title, subtitleText, availableLinks, subtitles, provider, episodeDataUrl) =>
              setCurrentScreen({
                type: 'player',
                title,
                subtitleText,
                availableLinks,
                subtitles,
                provider,
                episodeDataUrl,
              })
            }
          />
        )}

        {currentScreen.type === 'player' && (
          <PlayerScreen
            title={currentScreen.title}
            subtitleText={currentScreen.subtitleText}
            availableLinks={currentScreen.availableLinks}
            subtitles={currentScreen.subtitles}
            provider={currentScreen.provider}
            episodeDataUrl={currentScreen.episodeDataUrl}
            onBack={() => setCurrentScreen({ type: 'home' })}
          />
        )}

        {currentScreen.type === 'library' && (
          <LibraryScreen
            onSelectMedia={(provider, url) => setCurrentScreen({ type: 'details', provider, url })}
          />
        )}

        {currentScreen.type === 'extensions' && <ExtensionsScreen />}
      </main>

      {/* Global Search Modal */}
      {showSearchModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0,0,0,0.8)',
            backdropFilter: 'blur(12px)',
            zIndex: 9990,
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'flex-start',
            paddingTop: '80px',
          }}
          onClick={() => setShowSearchModal(false)}
        >
          <div
            className="glass-panel animate-fade-in"
            style={{
              width: '90%',
              maxWidth: '800px',
              maxHeight: '80vh',
              overflow: 'hidden',
              display: 'flex',
              flexDirection: 'column',
              padding: '24px',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            {/* Search Input Bar */}
            <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', marginBottom: '20px' }}>
              <div style={{ position: 'relative', flex: 1 }}>
                <Search
                  size={20}
                  style={{
                    position: 'absolute',
                    left: 16,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    color: 'var(--text-muted)',
                  }}
                />
                <input
                  type="text"
                  placeholder="Search movies, anime, tv series across providers..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  autoFocus
                  style={{
                    width: '100%',
                    background: 'rgba(0,0,0,0.5)',
                    border: '1px solid var(--border-glass)',
                    borderRadius: 'var(--radius-md)',
                    padding: '12px 16px 12px 48px',
                    fontSize: '1.05rem',
                    color: '#fff',
                    outline: 'none',
                  }}
                />
              </div>
              <button type="submit" className="btn btn-primary">
                Search
              </button>
              <button
                type="button"
                className="btn btn-secondary btn-icon"
                onClick={() => setShowSearchModal(false)}
              >
                <X size={20} />
              </button>
            </form>

            {/* Results Grid */}
            <div style={{ overflowY: 'auto', flex: 1, paddingRight: '4px' }}>
              {searchLoading ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '60px 0' }}>
                  <div className="spinner" />
                  <p style={{ marginTop: '12px', color: 'var(--text-muted)' }}>Searching providers...</p>
                </div>
              ) : searchResults.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--text-muted)' }}>
                  Type a title and press Search to find media across installed scrapers.
                </div>
              ) : (
                <div
                  style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(160px, 1fr))',
                    gap: '16px',
                  }}
                >
                  {searchResults.map((item, idx) => (
                    <div
                      key={idx}
                      className="poster-card"
                      onClick={() => {
                        setShowSearchModal(false);
                        setCurrentScreen({ type: 'details', provider: item.apiName, url: item.url });
                      }}
                    >
                      <img src={item.posterUrl || 'https://via.placeholder.com/300x450'} alt={item.name} />
                      <div className="poster-overlay">
                        <div className="poster-title">{item.name}</div>
                        <div className="poster-meta">{item.apiName}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default App;
