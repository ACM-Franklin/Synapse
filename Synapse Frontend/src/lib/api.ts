export type Currency = 'primary' | 'secondary';

export interface LoginResponse {
  authorizeUrl: string;
}

export interface CurrentUserDto {
  userId: string;
  username: string;
  globalName: string | null;
  avatarHash: string | null;
  isMember: boolean;
  isAdmin: boolean;
}

export interface GuildSummaryDto {
  guildId: string;
  guildName: string;
  memberCount: number;
  activeChannelCount: number;
  activeRoleCount: number;
}

export interface RewardTraceDto {
  ruleName: string;
  currencyType: string;
  amount: number;
  transactionType: string;
  subjectType: string;
  subjectExtId: string;
  createdAt: string;
}

export interface MemberDashboardDto {
  userId: string;
  displayName: string;
  avatarHash: string | null;
  primaryCurrency: number;
  secondaryCurrency: number;
  level: number;
  leaderboardRank: number;
  messagesSent: number;
  reactionsSent: number;
  voiceMinutes: number;
  pending: boolean;
  recentRewards: RewardTraceDto[];
}

export interface LeaderboardDto {
  currencyType: string;
  limit: number;
  entries: LeaderboardEntryDto[];
}

export interface LeaderboardEntryDto {
  rank: number;
  userId: string;
  displayName: string;
  avatarHash: string | null;
  amount: number;
  level: number;
}

export interface SystemStatusDto {
  guildId: string;
  guildName: string;
  oauthConfigured: boolean;
  discordConnected: boolean;
  gatewayPingMs: number;
  activeSessions: number;
  sessionTtlSeconds: number;
  adminRoleCount: number;
  memberCount: number;
  activeChannelCount: number;
  rulesEnabled: number;
  rulesInvalid: number;
  latestHistoricalScanJobId: number | null;
  latestHistoricalScanStatus: string | null;
}

export interface RuleDto {
  id: number;
  name: string;
  description: string | null;
  eventType: string;
  enabled: boolean;
  appliesLive: boolean;
  appliesHistoric: boolean;
  cooldownSeconds: number;
  valid: boolean;
  invalidReasons: string[];
  predicates: PredicateDto[];
  outcomes: OutcomeDto[];
  createdAt: string;
  updatedAt: string;
}

export interface PredicateDto {
  id: number;
  predicateType: string;
  parameters: string | null;
  sortOrder: number;
}

export interface OutcomeDto {
  id: number;
  type: string;
  pCurrency: number | null;
  sCurrency: number | null;
  parameters: string | null;
}

export interface HistoricalScanDto {
  jobId: number;
  guildId: string;
  status: string;
  startedAt: string;
  completedAt: string | null;
  checkpointCount: number;
  errorMessage: string | null;
}

export interface ReplayJobDto {
  jobId: number;
  status: string;
  batchSize: number;
  batchesProcessed: number;
  scannedCount: number;
  replayedCount: number;
  failedCount: number;
  lastMessageId: number;
  startedAt: string;
  completedAt: string | null;
  errorMessage: string | null;
}

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    readonly body: string
  ) {
    super(body || `Request failed with status ${status}`);
  }
}

const API_BASE = (import.meta.env.VITE_SYNAPSE_API_BASE ?? '').replace(/\/$/, '');
const HISTORICAL_CONFIRM = 'I_UNDERSTAND_THIS_SCANS_DISCORD';
const REPLAY_CONFIRM = 'I_UNDERSTAND_THIS_REPLAYS_REWARDS';

function apiPath(path: string): string {
  return `${API_BASE}${path}`;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);

  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(apiPath(path), {
    ...init,
    credentials: 'include',
    headers
  });

  if (!response.ok) {
    const body = await response.text();
    throw new ApiRequestError(response.status, body);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

function jsonBody(value: unknown): string {
  return JSON.stringify(value);
}

export function isApiRequestError(error: unknown): error is ApiRequestError {
  return error instanceof ApiRequestError;
}

export const api = {
  login: () => request<LoginResponse>('/api/auth/login'),
  me: () => request<CurrentUserDto>('/api/auth/me'),
  logout: () => request<void>('/api/auth/logout', { method: 'POST' }),
  guildSummary: () => request<GuildSummaryDto>('/api/guild/summary'),
  memberDashboard: () => request<MemberDashboardDto>('/api/members/me/dashboard'),
  leaderboard: (currency: Currency, limit = 25) =>
    request<LeaderboardDto>(`/api/leaderboard?currency=${currency}&limit=${limit}`),
  systemStatus: () => request<SystemStatusDto>('/api/system/status'),
  rules: () => request<RuleDto[]>('/api/rules'),
  startHistoricalScan: () =>
    request<HistoricalScanDto>('/api/scans/historical', {
      method: 'POST',
      body: jsonBody({ confirm: HISTORICAL_CONFIRM })
    }),
  historicalScan: (jobId: number) => request<HistoricalScanDto>(`/api/scans/historical/${jobId}`),
  startReplay: (batchSize: number) =>
    request<ReplayJobDto>('/api/admin/replay/messages', {
      method: 'POST',
      body: jsonBody({ confirm: REPLAY_CONFIRM, batchSize })
    }),
  replay: (jobId: number) => request<ReplayJobDto>(`/api/admin/replay/messages/${jobId}`)
};