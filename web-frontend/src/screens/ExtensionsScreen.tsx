import React, { useEffect, useState } from 'react';
import { Package, Plus, Trash2, Download, CheckCircle2, Server } from 'lucide-react';
import type { RepositoryDto, SitePluginDto } from '../api/types';
import api from '../api/client';

export const ExtensionsScreen: React.FC = () => {
  const [repositories, setRepositories] = useState<RepositoryDto[]>([]);
  const [installedPlugins, setInstalledPlugins] = useState<SitePluginDto[]>([]);
  const [catalogPlugins, setCatalogPlugins] = useState<SitePluginDto[]>([]);
  const [selectedRepoUrl, setSelectedRepoUrl] = useState<string>('');
  const [newRepoUrl, setNewRepoUrl] = useState<string>('');
  const [loading, setLoading] = useState<boolean>(true);
  const [isAddingRepo, setIsAddingRepo] = useState<boolean>(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    async function loadPlugins() {
      setLoading(true);
      setErrorMessage(null);
      try {
        const repos = await api.getRepositories();
        const validRepos = repos.filter((r) => r.url && r.url.trim());
        setRepositories(validRepos);

        const installed = await api.getInstalledPlugins();
        setInstalledPlugins(installed.plugins);

        if (validRepos.length > 0) {
          const firstRepo = validRepos[0].url;
          if (firstRepo && firstRepo.trim()) {
            setSelectedRepoUrl(firstRepo);
            const catalog = await api.getPluginCatalog(firstRepo);
            setCatalogPlugins(catalog.plugins);
          }
        }
      } catch (err: any) {
        console.error('Failed to fetch extensions/plugins:', err);
      } finally {
        setLoading(false);
      }
    }
    loadPlugins();
  }, []);

  const handleSelectRepo = async (repoUrl: string) => {
    if (!repoUrl || !repoUrl.trim()) return;
    setSelectedRepoUrl(repoUrl);
    setLoading(true);
    setErrorMessage(null);
    try {
      const catalog = await api.getPluginCatalog(repoUrl);
      setCatalogPlugins(catalog.plugins);
    } catch (err: any) {
      console.error('Failed to load plugin catalog:', err);
      setErrorMessage(err.message || 'Failed to fetch catalog for repository');
    } finally {
      setLoading(false);
    }
  };

  const handleAddRepo = async (e: React.FormEvent) => {
    e.preventDefault();
    const targetUrl = newRepoUrl.trim();
    if (!targetUrl) return;

    setIsAddingRepo(true);
    setErrorMessage(null);

    try {
      const repoName = targetUrl.split('/').pop() || 'Repository';
      await api.addRepository({ name: repoName, url: targetUrl });

      const updatedRepos = await api.getRepositories();
      const validRepos = updatedRepos.filter((r) => r.url && r.url.trim());
      setRepositories(validRepos);
      setNewRepoUrl('');

      const activeRepo = validRepos.find((r) => r.url === targetUrl) || validRepos[validRepos.length - 1];
      if (activeRepo && activeRepo.url) {
        await handleSelectRepo(activeRepo.url);
      }
    } catch (err: any) {
      console.error('Failed to add repo:', err);
      setErrorMessage(err.message || 'Could not add repository. Ensure backend server is running and URL is valid.');
    } finally {
      setIsAddingRepo(false);
    }
  };

  const handleRemoveRepo = async (url: string) => {
    try {
      await api.removeRepository(url);
      const updated = repositories.filter((r) => r.url !== url);
      setRepositories(updated);
      if (selectedRepoUrl === url && updated.length > 0) {
        handleSelectRepo(updated[0].url);
      } else if (updated.length === 0) {
        setCatalogPlugins([]);
        setSelectedRepoUrl('');
      }
    } catch (err) {
      console.error('Failed to remove repo:', err);
    }
  };

  const handleInstallPlugin = async (plugin: SitePluginDto) => {
    setActionLoading(plugin.internalName);
    try {
      await api.installPlugin(plugin.repositoryUrl || selectedRepoUrl, plugin.internalName);
      const updatedInstalled = await api.getInstalledPlugins();
      setInstalledPlugins(updatedInstalled.plugins);
      if (selectedRepoUrl) handleSelectRepo(selectedRepoUrl);
    } catch (err) {
      console.error('Failed to install plugin:', err);
    } finally {
      setActionLoading(null);
    }
  };

  const handleUninstallPlugin = async (plugin: SitePluginDto) => {
    setActionLoading(plugin.internalName);
    try {
      await api.uninstallPlugin(plugin.internalName);
      const updatedInstalled = await api.getInstalledPlugins();
      setInstalledPlugins(updatedInstalled.plugins);
      if (selectedRepoUrl) handleSelectRepo(selectedRepoUrl);
    } catch (err) {
      console.error('Failed to uninstall plugin:', err);
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <div style={{ padding: '90px 4% 80px 4%' }}>
      {/* Header */}
      <div style={{ marginBottom: '32px' }}>
        <h1 style={{ fontSize: '2.5rem', fontWeight: 900, letterSpacing: '-0.5px' }}>
          Extensions & Plugins
        </h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem', marginTop: '4px' }}>
          Install CloudStream `.cs3` plugins to enable streaming scrapers
        </p>
      </div>

      {/* Add Repository Form */}
      <div style={{ backgroundColor: '#181818', padding: '24px', borderRadius: 'var(--radius-sm)', marginBottom: '36px', border: '1px solid rgba(255,255,255,0.08)' }}>
        <h3 style={{ fontSize: '1.15rem', fontWeight: 800, marginBottom: '14px', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <Server size={18} style={{ color: 'var(--netflix-red)' }} />
          <span>Add Plugin Repository</span>
        </h3>
        <form onSubmit={handleAddRepo} style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
          <input
            type="text"
            placeholder="Paste repository JSON URL (e.g. https://raw.githubusercontent.com/.../repo.json)"
            value={newRepoUrl}
            onChange={(e) => setNewRepoUrl(e.target.value)}
            disabled={isAddingRepo}
            style={{
              flex: 1,
              minWidth: '280px',
              background: '#222',
              border: '1px solid rgba(255,255,255,0.15)',
              borderRadius: 'var(--radius-sm)',
              padding: '12px 18px',
              color: '#fff',
              outline: 'none',
              fontFamily: 'var(--font-primary)',
              fontSize: '0.95rem',
            }}
          />
          <button type="submit" className="btn btn-primary" disabled={isAddingRepo || !newRepoUrl.trim()} style={{ padding: '12px 24px' }}>
            {isAddingRepo ? (
              <div className="spinner" style={{ width: 18, height: 18 }} />
            ) : (
              <Plus size={18} />
            )}
            <span>{isAddingRepo ? 'Adding...' : 'Add Repository'}</span>
          </button>
        </form>
      </div>

      {errorMessage && (
        <div style={{ backgroundColor: 'rgba(229, 9, 20, 0.15)', padding: '18px 24px', borderRadius: 'var(--radius-sm)', marginBottom: '28px', border: '1px solid var(--netflix-red)' }}>
          <p style={{ color: '#fff', fontSize: '0.92rem', fontWeight: 600 }}>{errorMessage}</p>
        </div>
      )}

      {/* Repositories Tabs */}
      {repositories.length > 0 && (
        <div style={{ display: 'flex', gap: '10px', overflowX: 'auto', paddingBottom: '12px', marginBottom: '28px' }}>
          {repositories.map((repo, idx) => (
            <div
              key={idx}
              className={`btn ${selectedRepoUrl === repo.url ? 'btn-primary' : 'btn-secondary'}`}
              style={{ cursor: 'pointer', padding: '8px 18px', fontSize: '0.88rem' }}
              onClick={() => handleSelectRepo(repo.url)}
            >
              <span>{repo.name}</span>
              <Trash2
                size={14}
                style={{ marginLeft: 8, opacity: 0.8 }}
                onClick={(e) => {
                  e.stopPropagation();
                  handleRemoveRepo(repo.url);
                }}
              />
            </div>
          ))}
        </div>
      )}

      {/* Plugin Cards Grid */}
      {loading ? (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '20px' }}>
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="skeleton" style={{ height: '140px' }} />
          ))}
        </div>
      ) : catalogPlugins.length === 0 ? (
        <div style={{ padding: '80px', textAlign: 'center', color: 'var(--text-muted)' }}>
          <Package size={56} style={{ color: 'var(--text-subtle)', marginBottom: '16px' }} />
          <p style={{ fontSize: '1.05rem', fontWeight: 600 }}>
            {selectedRepoUrl
              ? 'No plugins found in this repository.'
              : 'No repositories added yet. Paste a CloudStream repo URL above to start.'}
          </p>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '22px' }}>
          {catalogPlugins.map((plugin, idx) => {
            const isInstalled = installedPlugins.some((p) => p.internalName === plugin.internalName);
            const isLoadingThis = actionLoading === plugin.internalName;

            return (
              <div
                key={idx}
                className="animate-fade-in"
                style={{
                  padding: '24px',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                  backgroundColor: '#181818',
                  borderRadius: 'var(--radius-sm)',
                  border: isInstalled ? '1px solid var(--netflix-red)' : '1px solid rgba(255,255,255,0.08)',
                }}
              >
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
                    <h4 style={{ fontSize: '1.15rem', fontWeight: 800, color: '#fff' }}>{plugin.name}</h4>
                    <span style={{ fontSize: '0.75rem', padding: '2px 8px', borderRadius: 2, background: 'rgba(255,255,255,0.1)', color: '#fff' }}>
                      v{plugin.version}
                    </span>
                  </div>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '20px' }}>
                    ID: {plugin.internalName}
                  </p>
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  {isInstalled ? (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', color: 'var(--netflix-green)', fontSize: '0.82rem', fontWeight: 800 }}>
                      <CheckCircle2 size={16} /> Installed
                    </div>
                  ) : <div />}

                  {isInstalled ? (
                    <button
                      className="btn btn-secondary"
                      onClick={() => handleUninstallPlugin(plugin)}
                      disabled={isLoadingThis}
                      style={{ fontSize: '0.88rem', padding: '8px 16px' }}
                    >
                      {isLoadingThis ? (
                        <div className="spinner" style={{ width: 16, height: 16 }} />
                      ) : (
                        <Trash2 size={16} style={{ color: 'var(--netflix-red)' }} />
                      )}
                      <span>Uninstall</span>
                    </button>
                  ) : (
                    <button
                      className="btn btn-primary"
                      onClick={() => handleInstallPlugin(plugin)}
                      disabled={isLoadingThis}
                      style={{ fontSize: '0.88rem', padding: '8px 18px' }}
                    >
                      {isLoadingThis ? (
                        <div className="spinner" style={{ width: 16, height: 16 }} />
                      ) : (
                        <Download size={16} />
                      )}
                      <span>Install Plugin</span>
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default ExtensionsScreen;


