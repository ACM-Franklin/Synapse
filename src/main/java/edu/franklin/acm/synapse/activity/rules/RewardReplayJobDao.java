package edu.franklin.acm.synapse.activity.rules;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@RegisterConstructorMapper(RewardReplayJob.class)
public interface RewardReplayJobDao {

    @SqlUpdate("""
            INSERT INTO reward_replay_jobs (status, batch_size)
            VALUES ('RUNNING', :batchSize)
            """)
    @GetGeneratedKeys("id")
    long insertStarted(@Bind("batchSize") int batchSize);

    @SqlUpdate("""
            UPDATE reward_replay_jobs
            SET status = 'COMPLETED',
                batches_processed = :batchesProcessed,
                scanned_count = :scannedCount,
                replayed_count = :replayedCount,
                failed_count = :failedCount,
                last_message_id = :lastMessageId,
                completed_at = CURRENT_TIMESTAMP,
                error_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId
            """)
    void markCompleted(
            @Bind("jobId") long jobId,
            @Bind("batchesProcessed") int batchesProcessed,
            @Bind("scannedCount") int scannedCount,
            @Bind("replayedCount") int replayedCount,
            @Bind("failedCount") int failedCount,
            @Bind("lastMessageId") long lastMessageId);

    @SqlUpdate("""
            UPDATE reward_replay_jobs
            SET status = 'FAILED',
                completed_at = CURRENT_TIMESTAMP,
                error_message = :errorMessage,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId
            """)
    void markFailed(@Bind("jobId") long jobId, @Bind("errorMessage") String errorMessage);

    @SqlQuery("""
            SELECT id, status, batch_size AS batchSize,
                   batches_processed AS batchesProcessed,
                   scanned_count AS scannedCount,
                   replayed_count AS replayedCount,
                   failed_count AS failedCount,
                   last_message_id AS lastMessageId,
                   started_at AS startedAt,
                   completed_at AS completedAt,
                   error_message AS errorMessage
            FROM reward_replay_jobs
            WHERE id = :jobId
            """)
    RewardReplayJob findById(@Bind("jobId") long jobId);

    @SqlQuery("""
            SELECT id, status, batch_size AS batchSize,
                   batches_processed AS batchesProcessed,
                   scanned_count AS scannedCount,
                   replayed_count AS replayedCount,
                   failed_count AS failedCount,
                   last_message_id AS lastMessageId,
                   started_at AS startedAt,
                   completed_at AS completedAt,
                   error_message AS errorMessage
            FROM reward_replay_jobs
            WHERE status = 'RUNNING'
            ORDER BY id
            LIMIT 1
            """)
    RewardReplayJob findRunning();
}