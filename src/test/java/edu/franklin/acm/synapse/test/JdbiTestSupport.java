package edu.franklin.acm.synapse.test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import edu.franklin.acm.synapse.activity.Event;
import edu.franklin.acm.synapse.activity.EventDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.activity.message.MessageEvent;
import edu.franklin.acm.synapse.activity.rules.RuleDao;
import edu.franklin.acm.synapse.activity.rules.RuleOutcome;
import edu.franklin.acm.synapse.activity.rules.RuleOutcomeDao;

public final class JdbiTestSupport {

    private JdbiTestSupport() {
    }

    public static Jdbi sqliteWithSchema(Path tempDir) {
        Jdbi jdbi = Jdbi.create("jdbc:sqlite:" + tempDir.resolve("synapse-test.sqlite").toAbsolutePath());
        jdbi.installPlugin(new SqlObjectPlugin());
        loadSchema(jdbi);
        return jdbi;
    }

    @SuppressWarnings("null")
    public static <T> T dao(Jdbi jdbi, Class<T> daoType) {
        return jdbi.onDemand(daoType);
    }

    public static long insertMember(Jdbi jdbi, long extId) {
        return dao(jdbi, MemberDao.class).upsert(extId, "member-" + extId, false);
    }

    public static long insertChannel(Jdbi jdbi, long extId) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                INSERT INTO channels (ext_id, name, type)
                VALUES (:extId, :name, 'TEXT')
                """)
                .bind("extId", extId)
                .bind("name", "channel-" + extId)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(long.class)
                .one());
    }

    public static long insertEvent(Jdbi jdbi, long memberId, Long channelId, String eventType) {
        return dao(jdbi, EventDao.class).insert(new Event(
                0L,
                memberId,
                channelId,
                eventType,
                "2026-01-01T00:00:00"));
    }

    public static MessageEvent messageEvent(long eventId, long extId, String content,
                                            int type, boolean authorIsBot, int reactionCount) {
        return new MessageEvent(
                0L,
                eventId,
                extId,
                null,
                0L,
                content.length(),
                type,
                0,
                reactionCount,
                0,
                0,
                0,
                0,
                content,
                null,
                null,
                "2026-01-01T00:00:00",
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                authorIsBot);
    }

    public static long insertRule(Jdbi jdbi, String name, int cooldownSeconds) {
        return dao(jdbi, RuleDao.class).insert(
                name,
                "test rule",
                "MESSAGE_CREATE",
                true,
                true,
                false,
                cooldownSeconds);
    }

    public static long insertOutcome(Jdbi jdbi, long ruleId, Integer pCurrency, Integer sCurrency) {
        dao(jdbi, RuleOutcomeDao.class).insertBatch(List.of(new RuleOutcome(
                0L,
                ruleId,
                "CURRENCY",
                pCurrency,
                sCurrency,
                null)));
        return jdbi.withHandle(handle -> handle.createQuery("""
                SELECT id FROM rule_outcomes WHERE rule_id = :ruleId
                """)
                .bind("ruleId", ruleId)
                .mapTo(long.class)
                .one());
    }

    @SuppressWarnings("null")
    public static int queryInt(Jdbi jdbi, String sql) {
        return jdbi.withHandle(handle -> handle.createQuery(sql)
                .mapTo(int.class)
                .one());
    }

    @SuppressWarnings("null")
    public static int queryInt(Jdbi jdbi, String sql, String bindName, Object bindValue) {
        return jdbi.withHandle(handle -> handle.createQuery(sql)
                .bind(bindName, bindValue)
                .mapTo(int.class)
                .one());
    }

    @SuppressWarnings("null")
    public static Map<String, Object> queryRow(Jdbi jdbi, String sql, String bindName, Object bindValue) {
        return jdbi.withHandle(handle -> handle.createQuery(sql)
                .bind(bindName, bindValue)
                .mapToMap()
                .one());
    }

    @SuppressWarnings("null")
    public static Set<String> queryStringSet(Jdbi jdbi, String sql) {
        return jdbi.withHandle(handle -> handle.createQuery(sql)
                .mapTo(String.class)
                .collect(Collectors.toSet()));
    }

    private static void loadSchema(Jdbi jdbi) {
        URL schema = JdbiTestSupport.class.getClassLoader().getResource("schemas/synapse.sql");
        if (schema == null) {
            throw new IllegalStateException("schemas/synapse.sql is missing from the test classpath");
        }

        try {
            String sql = Files.readString(Path.of(schema.toURI()), StandardCharsets.UTF_8);
            jdbi.useHandle(handle -> handle.createScript(sql).execute());
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Failed to load test schema", e);
        }
    }
}
