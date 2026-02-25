package edu.franklin.acm.synapse.activity.migrations;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface MigrationDao {
    @SqlQuery("SELECT name FROM migrations ORDER BY occurred_at DESC")
    List<String> getAll();

    @SqlUpdate("INSERT INTO migrations (name, succeeded) VALUES (:name, :success)")
    void commit(@Bind("name") String name, @Bind("success") boolean success);
}
