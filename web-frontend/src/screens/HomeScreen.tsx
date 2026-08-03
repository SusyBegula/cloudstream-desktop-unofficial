import React, { useEffect, useRef, useState } from 'react';
import { Play, Info, ChevronLeft, ChevronRight, PackagePlus } from 'lucide-react';
import type { MainPageCategoryDto, MainPageRowDto, ProviderDto, SearchResultDto } from '../api/types';
import api from '../api/client';

interface HomeScreenProps {
  selectedProvider: string;
  onSelectProvider: (provider: string) => void;
  providers: ProviderDto[];
  onSelectMedia: (provider: string, url: string) => void;
  onNavigateToExtensions?: () => void;
}

interface CategoryRowProps {
  provider: string;
  category: MainPageCategoryDto;
  onSelectMedia: (provider: string, url: string) => void;
  onLoaded: (index: number, row: MainPageRowDto | null) => void;
}

const CategoryRow: React.FC<CategoryRowProps> = ({ provider, category, onSelectMedia, onLoaded }) => {
  const [row, setRow] = useState<MainPageRowDto | null>(null);
  const [visible, setVisible] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

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

  const scroll = (direction: 'left' | 'right') => {
    if (scrollRef.current) {
      const scrollAmount = direction === 'left' ? -540 : 540;
      scrollRef.current.scrollBy({ left: scrollAmount, behavior: 'smooth' });
    }
  };

  if (row && row.items.length === 0) return null;

  return (
    <div ref={containerRef} className="netflix-row">
      <h3 className="netflix-row-title">
        {row?.name || category.name}
      </h3>

      <div style={{ position: 'relative' }}>
        {row && row.items.length > 5 && (
          <>
            <button className="row-nav-btn left" onClick={() => scroll('left')} aria-label="Scroll left">
              <ChevronLeft size={28} />
            </button>
            <button className="row-nav-btn right" onClick={() => scroll('right')} aria-label="Scroll right">
              <ChevronRight size={28} />
            </button>
          </>
        )}

        <div ref={scrollRef} className="row-container no-scrollbar">
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
                  <div className="poster-play-btn">
                    <Play size={20} fill="#000" style={{ marginLeft: 2 }} />
                  </div>
                  <div className="poster-overlay">
                    <div className="poster-title">{item.name}</div>
                    <div className="poster-meta">
                      <span>98% Match</span>
                      {item.year && <span style={{ color: 'var(--text-muted)' }}>• {item.year}</span>}
                    </div>
                  </div>
                </div>
              ))
            : Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="poster-card skeleton" />
              ))}
        </div>
      </div>
    </div>
  );
};

