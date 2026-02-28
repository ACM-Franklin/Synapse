package edu.franklin.acm.synapse.activity;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * Queries the seasons table for active-season checks used by the rule engine.
 */
public interface SeasonDao {

    /**
     * Returns 1 if the given season is currently active (started and not yet ended), 0 otherwise.
     */
    @SqlQuery("""
            SELECT COUNT(*) FROM seasons
            WHERE id = :seasonId
              AND starts_at <= :now
              AND (ends_at IS NULL OR ends_at > :now)
            """)
    int countActiveSeason(@Bind("seasonId") long seasonId, @Bind("now") String now);

    /**
     * Returns the count of currently active seasons (any season that has started and not yet ended).
     */
    @SqlQuery("""
            SELECT COUNT(*) FROM seasons
            WHERE starts_at <= :now
              AND (ends_at IS NULL OR ends_at > :now)
            """)
    int countActiveSeasons(@Bind("now") String now);
}
