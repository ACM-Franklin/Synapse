package edu.franklin.acm.synapse.api.service;

import java.util.List;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * Read-only aggregate queries that back the frontend-facing API. Lives in the
 * api/service layer to keep frontend DTO concerns out of the core ingestion
 * DAO surface.
 */
public interface StatsQueryDao {

    @SqlQuery("SELECT COUNT(*) FROM members WHERE is_active = 1")
    int countActiveMembers();

    @SqlQuery("SELECT COUNT(*) FROM members WHERE is_active = 1 AND is_bot = 0")
    int countActiveHumanMembers();

    @SqlQuery("SELECT COUNT(*) FROM channels WHERE is_active = 1")
    int countActiveChannels();

    @SqlQuery("SELECT COUNT(*) FROM roles WHERE is_active = 1")
    int countActiveRoles();

    @SqlQuery("SELECT COUNT(*) FROM rules")
    int countAllRules();

    @SqlQuery("SELECT COUNT(*) FROM rules WHERE enabled = 1")
    int countEnabledRules();

    @SqlQuery("""
            SELECT m.id            AS memberId,
                   m.ext_id        AS userExtId,
                   COALESCE(NULLIF(m.nickname, ''),
                            NULLIF(m.global_name, ''),
                            m.name) AS displayName,
                   m.avatar_hash   AS avatarHash,
                   m.p_currency    AS pCurrency,
                   m.s_currency    AS sCurrency,
                   m.level         AS level
            FROM members m
            WHERE m.ext_id = :extId
            """)
    @org.jdbi.v3.sqlobject.config.RegisterConstructorMapper(MemberProfile.class)
    MemberProfile findProfileByExtId(@Bind("extId") long extId);

    @SqlQuery("""
            SELECT COUNT(*) FROM events
            WHERE member_id = :memberId AND event_type = 'MESSAGE_CREATE'
            """)
    int countMessagesByMember(@Bind("memberId") long memberId);

    @SqlQuery("""
            SELECT COUNT(*) FROM events
            WHERE member_id = :memberId AND event_type = 'REACTION_ADD'
            """)
    int countReactionsByMember(@Bind("memberId") long memberId);

    @SqlQuery("""
            SELECT COALESCE(SUM(duration_secs), 0) / 60
            FROM voice_sessions
            WHERE member_id = :memberId AND left_at IS NOT NULL
            """)
    int sumVoiceMinutesByMember(@Bind("memberId") long memberId);

    @SqlQuery("""
            SELECT COUNT(*) + 1 FROM members
            WHERE is_active = 1 AND is_bot = 0 AND p_currency > :amount
            """)
    int rankByPrimaryCurrency(@Bind("amount") int amount);

    @SqlQuery("""
            SELECT m.ext_id        AS userExtId,
                   COALESCE(NULLIF(m.nickname, ''),
                            NULLIF(m.global_name, ''),
                            m.name) AS displayName,
                   m.avatar_hash   AS avatarHash,
                   m.p_currency    AS amount,
                   m.level         AS level
            FROM members m
            WHERE m.is_active = 1 AND m.is_bot = 0
            ORDER BY m.p_currency DESC, m.id ASC
            LIMIT :limit
            """)
    @org.jdbi.v3.sqlobject.config.RegisterConstructorMapper(LeaderboardRow.class)
    List<LeaderboardRow> topByPrimaryCurrency(@Bind("limit") int limit);

    @SqlQuery("""
            SELECT m.ext_id        AS userExtId,
                   COALESCE(NULLIF(m.nickname, ''),
                            NULLIF(m.global_name, ''),
                            m.name) AS displayName,
                   m.avatar_hash   AS avatarHash,
                   m.s_currency    AS amount,
                   m.level         AS level
            FROM members m
            WHERE m.is_active = 1 AND m.is_bot = 0
            ORDER BY m.s_currency DESC, m.id ASC
            LIMIT :limit
            """)
    @org.jdbi.v3.sqlobject.config.RegisterConstructorMapper(LeaderboardRow.class)
    List<LeaderboardRow> topBySecondaryCurrency(@Bind("limit") int limit);

    @SqlQuery("""
            SELECT rl.id              AS id,
                   r.name             AS ruleName,
                   rl.currency_type   AS currencyType,
                   rl.amount          AS amount,
                   rl.transaction_type AS transactionType,
                   rl.subject_type    AS subjectType,
                   rl.subject_ext_id  AS subjectExtId,
                   rl.created_at      AS createdAt
            FROM reward_ledger rl
            LEFT JOIN rules r ON r.id = rl.rule_id
            WHERE rl.member_id = :memberId
            ORDER BY rl.id DESC
            LIMIT :limit
            """)
    @org.jdbi.v3.sqlobject.config.RegisterConstructorMapper(RewardTraceRow.class)
    List<RewardTraceRow> recentRewardsByMember(
            @Bind("memberId") long memberId, @Bind("limit") int limit);

    @SqlQuery("""
            SELECT id, guild_ext_id AS guildExtId, status, started_at AS startedAt,
                   completed_at AS completedAt, checkpoint_count AS checkpointCount,
                   error_message AS errorMessage
            FROM historical_scan_jobs
            ORDER BY id DESC
            LIMIT 1
            """)
    @org.jdbi.v3.sqlobject.config.RegisterConstructorMapper(edu.franklin.acm.synapse.activity.guild.HistoricalScanJob.class)
    edu.franklin.acm.synapse.activity.guild.HistoricalScanJob findLatestHistoricalScanJob();

    record MemberProfile(
            long memberId,
            long userExtId,
            String displayName,
            String avatarHash,
            int pCurrency,
            int sCurrency,
            int level) {
    }

    record LeaderboardRow(
            long userExtId,
            String displayName,
            String avatarHash,
            int amount,
            int level) {
    }

    record RewardTraceRow(
            long id,
            String ruleName,
            String currencyType,
            int amount,
            String transactionType,
            String subjectType,
            Long subjectExtId,
            String createdAt) {
    }
}
