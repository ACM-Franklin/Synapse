package edu.franklin.acm.synapse.activity.guild;

import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Manages the single-row {@code synapse_statistics} table for operational
 * health tracking. Records when the bot started and when reconciliation
 * last completed.
 */
public interface SynapseStatisticsDao {

    @SqlUpdate("""
            INSERT INTO synapse_statistics (id, started_at, updated_at)
            VALUES (1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE SET
                started_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            """)
    void recordStartup();

    @SqlUpdate("""
            UPDATE synapse_statistics
            SET last_reconciled_at = CURRENT_TIMESTAMP,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = 1
            """)
    void recordReconciliation();
}
