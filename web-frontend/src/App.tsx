import React, { useState, useEffect } from 'react';
import { Search, X, Play, ChevronDown } from 'lucide-react';
import type { ExtractorLinkDto, SearchResultDto, SubtitleFileDto, ProviderDto } from './api/types';
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

  // Providers & Provider Selector state
  const [providers, setProviders] = useState<ProviderDto[]>([]);
  const [selectedProvider, setSelectedProvider] = useState<string>('');

  // Global Search Modal state
  const [showSearchModal, setShowSearchModal] = useState<boolean>(false);
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [searchResults, setSearchResults] = useState<SearchResultDto[]>([]);
  const [searchLoading, setSearchLoading] = useState<boolean>(false);

  // Scroll state for Netflix top header opacity transition
  const [isScrolled, setIsScrolled] = useState<boolean>(false);

  useEffect(() => {
    async function loadProviders() {
      try {
        const res = await api.getProviders();
        setProviders(res.providers);
        if (res.providers.length > 0) {
          const mainProvider = res.providers.find((p) => p.hasMainPage) || res.providers[0];
          setSelectedProvider(mainProvider.name);
        }
      } catch (err) {
        console.error('Failed to load providers:', err);
      }
    }
    loadProviders();
  }, []);

  useEffect(() => {
    const handleScroll = () => {
      setIsScrolled(window.scrollY > 30);
    };
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  // Keyboard shortcut listener for Cmd+K / Ctrl+K
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
        e.preventDefault();
        setShowSearchModal((prev) => !prev);
      } else if (e.key === 'Escape' && showSearchModal) {
        setShowSearchModal(false);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [showSearchModal]);

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
    <div style={{ minHeight: '100vh', backgroundColor: 'var(--bg-dark)' }}>
      {/* Netflix Top Header Navigation */}
      {currentScreen.type !== 'player' && (
        <header className={`netflix-header ${isScrolled ? 'scrolled' : ''}`}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '36px' }}>
            {/* Netflix Brand Logo */}
            <div
              style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}
              onClick={() => setCurrentScreen({ type: 'home' })}
            >
              <div
                style={{
                  width: '32px',
                  height: '32px',
                  borderRadius: 'var(--radius-sm)',
                  backgroundColor: 'var(--netflix-red)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  fontWeight: 900,
                  fontSize: '1.2rem',
                  color: '#fff',
                }}
              >
                N
              </div>
              <span style={{ fontWeight: 900, fontSize: '1.4rem', letterSpacing: '-0.8px', color: 'var(--netflix-red)' }}>
                CLOUDSTREAM
              </span>
            </div>

            {/* Top Primary Nav Links */}
            <nav style={{ display: 'flex', gap: '24px', alignItems: 'center' }}>
              <span
                style={{
                  fontSize: '0.92rem',
                  fontWeight: currentScreen.type === 'home' ? 700 : 400,
                  color: currentScreen.type === 'home' ? '#FFFFFF' : 'var(--text-muted)',
                  cursor: 'pointer',
                  transition: 'color 0.2s',
                }}
                onClick={() => setCurrentScreen({ type: 'home' })}
              >
                Home
              </span>

              <span
                style={{
                  fontSize: '0.92rem',
                  fontWeight: currentScreen.type === 'library' ? 700 : 400,
                  color: currentScreen.type === 'library' ? '#FFFFFF' : 'var(--text-muted)',
                  cursor: 'pointer',
                  transition: 'color 0.2s',
                }}
                onClick={() => setCurrentScreen({ type: 'library' })}
              >
                My List
              </span>

              <span
                style={{
                  fontSize: '0.92rem',
                  fontWeight: currentScreen.type === 'extensions' ? 700 : 400,
                  color: currentScreen.type === 'extensions' ? '#FFFFFF' : 'var(--text-muted)',
                  cursor: 'pointer',
                  transition: 'color 0.2s',
                }}
                onClick={() => setCurrentScreen({ type: 'extensions' })}
              >
                Extensions
              </span>
            </nav>
          </div>

          {/* Right Header Actions */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
            {/* Provider Switcher Dropdown */}
            {providers.length > 0 && (
              <div style={{ position: 'relative' }}>
                <select
                  value={selectedProvider}
                  onChange={(e) => setSelectedProvider(e.target.value)}
                  style={{
                    backgroundColor: 'rgba(0,0,0,0.6)',
                    color: '#fff',
                    border: '1px solid rgba(255,255,255,0.2)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '6px 30px 6px 12px',
                    fontSize: '0.85rem',
                    fontWeight: 600,
                    appearance: 'none',
                    cursor: 'pointer',
                    outline: 'none',
                  }}
                >
                  {providers.map((p) => (
                    <option key={p.name} value={p.name} style={{ backgroundColor: '#141414', color: '#fff' }}>
                      {p.name} ({p.lang.toUpperCase()})
                    </option>
                  ))}
                </select>
                <ChevronDown
                  size={14}
                  style={{
                    position: 'absolute',
                    right: 8,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    pointerEvents: 'none',
                    color: 'var(--text-muted)',
                  }}
                />
              </div>
            )}

            {/* Quick Search Button */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
                background: 'rgba(255,255,255,0.1)',
                border: '1px solid rgba(255,255,255,0.15)',
                borderRadius: 'var(--radius-sm)',
                padding: '6px 12px',
                cursor: 'pointer',
                fontSize: '0.85rem',
                color: 'var(--text-muted)',
              }}
              onClick={() => setShowSearchModal(true)}
            >
              <Search size={16} color="#fff" />
              <span>Search</span>
              <div
                style={{
                  background: 'rgba(255,255,255,0.15)',
                  padding: '1px 5px',
                  borderRadius: '3px',
                  fontSize: '0.7rem',
                  fontWeight: 600,
                  color: '#fff',
                }}
              >
                ⌘K
              </div>
            </div>
          </div>
        </header>
      )}

      {/* Main Content Viewport */}
      <main style={{ minHeight: '100vh', width: '100%' }}>
        {currentScreen.type === 'home' && (
          <HomeScreen
            selectedProvider={selectedProvider}
            onSelectProvider={setSelectedProvider}
            providers={providers}
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

      {/* Command Palette Search Overlay */}
      {showSearchModal && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.88)',
            backdropFilter: 'blur(12px)',
            zIndex: 9990,
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'flex-start',
            paddingTop: '90px',
          }}
          onClick={() => setShowSearchModal(false)}
        >
          <div
            className="animate-fade-in"
            style={{
              width: '92%',
              maxWidth: '820px',
              maxHeight: '80vh',
              overflow: 'hidden',
              display: 'flex',
              flexDirection: 'column',
              padding: '24px',
              backgroundColor: '#181818',
              borderRadius: 'var(--radius-md)',
              border: '1px solid rgba(255,255,255,0.15)',
              boxShadow: '0 20px 50px rgba(0,0,0,0.9)',
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <form onSubmit={handleSearchSubmit} style={{ display: 'flex', gap: '12px', marginBottom: '20px' }}>
              <div style={{ position: 'relative', flex: 1 }}>
                <Search
                  size={20}
                  style={{
                    position: 'absolute',
                    left: 16,
                    top: '50%',
                    transform: 'translateY(-50%)',
                    color: 'var(--netflix-red)',
                  }}
                />
                <input
                  type="text"
                  placeholder="Search movies, anime, tv shows..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  autoFocus
                  style={{
                    width: '100%',
                    background: '#222',
                    border: '1px solid rgba(255,255,255,0.15)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '14px 16px 14px 48px',
                    fontSize: '1.05rem',
                    color: '#fff',
                    outline: 'none',
                    fontFamily: 'var(--font-primary)',
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

            <div className="no-scrollbar" style={{ overflowY: 'auto', flex: 1, paddingRight: '4px' }}>
              {searchLoading ? (
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '60px 0' }}>
                  <div className="spinner" />
                  <p style={{ marginTop: '16px', color: 'var(--text-muted)' }}>Searching providers...</p>
                </div>
              ) : searchResults.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '60px 0', color: 'var(--text-muted)' }}>
                  <p style={{ fontSize: '1rem', fontWeight: 600 }}>Type a title to search</p>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-subtle)', marginTop: '4px' }}>
                    Press Enter to search across active installed extensions.
                  </p>
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
                      className="poster-card animate-fade-in"
                      onClick={() => {
                        setShowSearchModal(false);
                        setCurrentScreen({ type: 'details', provider: item.apiName, url: item.url });
                      }}
                    >
                      <img src={item.posterUrl || 'https://via.placeholder.com/300x450'} alt={item.name} />
                      <div className="poster-play-btn">
                        <Play size={20} fill="#000" style={{ marginLeft: 2 }} />
                      </div>
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


