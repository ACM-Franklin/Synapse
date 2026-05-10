package edu.franklin.acm.synapse.scanners;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.franklin.acm.synapse.activity.guild.HistoricalScanCheckpoint;
import edu.franklin.acm.synapse.activity.guild.HistoricalScanCheckpointDao;
import edu.franklin.acm.synapse.activity.guild.HistoricalScanJobDao;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.dv8tion.jda.api.entities.Guild;

@ApplicationScoped
public class HistoricalScanCoordinator {

    private static final Logger log = LoggerFactory.getLogger(HistoricalScanCoordinator.class);

    @Inject HistoricalScanJobDao historicalScanJobDao;
    @Inject HistoricalScanCheckpointDao historicalScanCheckpointDao;
    @Inject GuildHistoricalScanner guildHistoricalScanner;

    public long startGuildScan(Guild guild) {
        long guildExtId = guild.getIdLong();
        long jobId = historicalScanJobDao.insertStarted(guildExtId);
        Map<Long, Long> checkpoints = loadCheckpoints(guildExtId);

        log.info("Starting historical scan job {} for guild {} ({}) with {} checkpoints",
                jobId, guild.getName(), guildExtId, checkpoints.size());

        guildHistoricalScanner.scanGuild(guild, checkpoints,
                (channelExtId, lastMessageExtId) ->
                    historicalScanCheckpointDao.upsert(guildExtId, channelExtId, lastMessageExtId))
            .thenAccept(updatedCheckpoints -> completeJob(jobId, guildExtId, updatedCheckpoints.size()))
                .exceptionally(ex -> {
                    failJob(jobId, guildExtId, ex);
                    return null;
                });

        return jobId;
    }

        void completeJob(long jobId, long guildExtId, int checkpointCount) {
        historicalScanJobDao.markCompleted(jobId, checkpointCount);
        log.info("Completed historical scan job {} for guild {} with {} checkpoints",
            jobId, guildExtId, checkpointCount);
    }

    void failJob(long jobId, long guildExtId, Throwable failure) {
        Throwable root = unwrap(failure);
        String errorMessage = root.getMessage();
        if (errorMessage == null || errorMessage.isBlank()) {
            errorMessage = root.getClass().getSimpleName();
        }
        historicalScanJobDao.markFailed(jobId, errorMessage);
        log.error("Historical scan job {} failed for guild {}", jobId, guildExtId, root);
    }

    private Map<Long, Long> loadCheckpoints(long guildExtId) {
        Map<Long, Long> checkpoints = new HashMap<>();
        for (HistoricalScanCheckpoint checkpoint : historicalScanCheckpointDao.findByGuildExtId(guildExtId)) {
            checkpoints.put(checkpoint.channelExtId(), checkpoint.lastMessageExtId());
        }
        return checkpoints;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}