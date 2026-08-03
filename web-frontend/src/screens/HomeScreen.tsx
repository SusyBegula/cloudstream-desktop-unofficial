import React, { useEffect, useState } from 'react';
import { Play, ChevronDown, Sparkles, PackagePlus } from 'lucide-react';
import type { HomePageResponseDto, ProviderDto, SearchResultDto } from '../api/types';
import api from '../api/client';

interface HomeScreenProps {
  onSelectMedia: (provider: string, url: string) => void;
  onNavigateToExtensions?: () => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({ onSelectMedia, onNavigateToExtensions }) => {
  const [providers, setProviders] = useState<ProviderDto[]>([]);
  const [selectedProvider, setSelectedProvider] = useState<string>('');
  const [homeData, setHomeData] = useState<HomePageResponseDto | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Fetch Providers list on mount
  useEffect(() => {
    async function loadProviders() {
      try {
        const res = await api.getProviders();
        setProviders(res.providers);
        if (res.providers.length > 0) {
          const mainProvider = res.providers.find((p) => p.hasMainPage) || res.providers[0];
          setSelectedProvider(mainProvider.name);
        } else {
          setLoading(false);
        }
      } catch (err: any) {
        setError(err.message || 'Failed to fetch providers');
        setLoading(false);
      }
    }
    loadProviders();
  }, []);

  // Fetch Homepage Data when selected provider changes
  useEffect(() => {
    if (!selectedProvider) return;
    async function fetchHome() {
      setLoading(true);
      setError(null);
      try {
        const data = await api.getMainPage(selectedProvider);
        setHomeData(data);
      } catch (err: any) {
        setError(err.message || 'Failed to load homepage content');
      } finally {
        setLoading(false);
      }
    }
    fetchHome();
  }, [selectedProvider]);

  const featuredItem: SearchResultDto | null =
    homeData?.rows[0]?.items[0] || null;

  return (
    <div style={{ paddingBottom: '60px' }}>
      {/* Top Bar / Provider Selector */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          marginBottom: '28px',
        }}
      >
        <div>
          <h1 style={{ fontSize: '2rem', fontWeight: 800 }} className="text-gradient">
            Discover & Stream
          </h1>
          <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
            Explore trending movies, anime, and TV shows across providers
          </p>
        </div>

        {/* Provider Switcher Dropdown */}
        {providers.length > 0 && (
          <div style={{ position: 'relative' }}>
            <select
              value={selectedProvider}
              onChange={(e) => setSelectedProvider(e.target.value)}
              className="glass-pill"
              style={{
                padding: '10px 36px 10px 18px',
                fontSize: '0.95rem',
                fontWeight: 600,
                appearance: 'none',
                cursor: 'pointer',
                outline: 'none',
              }}
            >
              {providers.map((p) => (
                <option key={p.name} value={p.name} style={{ backgroundColor: '#12141d', color: '#fff' }}>
                  {p.name} ({p.lang.toUpperCase()})
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
                color: 'var(--text-muted)',
              }}
            />
          </div>
        )}
      </div>

      {loading ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '100px 0' }}>
          <div className="spinner" />
          <p style={{ marginTop: '16px', color: 'var(--text-muted)' }}>Loading provider catalog...</p>
        </div>
      ) : providers.length === 0 ? (
        <div className="glass-panel" style={{ padding: '48px', textAlign: 'center', margin: '40px 0' }}>
          <PackagePlus size={48} style={{ color: 'var(--accent-cyan)', marginBottom: '16px' }} />
          <h2 style={{ fontSize: '1.5rem', fontWeight: 800, marginBottom: '8px' }}>
            No Extension Plugins Installed
          </h2>
          <p style={{ color: 'var(--text-muted)', maxWidth: '500px', margin: '0 auto 24px auto', lineHeight: 1.6 }}>
            You haven't installed any CloudStream `.cs3` plugins yet. Add a repository in the Extensions tab to start streaming!
          </p>
          {onNavigateToExtensions && (
            <button className="btn btn-primary" onClick={onNavigateToExtensions}>
              Go to Extensions Store
            </button>
          )}
        </div>
      ) : error ? (
        <div className="glass-panel" style={{ padding: '32px', textAlign: 'center', margin: '40px 0' }}>
          <p style={{ color: 'var(--accent-pink)', fontWeight: 600, marginBottom: '12px' }}>{error}</p>
          <button className="btn btn-secondary" onClick={() => setSelectedProvider(selectedProvider)}>
            Retry Loading
          </button>
        </div>
      ) : (
        <>
          {/* Featured Hero Banner */}
          {featuredItem && (
            <div
              className="glass-panel animate-fade-in"
              style={{
                position: 'relative',
                height: '380px',
                borderRadius: 'var(--radius-lg)',
                overflow: 'hidden',
                marginBottom: '40px',
                display: 'flex',
                alignItems: 'flex-end',
              }}
            >
              <img
                src={featuredItem.posterUrl || ''}
                alt={featuredItem.name}
                style={{
                  position: 'absolute',
                  inset: 0,
                  width: '100%',
                  height: '100%',
                  objectFit: 'cover',
                  filter: 'brightness(0.55)',
                }}
              />
              <div
                style={{
                  position: 'absolute',
                  inset: 0,
                  background: 'linear-gradient(to top, rgba(8,9,12,0.95) 0%, rgba(8,9,12,0.4) 60%, transparent 100%)',
                }}
              />
              <div style={{ position: 'relative', zIndex: 2, padding: '36px', maxWidth: '650px' }}>
                <div className="glass-pill" style={{ marginBottom: '12px', background: 'rgba(99,102,241,0.25)', borderColor: 'var(--accent-primary)' }}>
                  <Sparkles size={14} style={{ color: 'var(--accent-cyan)' }} />
                  <span>Featured on {selectedProvider}</span>
                </div>
                <h2 style={{ fontSize: '2.4rem', fontWeight: 800, lineHeight: 1.1, marginBottom: '12px' }}>
                  {featuredItem.name}
                </h2>
                <div style={{ display: 'flex', gap: '12px', alignItems: 'center', marginBottom: '20px', fontSize: '0.9rem', color: 'var(--text-muted)' }}>
                  {featuredItem.year && (
                    <span className="glass-pill">{featuredItem.year}</span>
                  )}
                  {featuredItem.quality && (
                    <span className="glass-pill" style={{ color: 'var(--accent-cyan)' }}>{featuredItem.quality}</span>
                  )}
                  {featuredItem.type && (
                    <span style={{ textTransform: 'capitalize' }}>{featuredItem.type}</span>
                  )}
                </div>
                <button
                  className="btn btn-primary"
                  onClick={() => onSelectMedia(selectedProvider, featuredItem.url)}
                  style={{ padding: '12px 28px', fontSize: '1rem' }}
                >
                  <Play size={20} fill="#fff" /> Watch Now
                </button>
              </div>
            </div>
          )}

          {/* Rows / Carousels */}
          {homeData?.rows.map((row, rowIdx) => (
            <div key={rowIdx} style={{ marginBottom: '36px' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
                <h3 style={{ fontSize: '1.35rem', fontWeight: 700 }}>{row.name}</h3>
              </div>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                  gap: '20px',
                }}
              >
                {row.items.map((item, itemIdx) => (
                  <div
                    key={itemIdx}
                    className="poster-card animate-fade-in"
                    onClick={() => onSelectMedia(selectedProvider, item.url)}
                  >
                    <img
                      src={item.posterUrl || 'https://via.placeholder.com/300x450?text=No+Poster'}
                      alt={item.name}
                      loading="lazy"
                    />
                    {item.quality && <div className="poster-badge">{item.quality}</div>}
                    <div className="poster-overlay">
                      <div className="poster-title">{item.name}</div>
                      <div className="poster-meta">
                        {item.year && <span>{item.year}</span>}
                        {item.type && <span>• {item.type}</span>}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ))}
        </>
      )}
    </div>
  );
};

export default HomeScreen;
