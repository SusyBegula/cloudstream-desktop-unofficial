export interface SearchResultDto {
  name: string;
  url: string;
  apiName: string;
  type?: string | null;
  posterUrl?: string | null;
  year?: number | null;
  episodes?: number | null;
  quality?: string | null;
}

export interface SearchResponseDto {
  provider: string;
  results: SearchResultDto[];
}

export interface AggregatedSearchResponse {
  query: string;
  perProvider: SearchResponseDto[];
}

export interface EpisodeDto {
  data: string;
  name?: string | null;
  season?: number | null;
  episode?: number | null;
  posterUrl?: string | null;
  description?: string | null;
  date?: number | null;
  runTime?: number | null;
  dubStatus?: string | null;
}

export interface LoadResponseDto {
  kind: string;
  name: string;
  url: string;
  apiName: string;
  type: string;
  dataUrl?: string | null;
  posterUrl?: string | null;
  backgroundPosterUrl?: string | null;
  year?: number | null;
  plot?: string | null;
  tags?: string[] | null;
  duration?: number | null;
  showStatus?: string | null;
  contentRating?: string | null;
  episodes?: EpisodeDto[] | null;
  recommendations?: SearchResultDto[] | null;
}

export interface ProviderDto {
  name: string;
  mainUrl: string;
  lang: string;
  hasMainPage: boolean;
  hasQuickSearch: boolean;
  providerType: string;
  supportedTypes: string[];
}

export interface ProviderListResponse {
  providers: ProviderDto[];
}

export interface MainPageRowDto {
  name: string;
  items: SearchResultDto[];
  isHorizontalImages?: boolean;
}

export interface HomePageResponseDto {
  rows: MainPageRowDto[];
  hasNext: boolean;
}

export interface MainPageCategoryDto {
  index: number;
  name: string;
  isHorizontalImages?: boolean;
}

export interface MainPageCategoriesResponse {
  categories: MainPageCategoryDto[];
}

export interface SubtitleFileDto {
  lang: string;
  url: string;
}

export interface AudioFileDto {
  url: string;
}

export interface PlayListItemDto {
  url: string;
  durationUs: number;
}

export interface ExtractorLinkDto {
  source: string;
  name: string;
  url: string;
  referer: string;
  quality: number;
  type: string;
  headers?: Record<string, string>;
  audioTracks?: AudioFileDto[];
  playlist?: PlayListItemDto[] | null;
}

export interface LinkEventDto {
  kind: 'link' | 'subtitle' | 'done' | 'error';
  link?: ExtractorLinkDto | null;
  subtitle?: SubtitleFileDto | null;
  message?: string | null;
}

export interface PlayableStreamDto {
  proxyUrl: string;
  mimeType: string;
  kind: string;
  subtitles?: SubtitleFileDto[];
  audioTracks?: AudioFileDto[];
  /** Set only for transcoded streams (growing HLS playlist) — the real total duration, so the
   * player can show a full seek bar immediately instead of just the portion transcoded so far. */
  durationSeconds?: number;
}

export interface WatchHistoryEntryDto {
  provider: string;
  url: string;
  name: string;
  posterUrl?: string | null;
  episodeData?: string | null;
  season?: number | null;
  episode?: number | null;
  positionMs: number;
  durationMs: number;
  updatedAt: number;
}

export interface WatchHistoryResponse {
  entries: WatchHistoryEntryDto[];
}

/** Remembers which stream source (ExtractorLinkDto.source) to prefer for a show, so the player
 * doesn't default back to the first link on every new episode. Falls back automatically when
 * that source isn't present for a given episode (e.g. picked up by client-side matching logic). */
export interface PreferredSourceDto {
  provider: string;
  seriesUrl: string;
  sourceName: string;
}

export interface BookmarkDto {
  provider: string;
  url: string;
  name: string;
  posterUrl?: string | null;
  addedAt: number;
}

export interface BookmarksResponse {
  bookmarks: BookmarkDto[];
}

export interface SitePluginDto {
  internalName: string;
  name: string;
  version: number;
  fileName: string;
  url: string;
  repositoryUrl: string;
  isInstalled: boolean;
}

export interface RepositoryDto {
  name: string;
  url: string;
}

export interface InstalledPluginsResponse {
  plugins: SitePluginDto[];
}

export interface PluginCatalogResponse {
  repository: string;
  plugins: SitePluginDto[];
}

export interface ActionResult {
  success: boolean;
  message?: string | null;
}