export const HomeScreen: React.FC<HomeScreenProps> = ({
  selectedProvider,
  providers,
  onSelectMedia,
  onNavigateToExtensions,
}) => {
  const [categories, setCategories] = useState<MainPageCategoryDto[]>([]);
  const [loadedRows, setLoadedRows] = useState<Record<number, MainPageRowDto | null>>({});
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // Fetch categories when selected provider changes
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
    <div style={{ paddingBottom: '80px' }}>
      {loading ? (
        <div style={{ padding: '120px 4% 0 4%' }}>
          <div className="skeleton" style={{ height: '60vh', marginBottom: '40px', borderRadius: 'var(--radius-sm)' }} />
          <div className="skeleton" style={{ height: '200px', marginBottom: '32px' }} />
        </div>
      ) : providers.length === 0 ? (
        <div style={{ padding: '140px 4% 40px 4%', textAlign: 'center', maxWidth: '600px', margin: '0 auto' }}>
          <PackagePlus size={64} style={{ color: 'var(--netflix-red)', marginBottom: '20px' }} />
          <h2 style={{ fontSize: '1.8rem', fontWeight: 800, marginBottom: '12px' }}>
            No Extension Plugins Installed
          </h2>
          <p style={{ color: 'var(--text-muted)', marginBottom: '32px', lineHeight: 1.6 }}>
            You haven't installed any CloudStream `.cs3` plugins yet. Add a repository in the Extensions tab to start streaming!
          </p>
          {onNavigateToExtensions && (
            <button className="btn btn-primary" onClick={onNavigateToExtensions} style={{ padding: '14px 36px', fontSize: '1rem' }}>
              Go to Extensions Store
            </button>
          )}
        </div>
      ) : error ? (
        <div style={{ padding: '140px 4% 40px 4%', textAlign: 'center', maxWidth: '600px', margin: '0 auto' }}>
          <p style={{ color: 'var(--netflix-red)', fontWeight: 700, marginBottom: '20px', fontSize: '1.2rem' }}>{error}</p>
        </div>
      ) : (
        <>
          {/* Netflix Hero Billboard */}
          {featuredItem && (
            <div className="hero-billboard animate-fade-in">
              <img
                src={featuredItem.posterUrl || ''}
                alt={featuredItem.name}
                style={{
                  position: 'absolute',
                  inset: 0,
                  width: '100%',
                  height: '100%',
                  objectFit: 'cover',
                  objectPosition: 'center 20%',
                  filter: 'brightness(0.75)',
                }}
              />
              <div className="billboard-vignette-left" />
              <div className="billboard-vignette-bottom" />

              <div style={{ position: 'relative', zIndex: 5, maxWidth: '640px' }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '12px' }}>
                  <div style={{ width: 16, height: 16, borderRadius: 2, background: 'var(--netflix-red)', color: '#fff', fontSize: '0.65rem', fontWeight: 900, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    N
                  </div>
                  <span style={{ fontSize: '0.82rem', fontWeight: 800, letterSpacing: '2px', color: '#E5E5E5' }}>
                    FEATURED SELECTION
                  </span>
                </div>

                <h1 style={{ fontSize: '3.2rem', fontWeight: 900, lineHeight: 1.1, marginBottom: '16px', textShadow: '0 4px 16px rgba(0,0,0,0.8)', letterSpacing: '-0.5px' }}>
                  {featuredItem.name}
                </h1>

                <div style={{ display: 'flex', gap: '12px', alignItems: 'center', marginBottom: '24px', fontSize: '0.92rem', color: '#fff', fontWeight: 600 }}>
                  <span style={{ color: 'var(--netflix-green)', fontWeight: 800 }}>98% Match</span>
                  {featuredItem.year && <span>{featuredItem.year}</span>}
                  {featuredItem.quality && (
                    <span style={{ border: '1px solid rgba(255,255,255,0.4)', padding: '1px 6px', borderRadius: 2, fontSize: '0.75rem' }}>
                      {featuredItem.quality}
                    </span>
                  )}
                  {featuredItem.type && (
                    <span style={{ textTransform: 'capitalize', color: 'var(--text-muted)' }}>{featuredItem.type}</span>
                  )}
                </div>

                <div style={{ display: 'flex', gap: '14px', alignItems: 'center' }}>
                  <button
                    className="btn btn-netflix-white"
                    onClick={() => onSelectMedia(selectedProvider, featuredItem.url)}
                    style={{ padding: '12px 32px', fontSize: '1.05rem' }}
                  >
                    <Play size={22} fill="#000" style={{ marginLeft: 2 }} /> Play
                  </button>

                  <button
                    className="btn btn-netflix-dark"
                    onClick={() => onSelectMedia(selectedProvider, featuredItem.url)}
                    style={{ padding: '12px 28px', fontSize: '1.05rem' }}
                  >
                    <Info size={22} /> More Info
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Netflix Category Rows */}
          <div style={{ marginTop: featuredItem ? '-40px' : '100px', position: 'relative', zIndex: 10 }}>
            {categories.map((category) => (
              <CategoryRow
                key={category.index}
                provider={selectedProvider}
                category={category}
                onSelectMedia={onSelectMedia}
                onLoaded={handleRowLoaded}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );
};

export default HomeScreen;


