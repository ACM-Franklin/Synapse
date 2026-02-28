package edu.franklin.acm.synapse.rules.engine.predicates;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.franklin.acm.synapse.activity.SeasonDao;
import edu.franklin.acm.synapse.rules.engine.PredicateEvaluator;
import edu.franklin.acm.synapse.rules.engine.RuleContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Handles temporal predicates: time-of-day ranges, day-of-week checks,
 * and season activity.
 */
@ApplicationScoped
public class TemporalEvaluator implements PredicateEvaluator {

    private static final Logger log = Logger.getLogger(TemporalEvaluator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> HANDLED = Set.of(
            "HOUR_OF_DAY_BETWEEN", "DAY_OF_WEEK_IS",
            "DURING_SEASON", "NOT_DURING_SEASON", "SEASON_ACTIVE"
    );

    @Inject
    SeasonDao seasonDao;

    @Override
    public boolean handles(String predicateType) {
        return HANDLED.contains(predicateType);
    }

    @Override
    public boolean evaluate(String predicateType, RuleContext ctx, String parametersJson) {
        try {
            return switch (predicateType) {
                case "HOUR_OF_DAY_BETWEEN" -> hourOfDayBetween(parametersJson);
                case "DAY_OF_WEEK_IS" -> dayOfWeekIs(parametersJson);
                case "DURING_SEASON" -> duringSeason(parametersJson, false);
                case "NOT_DURING_SEASON" -> duringSeason(parametersJson, true);
                case "SEASON_ACTIVE" -> seasonActive();
                default -> false;
            };
        } catch (Exception e) {
            log.warnf(e, "TemporalEvaluator failed for predicate '%s'", predicateType);
            return false;
        }
    }

    private boolean hourOfDayBetween(String parametersJson) throws Exception {
        if (parametersJson == null) return false;
        JsonNode params = MAPPER.readTree(parametersJson);
        int from = params.path("from").asInt();
        int to = params.path("to").asInt();
        int currentHour = LocalDateTime.now(ZoneOffset.UTC).getHour();

        if (from <= to) {
            return currentHour >= from && currentHour < to;
        } else {
            // Wraps midnight, e.g., from=22 to=6
            return currentHour >= from || currentHour < to;
        }
    }

    private boolean dayOfWeekIs(String parametersJson) throws Exception {
        if (parametersJson == null) return false;
        JsonNode params = MAPPER.readTree(parametersJson);
        String day = params.path("day").asText().toUpperCase();
        DayOfWeek target = DayOfWeek.valueOf(day);
        return LocalDateTime.now(ZoneOffset.UTC).getDayOfWeek() == target;
    }

    private boolean duringSeason(String parametersJson, boolean negate) throws Exception {
        if (parametersJson == null) return false;
        JsonNode params = MAPPER.readTree(parametersJson);
        long seasonId = params.path("season_id").asLong();
        String now = LocalDateTime.now(ZoneOffset.UTC).toString();
        boolean active = seasonDao.countActiveSeason(seasonId, now) > 0;
        return negate != active;
    }

    private boolean seasonActive() {
        String now = LocalDateTime.now(ZoneOffset.UTC).toString();
        return seasonDao.countActiveSeasons(now) > 0;
    }
}
