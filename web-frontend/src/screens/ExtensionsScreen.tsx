import React, { useEffect, useState } from 'react';
import { Package, Plus, Trash2, Download } from 'lucide-react';
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
    <div style={{ paddingBottom: '60px' }}>
      {/* Header */}
      <div style={{ marginBottom: '28px' }}>
        <h1 style={{ fontSize: '2rem', fontWeight: 800 }} className="text-gradient">
          Extensions & Plugins
        </h1>
        <p style={{ color: 'var(--text-muted)', fontSize: '0.95rem' }}>
          Install CloudStream `.cs3` plugins to enable content providers
        </p>
      </div>

      {/* Add Repository Form */}
      <div className="glass-panel" style={{ padding: '20px', marginBottom: '32px' }}>
        <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '12px' }}>Add Plugin Repository</h3>
        <form onSubmit={handleAddRepo} style={{ display: 'flex', gap: '12px' }}>
          <input
            type="text"
            placeholder="https://raw.githubusercontent.com/.../repo.json or cloudstreamrepo://"
            value={newRepoUrl}
            onChange={(e) => setNewRepoUrl(e.target.value)}
            disabled={isAddingRepo}
            style={{
              flex: 1,
              background: 'rgba(0,0,0,0.4)',
              border: '1px solid var(--border-glass)',
              borderRadius: 'var(--radius-md)',
              padding: '10px 16px',
              color: '#fff',
              outline: 'none',
              fontFamily: 'var(--font-primary)',
            }}
          />
          <button type="submit" className="btn btn-primary" disabled={isAddingRepo || !newRepoUrl.trim()}>
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
        <div className="glass-panel" style={{ padding: '16px 20px', marginBottom: '24px', borderColor: 'var(--accent-pink)' }}>
          <p style={{ color: 'var(--accent-pink)', fontSize: '0.9rem', fontWeight: 600 }}>{errorMessage}</p>
        </div>
      )}

      {/* Repositories Tabs */}
      {repositories.length > 0 && (
        <div style={{ display: 'flex', gap: '10px', overflowX: 'auto', paddingBottom: '12px', marginBottom: '24px' }}>
          {repositories.map((repo, idx) => (
            <div
              key={idx}
              className={`glass-pill ${selectedRepoUrl === repo.url ? 'btn-primary' : ''}`}
              style={{ cursor: 'pointer', padding: '8px 16px' }}
              onClick={() => handleSelectRepo(repo.url)}
            >
              <span>{repo.name}</span>
              <Trash2
                size={14}
                style={{ marginLeft: 8, opacity: 0.7 }}
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
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', padding: '80px 0' }}>
          <div className="spinner" />
          <p style={{ marginTop: '16px', color: 'var(--text-muted)' }}>Fetching extension catalog...</p>
        </div>
      ) : catalogPlugins.length === 0 ? (
        <div className="glass-panel" style={{ padding: '40px', textAlign: 'center' }}>
          <Package size={40} style={{ color: 'var(--text-subtle)', marginBottom: '12px' }} />
          <p style={{ color: 'var(--text-muted)' }}>
            {selectedRepoUrl
              ? 'No plugins found in this repository.'
              : 'No repositories added yet. Paste a CloudStream repo URL above to start.'}
          </p>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: '20px' }}>
          {catalogPlugins.map((plugin, idx) => {
            const isInstalled = installedPlugins.some((p) => p.internalName === plugin.internalName);
            const isLoadingThis = actionLoading === plugin.internalName;

            return (
              <div key={idx} className="glass-panel animate-fade-in" style={{ padding: '20px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
                    <h4 style={{ fontSize: '1.1rem', fontWeight: 700 }}>{plugin.name}</h4>
                    <span className="glass-pill" style={{ fontSize: '0.75rem', padding: '2px 8px' }}>
                      v{plugin.version}
                    </span>
                  </div>
                  <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', marginBottom: '16px' }}>
                    Internal Name: {plugin.internalName}
                  </p>
                </div>

                <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                  {isInstalled ? (
                    <button
                      className="btn btn-secondary"
                      onClick={() => handleUninstallPlugin(plugin)}
                      disabled={isLoadingThis}
                      style={{ fontSize: '0.85rem' }}
                    >
                      {isLoadingThis ? (
                        <div className="spinner" style={{ width: 16, height: 16 }} />
                      ) : (
                        <Trash2 size={16} style={{ color: 'var(--accent-pink)' }} />
                      )}
                      <span>Uninstall</span>
                    </button>
                  ) : (
                    <button
                      className="btn btn-primary"
                      onClick={() => handleInstallPlugin(plugin)}
                      disabled={isLoadingThis}
                      style={{ fontSize: '0.85rem' }}
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
