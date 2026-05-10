package edu.franklin.acm.synapse.activity.guild;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@RegisterConstructorMapper(HistoricalScanJob.class)
public interface HistoricalScanJobDao {

    @SqlUpdate("""
            INSERT INTO historical_scan_jobs (guild_ext_id, status)
            VALUES (:guildExtId, 'RUNNING')
            """)
    @GetGeneratedKeys("id")
    long insertStarted(@Bind("guildExtId") long guildExtId);

    @SqlUpdate("""
            UPDATE historical_scan_jobs
            SET status = 'COMPLETED',
                completed_at = CURRENT_TIMESTAMP,
                checkpoint_count = :checkpointCount,
                error_message = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId
            """)
    void markCompleted(@Bind("jobId") long jobId, @Bind("checkpointCount") int checkpointCount);

    @SqlUpdate("""
            UPDATE historical_scan_jobs
            SET status = 'FAILED',
                completed_at = CURRENT_TIMESTAMP,
                error_message = :errorMessage,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :jobId
            """)
    void markFailed(@Bind("jobId") long jobId, @Bind("errorMessage") String errorMessage);

    @SqlQuery("""
            SELECT id,
                   guild_ext_id AS guildExtId,
                   status,
                   started_at AS startedAt,
                   completed_at AS completedAt,
                   checkpoint_count AS checkpointCount,
                   error_message AS errorMessage
            FROM historical_scan_jobs
            WHERE id = :jobId
            """)
    HistoricalScanJob findById(@Bind("jobId") long jobId);
}