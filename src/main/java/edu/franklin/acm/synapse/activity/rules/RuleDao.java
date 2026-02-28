package edu.franklin.acm.synapse.activity.rules;

import java.util.List;

import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@RegisterConstructorMapper(Rule.class)
public interface RuleDao {

    @SqlQuery("""
            SELECT id, name, description, event_type, enabled,
                   applies_live, applies_historic, cooldown_seconds,
                   created_at, updated_at
            FROM rules
            WHERE enabled = 1 AND event_type = :eventType
            """)
    List<Rule> findEnabledByEventType(@Bind("eventType") String eventType);

    @SqlQuery("""
            SELECT id, name, description, event_type, enabled,
                   applies_live, applies_historic, cooldown_seconds,
                   created_at, updated_at
            FROM rules
            ORDER BY name
            """)
    List<Rule> findAll();

    @SqlUpdate("""
            INSERT INTO rules (name, description, event_type, enabled,
                               applies_live, applies_historic, cooldown_seconds)
            VALUES (:name, :description, :eventType, :enabled,
                    :appliesLive, :appliesHistoric, :cooldownSeconds)
            """)
    @GetGeneratedKeys
    long insert(
            @Bind("name") String name,
            @Bind("description") String description,
            @Bind("eventType") String eventType,
            @Bind("enabled") boolean enabled,
            @Bind("appliesLive") boolean appliesLive,
            @Bind("appliesHistoric") boolean appliesHistoric,
            @Bind("cooldownSeconds") int cooldownSeconds);

    @SqlUpdate("UPDATE rules SET enabled = :enabled, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
    void updateEnabled(@Bind("id") long id, @Bind("enabled") boolean enabled);

    @SqlUpdate("DELETE FROM rules WHERE id = :id")
    void delete(@Bind("id") long id);
}
