<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import {
    api,
    isApiRequestError,
    type Currency,
    type CurrentUserDto,
    type GuildSummaryDto,
    type HistoricalScanDto,
    type LeaderboardDto,
    type MemberDashboardDto,
    type ReplayJobDto,
    type RuleDto,
    type SystemStatusDto
  } from './lib/api';

  type AuthState = 'loading' | 'guest' | 'authenticated' | 'denied' | 'error';
  type View = 'dashboard' | 'leaderboard' | 'admin' | 'rules' | 'operations';

  interface NavItem {
    id: View;
    label: string;
    adminOnly?: boolean;
  }

  const navItems: NavItem[] = [
    { id: 'dashboard', label: 'My Dashboard' },
    { id: 'leaderboard', label: 'Leaderboard' },
    { id: 'admin', label: 'Admin Status', adminOnly: true },
    { id: 'rules', label: 'Rules', adminOnly: true },
    { id: 'operations', label: 'Scan And Replay', adminOnly: true }
  ];

  const loginAssetStyles = [
    '--login-logo-image: url("/images/synapse-logo.png")'
  ].join('; ');

  let authState: AuthState = 'loading';
  let currentUser: CurrentUserDto | null = null;
  let view: View = 'dashboard';
  let authError = '';
  let dataError = '';
  let operationError = '';
  let loginBusy = false;
  let memberLoading = false;
  let adminLoading = false;
  let leaderboardLoading = false;
  let operationBusy = false;

  let guildSummary: GuildSummaryDto | null = null;
  let dashboard: MemberDashboardDto | null = null;
  let leaderboard: LeaderboardDto | null = null;
  let systemStatus: SystemStatusDto | null = null;
  let rules: RuleDto[] = [];
  let selectedRuleId: number | null = null;
  let leaderboardCurrency: Currency = 'primary';
  let historicalConfirmed = false;
  let replayConfirmed = false;
  let replayBatchSize = 200;
  let historicalJob: HistoricalScanDto | null = null;
  let replayJob: ReplayJobDto | null = null;
  let historicalTimer: number | undefined;
  let replayTimer: number | undefined;
  let autoRefreshTimer: number | undefined;

  $: selectedRule = rules.find((rule) => rule.id === selectedRuleId) ?? rules[0] ?? null;
  $: currentViewTitle = navItems.find((item) => item.id === view)?.label ?? 'Synapse';

  onMount(() => {
    void refreshSession();
  });

  onDestroy(() => {
    clearRuntimeTimers();
  });

  async function refreshSession(): Promise<void> {
    authState = 'loading';
    authError = '';

    try {
      currentUser = await api.me();

      if (!currentUser.isMember) {
        authState = 'denied';
        return;
      }

      authState = 'authenticated';
      startAutoRefresh();
      await loadAppData();
    } catch (error) {
      currentUser = null;
      if (isApiRequestError(error) && error.status === 401) {
        authState = 'guest';
        return;
      }
      if (isApiRequestError(error) && error.status === 403) {
        authState = 'denied';
        return;
      }
      authState = 'error';
      authError = explainError(error);
    }
  }

  async function startLogin(): Promise<void> {
    loginBusy = true;
    authError = '';

    try {
      const response = await api.login();
      window.location.assign(response.authorizeUrl);
    } catch (error) {
      authError = explainError(error);
    } finally {
      loginBusy = false;
    }
  }

  async function logout(): Promise<void> {
    try {
      await api.logout();
    } finally {
      clearRuntimeTimers();
      currentUser = null;
      guildSummary = null;
      dashboard = null;
      leaderboard = null;
      systemStatus = null;
      rules = [];
      authState = 'guest';
      view = 'dashboard';
    }
  }

  async function loadAppData(): Promise<void> {
    await loadMemberData();
    if (currentUser?.isAdmin) {
      await loadAdminData();
    }
  }

  async function loadMemberData(): Promise<void> {
    memberLoading = true;
    dataError = '';

    try {
      const [summaryResponse, dashboardResponse, leaderboardResponse] = await Promise.all([
        api.guildSummary(),
        api.memberDashboard(),
        api.leaderboard(leaderboardCurrency)
      ]);
      guildSummary = summaryResponse;
      dashboard = dashboardResponse;
      leaderboard = leaderboardResponse;
    } catch (error) {
      handleDataError(error);
    } finally {
      memberLoading = false;
    }
  }

  async function loadLeaderboard(currency: Currency): Promise<void> {
    leaderboardLoading = true;
    dataError = '';
    leaderboardCurrency = currency;

    try {
      leaderboard = await api.leaderboard(currency);
    } catch (error) {
      handleDataError(error);
    } finally {
      leaderboardLoading = false;
    }
  }

  async function loadAdminData(): Promise<void> {
    if (!currentUser?.isAdmin) {
      return;
    }

    adminLoading = true;
    dataError = '';

    try {
      const [statusResponse, ruleResponse] = await Promise.all([api.systemStatus(), api.rules()]);
      systemStatus = statusResponse;
      rules = ruleResponse;
      selectedRuleId = ruleResponse[0]?.id ?? null;

      if (statusResponse.latestHistoricalScanJobId) {
        historicalJob = await api.historicalScan(statusResponse.latestHistoricalScanJobId);
      }
    } catch (error) {
      handleDataError(error);
    } finally {
      adminLoading = false;
    }
  }

  async function refreshCurrentView(): Promise<void> {
    if (view === 'leaderboard') {
      await loadLeaderboard(leaderboardCurrency);
      return;
    }
    if (view === 'admin' || view === 'rules' || view === 'operations') {
      await loadAdminData();
      return;
    }
    await loadMemberData();
  }

  async function selectView(nextView: View): Promise<void> {
    const target = navItems.find((item) => item.id === nextView);
    if (target?.adminOnly && !currentUser?.isAdmin) {
      return;
    }

    view = nextView;

    if (nextView === 'leaderboard' && !leaderboard) {
      await loadLeaderboard(leaderboardCurrency);
    }

    if ((nextView === 'admin' || nextView === 'rules' || nextView === 'operations') && !systemStatus) {
      await loadAdminData();
    }
  }

  async function startHistoricalScan(): Promise<void> {
    if (!historicalConfirmed) {
      return;
    }

    operationBusy = true;
    operationError = '';

    try {
      historicalJob = await api.startHistoricalScan();
      if (historicalJob.status === 'RUNNING') {
        pollHistoricalScan(historicalJob.jobId);
      }
      await loadAdminData();
    } catch (error) {
      operationError = explainError(error);
    } finally {
      operationBusy = false;
    }
  }

  async function startReplay(): Promise<void> {
    if (!replayConfirmed) {
      return;
    }

    operationBusy = true;
    operationError = '';

    try {
      replayJob = await api.startReplay(normalizedBatchSize());
      if (replayJob.status === 'RUNNING') {
        pollReplay(replayJob.jobId);
      }
    } catch (error) {
      operationError = explainError(error);
    } finally {
      operationBusy = false;
    }
  }

  function pollHistoricalScan(jobId: number): void {
    window.clearTimeout(historicalTimer);
    historicalTimer = window.setTimeout(async () => {
      try {
        historicalJob = await api.historicalScan(jobId);
        if (historicalJob.status === 'RUNNING') {
          pollHistoricalScan(jobId);
        } else {
          await loadAdminData();
        }
      } catch (error) {
        operationError = explainError(error);
      }
    }, 1500);
  }

  function pollReplay(jobId: number): void {
    window.clearTimeout(replayTimer);
    replayTimer = window.setTimeout(async () => {
      try {
        replayJob = await api.replay(jobId);
        if (replayJob.status === 'RUNNING') {
          pollReplay(jobId);
        } else {
          await loadMemberData();
        }
      } catch (error) {
        operationError = explainError(error);
      }
    }, 1500);
  }

  function startAutoRefresh(): void {
    window.clearInterval(autoRefreshTimer);
    autoRefreshTimer = window.setInterval(() => {
      if (document.hidden || authState !== 'authenticated') {
        return;
      }

      void refreshCurrentView();
    }, 60000);
  }

  function clearRuntimeTimers(): void {
    window.clearTimeout(historicalTimer);
    window.clearTimeout(replayTimer);
    window.clearInterval(autoRefreshTimer);
  }

  function normalizedBatchSize(): number {
    return Math.min(1000, Math.max(1, Math.round(Number(replayBatchSize) || 200)));
  }

  function handleDataError(error: unknown): void {
    if (isApiRequestError(error) && error.status === 401) {
      authState = 'guest';
      currentUser = null;
      return;
    }
    if (isApiRequestError(error) && error.status === 403) {
      dataError = 'Access denied by the backend.';
      return;
    }
    dataError = explainError(error);
  }

  function explainError(error: unknown): string {
    if (!isApiRequestError(error)) {
      return error instanceof Error ? error.message : 'Request failed.';
    }

    if (error.status === 400) return error.body || 'The backend rejected the request.';
    if (error.status === 401) return 'Session expired. Sign in again.';
    if (error.status === 403) return 'This Discord account cannot access that surface.';
    if (error.status === 404) return 'The requested resource no longer exists.';
    if (error.status === 409) return 'An operation is already running.';
    if (error.status === 429) return 'Rate limited. Wait briefly and retry.';
    if (error.status === 503) return error.body || 'Synapse is not fully configured or the bot runtime is unavailable.';
    return error.body || `Request failed with status ${error.status}.`;
  }

  function visibleNavItems(): NavItem[] {
    return navItems.filter((item) => !item.adminOnly || currentUser?.isAdmin);
  }

  function displayName(): string {
    return currentUser?.globalName || currentUser?.username || 'Member';
  }

  function accountUsername(): string {
    const username = currentUser?.username?.trim();
    if (!username) return 'Member';
    return username.startsWith('@') ? username : `@${username}`;
  }

  function currentUserAvatarUrl(): string | null {
    if (!currentUser?.avatarHash) {
      return null;
    }

    const extension = currentUser.avatarHash.startsWith('a_') ? 'gif' : 'png';
    return `https://cdn.discordapp.com/avatars/${currentUser.userId}/${currentUser.avatarHash}.${extension}?size=128`;
  }

  function initials(name: string | null | undefined): string {
    const trimmed = name?.trim();
    return trimmed ? trimmed.slice(0, 2).toUpperCase() : 'SN';
  }

  function formatNumber(value: number | null | undefined): string {
    return new Intl.NumberFormat().format(value ?? 0);
  }

  function formatSeconds(value: number | null | undefined): string {
    if (!value) return '0m';
    const hours = Math.floor(value / 3600);
    const minutes = Math.floor((value % 3600) / 60);
    return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
  }

  function activeRuleCount(): number {
    return rules.filter((rule) => rule.enabled).length;
  }

  function invalidRuleCount(): number {
    return rules.filter((rule) => !rule.valid).length;
  }

  function statusClass(status: string | null | undefined): string {
    if (!status) return 'neutral';
    return status.toLowerCase();
  }

