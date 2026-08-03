import type {
  ActionResult,
  AggregatedSearchResponse,
  BookmarkDto,
  BookmarksResponse,
  ExtractorLinkDto,
  HomePageResponseDto,
  InstalledPluginsResponse,
  LinkEventDto,
  LoadResponseDto,
  PlayableStreamDto,
  PluginCatalogResponse,
  ProviderListResponse,
  RepositoryDto,
  WatchHistoryEntryDto,
  WatchHistoryResponse,
} from './types';

const BASE_URL = window.location.origin.includes('3000') 
  ? '' // In Vite dev server mode, proxy handles /api
  : window.location.origin;

class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string = BASE_URL) {
    this.baseUrl = baseUrl;
  }

  private async fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
    const res = await fetch(url, options);
    if (!res.ok) {
      const errText = await res.text().catch(() => res.statusText);
      const method = options?.method || 'GET';
      console.error(`[API] ${method} ${url} → ${res.status}`, errText);
      throw new Error(`API Request failed (${res.status}) on ${method} ${url.split('?')[0].split('/').slice(-2).join('/')}: ${errText}`);
    }
    return res.json();
  }

  async getProviders(): Promise<ProviderListResponse> {
    return this.fetchJson<ProviderListResponse>(`${this.baseUrl}/api/providers`);
  }

  async getMainPage(provider: string, page: number = 1): Promise<HomePageResponseDto> {
    return this.fetchJson<HomePageResponseDto>(
      `${this.baseUrl}/api/providers/${encodeURIComponent(provider)}/main-page?page=${page}`
    );
  }

  async search(query: string, provider?: string, global: boolean = false): Promise<AggregatedSearchResponse> {
    const params = new URLSearchParams({ q: query });
    if (provider) params.append('provider', provider);
    if (global) params.append('global', 'true');
    return this.fetchJson<AggregatedSearchResponse>(`${this.baseUrl}/api/search?${params.toString()}`);
  }

  async load(provider: string, url: string): Promise<LoadResponseDto> {
    return this.fetchJson<LoadResponseDto>(`${this.baseUrl}/api/load`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider, url }),
    });
  }

  async resolve(link: ExtractorLinkDto): Promise<PlayableStreamDto> {
    return this.fetchJson<PlayableStreamDto>(`${this.baseUrl}/api/resolve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ link }),
    });
  }

  async getHistory(): Promise<WatchHistoryResponse> {
    return this.fetchJson<WatchHistoryResponse>(`${this.baseUrl}/api/history`);
  }

  async patchHistory(entry: WatchHistoryEntryDto): Promise<void> {
    await this.fetchJson<void>(`${this.baseUrl}/api/history`, {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(entry),
    });
  }

  async deleteHistory(entry: WatchHistoryEntryDto): Promise<void> {
    const params = new URLSearchParams({
      provider: entry.provider,
      url: entry.url,
    });
    if (entry.season !== undefined && entry.season !== null) params.append('season', entry.season.toString());
    if (entry.episode !== undefined && entry.episode !== null) params.append('episode', entry.episode.toString());
    if (entry.episodeData) params.append('episodeData', entry.episodeData);

    await this.fetchJson<void>(`${this.baseUrl}/api/history/item?${params.toString()}`, {
      method: 'DELETE',
    });
  }

  async clearHistory(): Promise<void> {
    await this.fetchJson<void>(`${this.baseUrl}/api/history`, { method: 'DELETE' });
  }

  async getBookmarks(): Promise<BookmarksResponse> {
    return this.fetchJson<BookmarksResponse>(`${this.baseUrl}/api/bookmarks`);
  }

  async putBookmark(bookmark: BookmarkDto): Promise<void> {
    await this.fetchJson<void>(`${this.baseUrl}/api/bookmarks`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(bookmark),
    });
  }

  async deleteBookmark(provider: string, url: string): Promise<void> {
    const params = new URLSearchParams({ provider, url });
    await this.fetchJson<void>(`${this.baseUrl}/api/bookmarks?${params.toString()}`, {
      method: 'DELETE',
    });
  }

  async getRepositories(): Promise<RepositoryDto[]> {
    return this.fetchJson<RepositoryDto[]>(`${this.baseUrl}/api/plugins/repositories`);
  }

  async addRepository(repo: RepositoryDto): Promise<ActionResult> {
    return this.fetchJson<ActionResult>(`${this.baseUrl}/api/plugins/repositories`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(repo),
    });
  }

  async removeRepository(url: string): Promise<ActionResult> {
    const params = new URLSearchParams({ url });
    return this.fetchJson<ActionResult>(`${this.baseUrl}/api/plugins/repositories?${params.toString()}`, {
      method: 'DELETE',
    });
  }

  async getPluginCatalog(repoUrl: string): Promise<PluginCatalogResponse> {
    const params = new URLSearchParams({ repo: repoUrl });
    return this.fetchJson<PluginCatalogResponse>(`${this.baseUrl}/api/plugins/catalog?${params.toString()}`);
  }

  async getInstalledPlugins(): Promise<InstalledPluginsResponse> {
    return this.fetchJson<InstalledPluginsResponse>(`${this.baseUrl}/api/plugins/installed`);
  }

  async installPlugin(repositoryUrl: string, internalName: string): Promise<ActionResult> {
    return this.fetchJson<ActionResult>(`${this.baseUrl}/api/plugins/install`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ repositoryUrl, internalName }),
    });
  }

  async uninstallPlugin(internalName: string): Promise<ActionResult> {
    return this.fetchJson<ActionResult>(`${this.baseUrl}/api/plugins/uninstall`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ internalName }),
    });
  }

  streamLinks(
    provider: string,
    data: string,
    onEvent: (event: LinkEventDto) => void,
    onError?: (err: Event) => void
  ): () => void {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const wsUrl = `${protocol}//${host}/api/links?provider=${encodeURIComponent(provider)}&data=${encodeURIComponent(data)}`;

    const ws = new WebSocket(wsUrl);

    ws.onmessage = (event) => {
      try {
        const payload: LinkEventDto = JSON.parse(event.data);
        onEvent(payload);
      } catch (e) {
        console.error('Failed to parse WebSocket message:', e);
      }
    };

    if (onError) ws.onerror = onError;

    return () => {
      if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
        ws.close();
      }
    };
  }
}

export const api = new ApiClient();
export default api;
