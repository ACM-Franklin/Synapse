package edu.franklin.acm.synapse.scanners;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import edu.franklin.acm.synapse.activity.guild.HistoricalScanCheckpoint;
import edu.franklin.acm.synapse.activity.guild.HistoricalScanCheckpointDao;
import edu.franklin.acm.synapse.activity.guild.HistoricalScanJob;
import edu.franklin.acm.synapse.activity.guild.HistoricalScanJobDao;
import edu.franklin.acm.synapse.test.JdbiTestSupport;
import net.dv8tion.jda.api.entities.Guild;

class HistoricalScanCoordinatorTest {

    @TempDir
    Path tempDir;

    @Test
    void startGuildScanCompletesJobAndPersistsReturnedCheckpoints() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        HistoricalScanCoordinator coordinator = coordinator(jdbi);
        HistoricalScanCheckpointDao checkpointDao = JdbiTestSupport.dao(jdbi, HistoricalScanCheckpointDao.class);
        HistoricalScanJobDao jobDao = JdbiTestSupport.dao(jdbi, HistoricalScanJobDao.class);
        Guild guild = mock(Guild.class);

        checkpointDao.upsert(7001L, 8001L, 9001L);
        when(guild.getIdLong()).thenReturn(7001L);
        when(guild.getName()).thenReturn("Franklin ACM");
        when(coordinator.guildHistoricalScanner.scanGuild(eq(guild), anyMap(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<Long, Long> watermarks = invocation.getArgument(1, Map.class);
            @SuppressWarnings("unchecked")
            BiConsumer<Long, Long> checkpointRecorder = invocation.getArgument(2, BiConsumer.class);
            assertEquals(9001L, watermarks.get(8001L));
            checkpointRecorder.accept(8001L, 9002L);
            checkpointRecorder.accept(8002L, 9100L);
            return CompletableFuture.completedFuture(Map.of(8001L, 9002L, 8002L, 9100L));
        });

        long jobId = coordinator.startGuildScan(guild);
        HistoricalScanJob job = jobDao.findById(jobId);
        List<HistoricalScanCheckpoint> checkpoints = checkpointDao.findByGuildExtId(7001L);

        assertAll(
                () -> assertEquals("COMPLETED", job.status()),
                () -> assertEquals(2, job.checkpointCount()),
                () -> assertEquals(2, checkpoints.size()),
                () -> assertEquals(9002L, checkpoints.get(0).lastMessageExtId()),
                () -> assertEquals(9100L, checkpoints.get(1).lastMessageExtId()));
    }

    @Test
    void startGuildScanMarksJobFailedWhenScannerFails() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        HistoricalScanCoordinator coordinator = coordinator(jdbi);
        HistoricalScanJobDao jobDao = JdbiTestSupport.dao(jdbi, HistoricalScanJobDao.class);
        Guild guild = mock(Guild.class);

        when(guild.getIdLong()).thenReturn(7002L);
        when(guild.getName()).thenReturn("Franklin ACM");
        when(coordinator.guildHistoricalScanner.scanGuild(eq(guild), anyMap(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("scanner boom")));

        long jobId = coordinator.startGuildScan(guild);
        HistoricalScanJob job = jobDao.findById(jobId);

        assertAll(
                () -> assertEquals("FAILED", job.status()),
                () -> assertEquals("scanner boom", job.errorMessage()));
    }

    @Test
    void failedScanPersistsProgressAndRerunResumesFromSavedCheckpoint() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        HistoricalScanCoordinator coordinator = coordinator(jdbi);
        HistoricalScanCheckpointDao checkpointDao = JdbiTestSupport.dao(jdbi, HistoricalScanCheckpointDao.class);
        HistoricalScanJobDao jobDao = JdbiTestSupport.dao(jdbi, HistoricalScanJobDao.class);
        Guild guild = mock(Guild.class);

        when(guild.getIdLong()).thenReturn(7003L);
        when(guild.getName()).thenReturn("Franklin ACM");
        when(coordinator.guildHistoricalScanner.scanGuild(eq(guild), anyMap(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    BiConsumer<Long, Long> checkpointRecorder = invocation.getArgument(2, BiConsumer.class);
                    checkpointRecorder.accept(8003L, 9003L);
                    return CompletableFuture.failedFuture(new IllegalStateException("interrupted"));
                })
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Map<Long, Long> watermarks = invocation.getArgument(1, Map.class);
                    @SuppressWarnings("unchecked")
                    BiConsumer<Long, Long> checkpointRecorder = invocation.getArgument(2, BiConsumer.class);
                    assertEquals(9003L, watermarks.get(8003L));
                    checkpointRecorder.accept(8003L, 9004L);
                    checkpointRecorder.accept(8004L, 9104L);
                    return CompletableFuture.completedFuture(Map.of(8003L, 9004L, 8004L, 9104L));
                });

        long failedJobId = coordinator.startGuildScan(guild);
        HistoricalScanJob failedJob = jobDao.findById(failedJobId);
        List<HistoricalScanCheckpoint> failedRunCheckpoints = checkpointDao.findByGuildExtId(7003L);

        assertAll(
                () -> assertEquals("FAILED", failedJob.status()),
                () -> assertEquals("interrupted", failedJob.errorMessage()),
                () -> assertEquals(1, failedRunCheckpoints.size()),
                () -> assertEquals(9003L, failedRunCheckpoints.get(0).lastMessageExtId()));

        long completedJobId = coordinator.startGuildScan(guild);
        HistoricalScanJob completedJob = jobDao.findById(completedJobId);
        List<HistoricalScanCheckpoint> completedRunCheckpoints = checkpointDao.findByGuildExtId(7003L);

        assertAll(
                () -> assertEquals("COMPLETED", completedJob.status()),
                () -> assertEquals(2, completedJob.checkpointCount()),
                () -> assertEquals(2, completedRunCheckpoints.size()),
                () -> assertEquals(9004L, completedRunCheckpoints.get(0).lastMessageExtId()),
                () -> assertEquals(9104L, completedRunCheckpoints.get(1).lastMessageExtId()));
    }

    private static HistoricalScanCoordinator coordinator(Jdbi jdbi) {
        HistoricalScanCoordinator coordinator = new HistoricalScanCoordinator();
        coordinator.historicalScanJobDao = JdbiTestSupport.dao(jdbi, HistoricalScanJobDao.class);
        coordinator.historicalScanCheckpointDao = JdbiTestSupport.dao(jdbi, HistoricalScanCheckpointDao.class);
        coordinator.guildHistoricalScanner = mock(GuildHistoricalScanner.class);
        return coordinator;
    }
}