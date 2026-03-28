export interface PublicUiDefaults {
  rateLimit: number;
  windowSeconds: number;
  algorithm: string;
}

export interface PublicConfig {
  grafanaDashboardUrl: string;
  refreshIntervalMs: number;
  allowedAlgorithms: string[];
  defaults: PublicUiDefaults;
}

export interface DashboardStatCard {
  title: string;
  value: number;
  valueLabel: string;
  caption: string;
  changeLabel: string;
  trend: "up" | "down";
  color: string;
  iconKey: "activity" | "check-circle" | "x-circle" | string;
}

export interface DashboardStats {
  totalRequests: number;
  totalRequestsLabel: string;
  allowedRequests: number;
  allowedRequestsLabel: string;
  blockedRequests: number;
  blockedRequestsLabel: string;
  totalPercent: number;
  allowedPercent: number;
  blockedPercent: number;
  cards: DashboardStatCard[];
}

export interface DashboardApiKeyRow {
  id: number;
  apiKey: string;
  userName: string;
  rateLimit: number;
  windowSeconds: number;
  algorithm: string;
  requestCount: number;
  usagePercentage: number;
  requestCountLabel: string;
  rateLimitLabel: string;
  windowLabel: string;
  algorithmLabel: string;
  usageLabel: string;
  usageColor: string;
  status: string;
  statusLabel: string;
  statusColor: string;
}

export interface DashboardPagination {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  filtered: boolean;
  search: string;
}

export interface DashboardSources {
  postgres: string;
  redis: string;
}

export interface DashboardResponse {
  stats: DashboardStats;
  apiKeys: DashboardApiKeyRow[];
  pagination: DashboardPagination;
  sources: DashboardSources;
  generatedAt: string;
}

export interface ApiErrorResponse {
  message: string;
}

export interface CreateApiKeyPayload {
  userName: string;
  rateLimit: number;
  windowSeconds: number;
}

export interface CreatedApiKeyResponse {
  apiKey: string;
  userName: string;
}

export interface ToastItem {
  id: string;
  title: string;
  message: string;
}

export interface DashboardAlertEvent {
  id: string;
  title?: string;
  message?: string;
}

export interface LoginResponse {
  message?: string;
  userId?: string;
  fullName?: string;
  email?: string;
  createdAt?: string;
  initials?: string;
}

export interface TableQuery {
  search: string;
  page: number;
  size: number;
}
