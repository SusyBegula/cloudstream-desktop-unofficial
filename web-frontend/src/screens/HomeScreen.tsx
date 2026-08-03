import React, { useEffect, useRef, useState } from 'react';
import { Play, ChevronDown, Sparkles, PackagePlus } from 'lucide-react';
import type { MainPageCategoryDto, MainPageRowDto, ProviderDto, SearchResultDto } from '../api/types';
import api from '../api/client';

interface HomeScreenProps {
  onSelectMedia: (provider: string, url: string) => void;
  onNavigateToExtensions?: () => void;
}

interface CategoryRowProps {
  provider: string;
  category: MainPageCategoryDto;
  onSelectMedia: (provider: string, url: string) => void;
  onLoaded: (index: number, row: MainPageRowDto | null) => void;
}

// Fetches its own category's items only once it (nearly) scrolls into view, instead of the
// homepage waiting on every category up front — a provider can have 30+ categories, each a
// separate network round-trip to the provider site.
const CategoryRow: React.FC<CategoryRowProps> = ({ provider, category, onSelectMedia, onLoaded }) => {
  const [row, setRow] = useState<MainPageRowDto | null>(null);
  const [visible, setVisible] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          setVisible(true);
          observer.disconnect();
        }
      },
      { rootMargin: '600px 0px' }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    if (!visible) return;
    let cancelled = false;
    api
      .getMainPageCategory(provider, category.index)
      .then((data) => {
        if (cancelled) return;
        const loaded = data.rows[0] ?? null;
        setRow(loaded);
        onLoaded(category.index, loaded);
      })
      .catch(() => {
        if (!cancelled) onLoaded(category.index, null);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible, provider, category.index]);

  if (row && row.items.length === 0) return null;

  return (
    <div ref={containerRef} style={{ marginBottom: '36px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '16px' }}>
        <h3 style={{ fontSize: '1.35rem', fontWeight: 700 }}>{row?.name || category.name}</h3>
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
          gap: '20px',
        }}
      >
        {row
          ? row.items.map((item, itemIdx) => (
              <div
                key={itemIdx}
                className="poster-card animate-fade-in"
                onClick={() => onSelectMedia(provider, item.url)}
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
            ))
          : Array.from({ length: 6 }).map((_, i) => (
              <div
                key={i}
                className="poster-card"
                style={{ background: 'rgba(255,255,255,0.05)', aspectRatio: '2 / 3' }}
              />
            ))}
      </div>
    </div>
  );
};

export const HomeScreen: React.FC<HomeScreenProps> = ({ onSelectMedia, onNavigateToExtensions }) => {
  const [providers, setProviders] = useState<ProviderDto[]>([]);
  const [selectedProvider, setSelectedProvider] = useState<string>('');
  const [categories, setCategories] = useState<MainPageCategoryDto[]>([]);
  const [loadedRows, setLoadedRows] = useState<Record<number, MainPageRowDto | null>>({});
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

  // Fetch just the category list (instant, no network calls to the provider site) when the
  // selected provider changes. Each category's items are fetched lazily by CategoryRow.
  useEffect(() => {
    if (!selectedProvider) return;
    async function fetchCategories() {
      setLoading(true);
      setError(null);
      setLoadedRows({});
      try {
        const data = await api.getMainPageCategories(selectedProvider);
        setCategories(data.categories);
      } catch (err: any) {
        setError(err.message || 'Failed to load homepage content');
      } finally {
        setLoading(false);
      }
    }
    fetchCategories();
  }, [selectedProvider]);

  const handleRowLoaded = (index: number, row: MainPageRowDto | null) => {
    setLoadedRows((prev) => ({ ...prev, [index]: row }));
  };

  const featuredItem: SearchResultDto | null =
    categories.length > 0 ? loadedRows[categories[0].index]?.items[0] || null : null;

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

          {/* Rows / Carousels, each lazily fetching its own items as it scrolls into view */}
          {categories.map((category) => (
            <CategoryRow
              key={category.index}
              provider={selectedProvider}
              category={category}
              onSelectMedia={onSelectMedia}
              onLoaded={handleRowLoaded}
            />
          ))}
        </>
      )}
    </div>
  );
};

export default HomeScreen;