</script>

{#if authState === 'loading'}
  <main class="center-screen" style={loginAssetStyles}>
    <section class="login-panel loading-panel">
      <div class="brand-lockup loading-brand">
        <span class="brand-logo small"></span>
        <div>
          <strong>Synapse</strong>
          <span>Opening your workspace</span>
        </div>
      </div>
      <div class="loader"></div>
    </section>
  </main>
{:else if authState === 'guest' || authState === 'denied' || authState === 'error'}
  <main class="login-screen" style={loginAssetStyles}>
    <section class="login-stack">
      <div class="login-hero">
        <div class="login-brand">
          <span class="brand-logo" aria-hidden="true"></span>
        </div>
        <h1>Discord activity, reward history, and operational proof in one place.</h1>
      </div>

      <section class="login-panel auth-panel">
        <div class="login-card-copy">
          <h2>Sign in with Discord</h2>
          <p class="muted">Use your server account to open Synapse.</p>
        </div>

        {#if authState === 'denied'}
          <div class="notice error">This Discord account authenticated successfully, but it does not have access to this guild.</div>
        {/if}

        {#if authError}
          <div class="notice error">{authError}</div>
        {/if}

        <div class="login-actions">
          <button class="primary-button" type="button" onclick={() => void startLogin()} disabled={loginBusy}>
            {loginBusy ? 'Redirecting to Discord...' : 'Continue with Discord'}
          </button>
          <button class="ghost-button" type="button" onclick={() => void refreshSession()}>I already signed in</button>
        </div>
      </section>
    </section>
  </main>
{:else if currentUser}
  <div class="app-shell" style={loginAssetStyles}>
    <aside class="sidebar">
      <div class="sidebar-brand">
        <span class="brand-logo sidebar-logo" aria-hidden="true"></span>
        <span class="sidebar-guild-name" title={guildSummary?.guildName ?? 'managed guild'}>{guildSummary?.guildName ?? 'managed guild'}</span>
      </div>

      <nav class="nav-list" aria-label="Main navigation">
        {#each visibleNavItems() as item (item.id)}
          <button class:active={view === item.id} type="button" onclick={() => void selectView(item.id)}>
            {item.label}
          </button>
        {/each}
      </nav>

      <div class="sidebar-footer">
        <div class="sidebar-account">
          {#if currentUserAvatarUrl()}
            <img class="avatar avatar-image" src={currentUserAvatarUrl() ?? undefined} alt={`${accountUsername()} avatar`} />
          {:else}
            <div class="avatar">{initials(displayName())}</div>
          {/if}
          <div class="sidebar-account-meta">
            <strong title={accountUsername()}>{accountUsername()}</strong>
            <span>{currentUser.isAdmin ? 'Admin' : 'Member'}</span>
          </div>
        </div>
        <button class="ghost-button sidebar-logout" type="button" onclick={() => void logout()}>Log out</button>
      </div>
    </aside>

    <main class="content">
      <header class="topbar">
        <h1>{currentViewTitle}</h1>
      </header>

      {#if dataError}
        <div class="notice error">{dataError}</div>
      {/if}

      {#if view === 'dashboard'}
        <section class="view-stack">
          {#if memberLoading && !dashboard}
            <div class="skeleton-grid"><span></span><span></span><span></span></div>
          {:else if dashboard}
            {#if dashboard.pending}
              <div class="notice warning">Your Discord account is authenticated, but local member state is still syncing.</div>
            {/if}

            <section class="stat-grid">
              <article class="stat-card accent">
                <span>Primary</span>
                <strong>{formatNumber(dashboard.primaryCurrency)}</strong>
              </article>
              <article class="stat-card teal">
                <span>Secondary</span>
                <strong>{formatNumber(dashboard.secondaryCurrency)}</strong>
              </article>
              <article class="stat-card gold">
                <span>Level</span>
                <strong>{formatNumber(dashboard.level)}</strong>
              </article>
              <article class="stat-card green">
                <span>Rank</span>
                <strong>{dashboard.leaderboardRank ? `#${dashboard.leaderboardRank}` : 'Pending'}</strong>
              </article>
            </section>

            <section class="split-layout">
              <article class="panel">
                <div class="section-head">
                  <div>
                    <p class="eyebrow">Activity</p>
                    <h2>{dashboard.displayName}</h2>
                  </div>
                </div>
                <div class="metric-list">
                  <div><span>Messages</span><strong>{formatNumber(dashboard.messagesSent)}</strong></div>
                  <div><span>Reactions</span><strong>{formatNumber(dashboard.reactionsSent)}</strong></div>
                  <div><span>Voice minutes</span><strong>{formatNumber(dashboard.voiceMinutes)}</strong></div>
                </div>
              </article>

              <article class="panel">
                <div class="section-head">
                  <div>
                    <p class="eyebrow">Guild</p>
                    <h2>{guildSummary?.guildName ?? 'Guild summary'}</h2>
                  </div>
                </div>
                <div class="metric-list">
                  <div><span>Members</span><strong>{formatNumber(guildSummary?.memberCount)}</strong></div>
                  <div><span>Channels</span><strong>{formatNumber(guildSummary?.activeChannelCount)}</strong></div>
                  <div><span>Roles</span><strong>{formatNumber(guildSummary?.activeRoleCount)}</strong></div>
                </div>
              </article>
            </section>

            <section class="panel">
              <div class="section-head">
                <div>
                  <p class="eyebrow">Reward ledger</p>
                  <h2>Recent rewards</h2>
                </div>
              </div>

              {#if dashboard.recentRewards.length > 0}
                <div class="table-wrap">
                  <table>
                    <thead>
                      <tr><th>Rule</th><th>Type</th><th>Amount</th><th>Subject</th><th>Created</th></tr>
                    </thead>
                    <tbody>
                      {#each dashboard.recentRewards as reward}
                        <tr>
                          <td>{reward.ruleName}</td>
                          <td><span class="pill">{reward.currencyType} / {reward.transactionType}</span></td>
                          <td class:negative={reward.amount < 0}>{formatNumber(reward.amount)}</td>
                          <td><code>{reward.subjectType}:{reward.subjectExtId}</code></td>
                          <td>{reward.createdAt}</td>
                        </tr>
                      {/each}
                    </tbody>
                  </table>
                </div>
              {:else}
                <div class="empty-state">No reward rows yet.</div>
              {/if}
            </section>
          {/if}
        </section>
      {:else if view === 'leaderboard'}
        <section class="view-stack">
          <div class="toolbar">
            <div>
              <p class="eyebrow">Authenticated ranking</p>
              <h2>{leaderboard?.currencyType ?? leaderboardCurrency} leaderboard</h2>
            </div>
            <div class="segmented">
              <button class:active={leaderboardCurrency === 'primary'} type="button" onclick={() => void loadLeaderboard('primary')}>Primary</button>
              <button class:active={leaderboardCurrency === 'secondary'} type="button" onclick={() => void loadLeaderboard('secondary')}>Secondary</button>
            </div>
          </div>

          <section class="panel">
            {#if leaderboardLoading && !leaderboard}
              <div class="skeleton-list"><span></span><span></span><span></span></div>
            {:else if leaderboard && leaderboard.entries.length > 0}
              <div class="leaderboard-list">
                {#each leaderboard.entries as entry}
                  <div class:current={entry.userId === currentUser.userId} class="leaderboard-row">
                    <span class="rank">#{entry.rank}</span>
                    <span class="avatar">{initials(entry.displayName)}</span>
                    <span class="member-name"><strong>{entry.displayName}</strong><small>Level {entry.level}</small></span>
                    <strong>{formatNumber(entry.amount)}</strong>
                  </div>
                {/each}
              </div>
            {:else}
              <div class="empty-state">No leaderboard rows yet.</div>
            {/if}
          </section>
        </section>
      {:else if view === 'admin'}
        <section class="view-stack">
          {#if adminLoading && !systemStatus}
            <div class="skeleton-grid"><span></span><span></span><span></span></div>
          {:else if systemStatus}
            <section class="stat-grid admin-grid">
              <article class="stat-card {systemStatus.oauthConfigured ? 'green' : 'red'}"><span>OAuth</span><strong>{systemStatus.oauthConfigured ? 'Ready' : 'Missing'}</strong></article>
              <article class="stat-card {systemStatus.discordConnected ? 'green' : 'red'}"><span>Discord</span><strong>{systemStatus.discordConnected ? 'Connected' : 'Offline'}</strong></article>
              <article class="stat-card teal"><span>Gateway ping</span><strong>{formatNumber(systemStatus.gatewayPingMs)} ms</strong></article>
              <article class="stat-card gold"><span>Session TTL</span><strong>{formatSeconds(systemStatus.sessionTtlSeconds)}</strong></article>
            </section>

            <section class="split-layout">
              <article class="panel">
                <div class="section-head"><div><p class="eyebrow">Runtime</p><h2>{systemStatus.guildName}</h2></div></div>
                <div class="metric-list">
                  <div><span>Active sessions</span><strong>{formatNumber(systemStatus.activeSessions)}</strong></div>
                  <div><span>Admin roles</span><strong>{formatNumber(systemStatus.adminRoleCount)}</strong></div>
                  <div><span>Members</span><strong>{formatNumber(systemStatus.memberCount)}</strong></div>
                  <div><span>Channels</span><strong>{formatNumber(systemStatus.activeChannelCount)}</strong></div>
                </div>
              </article>

              <article class="panel">
                <div class="section-head"><div><p class="eyebrow">Rules</p><h2>Evaluation set</h2></div></div>
                <div class="metric-list">
                  <div><span>Enabled</span><strong>{formatNumber(systemStatus.rulesEnabled)}</strong></div>
                  <div><span>Invalid</span><strong>{formatNumber(systemStatus.rulesInvalid)}</strong></div>
                  <div><span>Loaded here</span><strong>{formatNumber(rules.length)}</strong></div>
                  <div><span>Invalid loaded</span><strong>{formatNumber(invalidRuleCount())}</strong></div>
                </div>
              </article>
            </section>
          {/if}
        </section>
      {:else if view === 'rules'}
        <section class="rules-layout">
          <aside class="panel rule-list">
            <div class="section-head"><div><p class="eyebrow">Read-only</p><h2>Rules</h2></div><span class="pill">{activeRuleCount()} enabled</span></div>
            {#if rules.length > 0}
              {#each rules as rule}
                <button class:active={selectedRule?.id === rule.id} type="button" onclick={() => (selectedRuleId = rule.id)}>
                  <strong>{rule.name}</strong>
                  <span>{rule.eventType}</span>
                  <em class={rule.valid ? 'valid' : 'invalid'}>{rule.valid ? 'valid' : 'invalid'}</em>
                </button>
              {/each}
            {:else}
              <div class="empty-state">No rules returned.</div>
            {/if}
          </aside>

          <section class="panel rule-detail">
            {#if selectedRule}
              <div class="section-head">
                <div>
                  <p class="eyebrow">{selectedRule.eventType}</p>
                  <h2>{selectedRule.name}</h2>
                </div>
                <span class="pill {selectedRule.enabled ? 'success' : ''}">{selectedRule.enabled ? 'enabled' : 'disabled'}</span>
              </div>

              {#if selectedRule.description}
                <p class="description">{selectedRule.description}</p>
              {/if}

              {#if !selectedRule.valid}
                <div class="notice error">{selectedRule.invalidReasons.join(' ') || 'This rule is invalid.'}</div>
              {/if}

              <div class="rule-meta">
                <div><span>Live</span><strong>{selectedRule.appliesLive ? 'Yes' : 'No'}</strong></div>
                <div><span>Historic</span><strong>{selectedRule.appliesHistoric ? 'Yes' : 'No'}</strong></div>
                <div><span>Cooldown</span><strong>{formatNumber(selectedRule.cooldownSeconds)}s</strong></div>
              </div>

              <div class="rule-columns">
                <div>
                  <h3>Predicates</h3>
                  {#if selectedRule.predicates.length > 0}
                    {#each selectedRule.predicates as predicate}
                      <div class="rule-chip"><strong>{predicate.predicateType}</strong><code>{predicate.parameters ?? '{}'}</code></div>
                    {/each}
                  {:else}
                    <div class="empty-state compact">No predicates.</div>
                  {/if}
                </div>

                <div>
                  <h3>Outcomes</h3>
                  {#each selectedRule.outcomes as outcome}
                    <div class="rule-chip"><strong>{outcome.type}</strong><code>P {outcome.pCurrency ?? 0} / S {outcome.sCurrency ?? 0}</code></div>
                  {/each}
                </div>
              </div>
            {:else}
              <div class="empty-state">Select a rule.</div>
            {/if}
          </section>
        </section>
      {:else if view === 'operations'}
        <section class="view-stack">
          {#if operationError}
            <div class="notice error">{operationError}</div>
          {/if}

          <section class="operation-grid">
            <article class="action-panel">
              <div>
                <p class="eyebrow">Historical scan</p>
                <h2>Scan Discord history</h2>
                <p class="muted">Starts the backend historical scan job with the required confirmation token.</p>
              </div>
              <label class="check-row"><input type="checkbox" bind:checked={historicalConfirmed} /> I understand this scans Discord.</label>
              <button class="primary-button" type="button" disabled={!historicalConfirmed || operationBusy} onclick={() => void startHistoricalScan()}>
                Start historical scan
              </button>

              {#if historicalJob}
                <div class="job-card">
                  <span class="status {statusClass(historicalJob.status)}">{historicalJob.status}</span>
                  <div><span>Job</span><strong>#{historicalJob.jobId}</strong></div>
                  <div><span>Checkpoints</span><strong>{formatNumber(historicalJob.checkpointCount)}</strong></div>
                  <div><span>Started</span><strong>{historicalJob.startedAt}</strong></div>
                  {#if historicalJob.errorMessage}<div class="notice error">{historicalJob.errorMessage}</div>{/if}
                </div>
              {/if}
            </article>

            <article class="action-panel">
              <div>
                <p class="eyebrow">Reward replay</p>
                <h2>Replay message rewards</h2>
                <p class="muted">Runs the persisted async replay job and polls the status endpoint.</p>
              </div>
              <label class="input-row"><span>Batch size</span><input type="number" min="1" max="1000" bind:value={replayBatchSize} /></label>
              <label class="check-row"><input type="checkbox" bind:checked={replayConfirmed} /> I understand this replays rewards.</label>
              <button class="primary-button" type="button" disabled={!replayConfirmed || operationBusy} onclick={() => void startReplay()}>
                Start reward replay
              </button>

              {#if replayJob}
                <div class="job-card">
                  <span class="status {statusClass(replayJob.status)}">{replayJob.status}</span>
                  <div><span>Job</span><strong>#{replayJob.jobId}</strong></div>
                  <div><span>Scanned</span><strong>{formatNumber(replayJob.scannedCount)}</strong></div>
                  <div><span>Replayed</span><strong>{formatNumber(replayJob.replayedCount)}</strong></div>
                  <div><span>Failed</span><strong>{formatNumber(replayJob.failedCount)}</strong></div>
                  {#if replayJob.errorMessage}<div class="notice error">{replayJob.errorMessage}</div>{/if}
                </div>
              {/if}
            </article>
          </section>
        </section>
      {/if}
    </main>
  </div>
{/if}