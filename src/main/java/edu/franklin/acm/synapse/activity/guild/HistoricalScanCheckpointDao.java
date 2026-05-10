package edu.franklin.acm.synapse.activity.guild;

import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@RegisterConstructorMapper(HistoricalScanCheckpoint.class)
public interface HistoricalScanCheckpointDao {

    @SqlUpdate("""
            INSERT INTO historical_scan_checkpoints (guild_ext_id, channel_ext_id, last_message_ext_id)
            VALUES (:guildExtId, :channelExtId, :lastMessageExtId)
            ON CONFLICT (guild_ext_id, channel_ext_id) DO UPDATE SET
                last_message_ext_id = excluded.last_message_ext_id,
                scanned_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE excluded.last_message_ext_id > historical_scan_checkpoints.last_message_ext_id
            """)
    void upsert(@Bind("guildExtId") long guildExtId,
                @Bind("channelExtId") long channelExtId,
                @Bind("lastMessageExtId") long lastMessageExtId);

    @SqlQuery("""
            SELECT id,
                   guild_ext_id AS guildExtId,
                   channel_ext_id AS channelExtId,
                   last_message_ext_id AS lastMessageExtId,
                   scanned_at AS scannedAt
            FROM historical_scan_checkpoints
            WHERE guild_ext_id = :guildExtId
            ORDER BY channel_ext_id
            """)
    List<HistoricalScanCheckpoint> findByGuildExtId(@Bind("guildExtId") long guildExtId);
}