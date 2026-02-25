package edu.franklin.acm.synapse.activity.migrations;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

/**
 * DAO for easy access to the migrations table.
 */
public interface MigrationDao {
    /**
     * Collects all items from the migrations table.
     * @return A list of migration executions.
     */
    @SqlQuery("SELECT name, succeeded, occurred_at FROM migrations ORDER BY occurred_at DESC")
    @RegisterConstructorMapper(Migration.class)
    List<Migration> getAll();

    /**
     * Commits a new migration run into the table for future reference.
     * @param name    The name of the migration file executed.
     * @param success Whether this execution was a success.
     */
    @SqlUpdate("INSERT INTO migrations (name, succeeded) VALUES (:name, :success)")
    void commit(@Bind("name") String name, @Bind("success") boolean success);
}
