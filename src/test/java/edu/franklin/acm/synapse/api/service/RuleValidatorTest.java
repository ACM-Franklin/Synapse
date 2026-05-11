package edu.franklin.acm.synapse.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.activity.channel.CategoryDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import edu.franklin.acm.synapse.activity.member.RoleDao;
import edu.franklin.acm.synapse.activity.rules.Rule;
import edu.franklin.acm.synapse.activity.rules.RulePredicate;
import edu.franklin.acm.synapse.test.JdbiTestSupport;

class RuleValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void channelReferenceIsValidWhenChannelIsActive() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        JdbiTestSupport.insertChannel(jdbi, 1234L);
        RuleValidator validator = newValidator(jdbi);

        List<String> reasons = validator.validate(List.of(
                new RulePredicate(0, 1, "IN_CHANNEL", "{\"channel_ext_id\":1234}", 0)));

        assertTrue(reasons.isEmpty());
    }

    @Test
    void channelReferenceIsInvalidWhenChannelIsMissing() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        RuleValidator validator = newValidator(jdbi);

        List<String> reasons = validator.validate(List.of(
                new RulePredicate(0, 1, "IN_CHANNEL", "{\"channel_ext_id\":4242}", 0)));

        assertEquals(1, reasons.size());
        assertTrue(reasons.get(0).contains("4242"));
    }

    @Test
    void roleReferenceIsValidWhenRoleIsActive() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        JdbiTestSupport.dao(jdbi, RoleDao.class).upsert(555L, "Admins");
        RuleValidator validator = newValidator(jdbi);

        List<String> reasons = validator.validate(List.of(
                new RulePredicate(0, 1, "MEMBER_HAS_ROLE", "{\"role_ext_id\":555}", 0)));

        assertTrue(reasons.isEmpty());
    }

    @Test
    void roleReferenceIsInvalidWhenRoleIsMissing() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        RuleValidator validator = newValidator(jdbi);

        List<String> reasons = validator.validate(List.of(
                new RulePredicate(0, 1, "MEMBER_HAS_ROLE", "{\"role_ext_id\":777}", 0)));

        assertFalse(reasons.isEmpty());
    }

    @Test
    void missingParameterIsFlagged() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        RuleValidator validator = newValidator(jdbi);

        List<String> reasons = validator.validate(List.of(
                new RulePredicate(0, 1, "IN_CHANNEL", "{}", 0)));

        assertEquals(1, reasons.size());
        assertTrue(reasons.get(0).contains("missing"));
    }

    @Test
    void unrecognizedPredicateTypeIsNotFlagged() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        RuleValidator validator = newValidator(jdbi);

        List<String> reasons = validator.validate(List.of(
                new RulePredicate(0, 1, "HOUR_OF_DAY_BETWEEN", "{\"start\":9,\"end\":17}", 0)));

        assertTrue(reasons.isEmpty(),
                "validator must not invent validation for predicates whose refs are not modeled");
    }

    @Test
    void unknownRuleEventTypeIsInvalid() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        RuleValidator validator = newValidator(jdbi);
        Rule rule = new Rule(1L, "bad", null, "GIBBERISH", true, true, false, 0, null, null);

        List<String> reasons = validator.validate(rule, List.of());

        assertEquals(1, reasons.size());
        assertTrue(reasons.get(0).contains("GIBBERISH"));
    }

    private static RuleValidator newValidator(Jdbi jdbi) {
        RuleValidator validator = new RuleValidator();
        validator.channelDao = JdbiTestSupport.dao(jdbi, ChannelDao.class);
        validator.roleDao = JdbiTestSupport.dao(jdbi, RoleDao.class);
        validator.categoryDao = JdbiTestSupport.dao(jdbi, CategoryDao.class);
        return validator;
    }
}
