package edu.franklin.acm.synapse.api.service;

import org.eclipse.microprofile.context.ManagedExecutor;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.rules.RewardReplayJob;
import edu.franklin.acm.synapse.activity.rules.RewardReplayJobDao;
import edu.franklin.acm.synapse.bot.SynapseBot;
import edu.franklin.acm.synapse.rules.engine.MessageRewardReplayRunSummary;
import edu.franklin.acm.synapse.rules.engine.MessageRewardReplayService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Wraps the existing scan and replay subsystems behind a single API-facing
 * service. Uses {@link Instance} to look up {@link SynapseBot} so this stays
 * usable in tests where the JDA bot is disabled.
 */
@ApplicationScoped
public class AdminOperationsService {

    private static final Logger log = LoggerFactory.getLogger(AdminOperationsService.class);

    public static final String SCAN_CONFIRM = "I_UNDERSTAND_THIS_SCANS_DISCORD";
    public static final String REPLAY_CONFIRM = "I_UNDERSTAND_THIS_REPLAYS_REWARDS";

    @Inject Instance<SynapseBot> botInstance;
    @Inject MessageRewardReplayService replayService;
    @Inject RewardReplayJobDao rewardReplayJobDao;
    @Inject ManagedExecutor managedExecutor;

    public long startHistoricalScan(String confirm) {
        requireConfirm(confirm, SCAN_CONFIRM,
                "Historical scans hit Discord hard; resend with confirm=" + SCAN_CONFIRM);
        SynapseBot bot = resolveBot();
        try {
            long jobId = bot.triggerHistoricalScan();
            log.info("Admin API started historical scan job {}", jobId);
            return jobId;
        } catch (IllegalStateException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.SERVICE_UNAVAILABLE);
        }
    }

    public RewardReplayJob startMessageRewardReplay(String confirm, int batchSize) {
        requireConfirm(confirm, REPLAY_CONFIRM,
                "Reward replay rewrites the ledger; resend with confirm=" + REPLAY_CONFIRM);
        if (batchSize < 1) {
            throw new WebApplicationException("batchSize must be >= 1", Response.Status.BAD_REQUEST);
        }
        RewardReplayJob running = rewardReplayJobDao.findRunning();
        if (running != null) {
            throw new WebApplicationException(
                    "Reward replay job " + running.id() + " is already running",
                    Response.Status.CONFLICT);
        }

        long jobId;
        try {
            jobId = rewardReplayJobDao.insertStarted(batchSize);
        } catch (UnableToExecuteStatementException e) {
            throw new WebApplicationException(
                    "A reward replay job is already running",
                    Response.Status.CONFLICT);
        }

        log.info("Admin API queued message reward replay job {} (batchSize={})", jobId, batchSize);
        executor().execute(() -> runReplayJob(jobId, batchSize));
        return rewardReplayJobDao.findById(jobId);
    }

    public RewardReplayJob findRewardReplayJob(long jobId) {
        return rewardReplayJobDao.findById(jobId);
    }

    public SynapseBot bot() {
        return botInstance.isResolvable() ? botInstance.get() : null;
    }

    private SynapseBot resolveBot() {
        if (!botInstance.isResolvable()) {
            throw new WebApplicationException(
                    "Bot is not running in this process",
                    Response.Status.SERVICE_UNAVAILABLE);
        }
        return botInstance.get();
    }

    private void runReplayJob(long jobId, int batchSize) {
        try {
            MessageRewardReplayRunSummary summary = replayService.replayAllActiveMessages(batchSize);
            rewardReplayJobDao.markCompleted(
                    jobId,
                    summary.batchesProcessed(),
                    summary.scannedCount(),
                    summary.replayedCount(),
                    summary.failedCount(),
                    summary.lastMessageId());
            log.info("Completed message reward replay job {}: {}", jobId, summary);
        } catch (Throwable failure) {
            String message = failure.getMessage();
            if (message == null || message.isBlank()) {
                message = failure.getClass().getSimpleName();
            }
            rewardReplayJobDao.markFailed(jobId, message);
            log.error("Message reward replay job {} failed", jobId, failure);
        }
    }

    private java.util.concurrent.Executor executor() {
        return managedExecutor != null ? managedExecutor : Runnable::run;
    }

    private static void requireConfirm(String actual, String expected, String message) {
        if (!expected.equals(actual)) {
            throw new WebApplicationException(message, Response.Status.BAD_REQUEST);
        }
    }
}
