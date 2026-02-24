package edu.franklin.acm.synapse.activity.member;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Guild members. In single-guild mode, every Discord user in this guild is a
 * member. There is no separate "users" abstraction.
 *
 * Live scanner maintains currency for name, global_name, nickname, avatar_hash,
 * pending.
 */
public interface MemberDao {

    /**
     * Upsert a member with basic info. Used by both scanners on first
     * encounter. Sets is_active = 1 on insert (new members are active by
     * definition).
     */
    @SqlQuery("""
            INSERT INTO members (ext_id, name, is_bot)
            VALUES (:extId, :name, :isBot)
            ON CONFLICT (ext_id) DO UPDATE SET
                name = :name,
                is_active = 1,
                updated_at = CURRENT_TIMESTAMP
            RETURNING id
            """)
    long upsert(@Bind("extId") long extId, @Bind("name") String name, @Bind("isBot") boolean isBot);

    /**
     * Full upsert with all profile fields. Used by live scanner on
     * GUILD_MEMBER_UPDATE and GUILD_MEMBER_JOIN.
     */
    @SqlQuery("""
            INSERT INTO members (ext_id, name, global_name, nickname, avatar_hash,
                                is_bot, is_active, joined_at, premium_since, pending)
            VALUES (:extId, :name, :globalName, :nickname, :avatarHash,
                    :isBot, 1, :joinedAt, :premiumSince, :pending)
            ON CONFLICT (ext_id) DO UPDATE SET
                name = :name,
                global_name = :globalName,
                nickname = :nickname,
                avatar_hash = :avatarHash,
                is_bot = :isBot,
                is_active = 1,
                joined_at = :joinedAt,
                premium_since = :premiumSince,
                pending = :pending,
                updated_at = CURRENT_TIMESTAMP
            RETURNING id
            """)
    long upsertFull(
            @Bind("extId") long extId,
            @Bind("name") String name,
            @Bind("globalName") String globalName,
            @Bind("nickname") String nickname,
            @Bind("avatarHash") String avatarHash,
            @Bind("isBot") boolean isBot,
            @Bind("joinedAt") String joinedAt,
            @Bind("premiumSince") String premiumSince,
            @Bind("pending") boolean pending);

    @SqlQuery("SELECT id FROM members WHERE ext_id = :extId")
    Long findIdByExtId(@Bind("extId") long extId);

    /**
     * Soft-delete: mark a member as inactive when they leave the guild. We
     * never nuke member data — they might come back.
     */
    @SqlUpdate("""
            UPDATE members SET is_active = 0, updated_at = CURRENT_TIMESTAMP
            WHERE ext_id = :extId
            """)
    void deactivate(@Bind("extId") long extId);

    /**
     * Re-activate a member (called on re-join or startup reconciliation).
     */
    @SqlUpdate("""
            UPDATE members SET is_active = 1, updated_at = CURRENT_TIMESTAMP
            WHERE ext_id = :extId
            """)
    void activate(@Bind("extId") long extId);

    /**
     * Mark all members as inactive. Used during startup reconciliation — we'll
     * then re-activate the ones who are actually present in the guild.
     */
    @SqlUpdate("UPDATE members SET is_active = 0, updated_at = CURRENT_TIMESTAMP")
    void deactivateAll();
}
