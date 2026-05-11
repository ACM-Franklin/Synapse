package edu.franklin.acm.synapse.activity.rules;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;

import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.test.JdbiTestSupport;

class RewardReplayJobDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void jobLifecycleTracksReplayProgress() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        RewardReplayJobDao dao = JdbiTestSupport.dao(jdbi, RewardReplayJobDao.class);

        long jobId = dao.insertStarted(50);
        RewardReplayJob running = dao.findRunning();
        dao.markCompleted(jobId, 2, 10, 9, 1, 123L);
        RewardReplayJob completed = dao.findById(jobId);

        assertAll(
                () -> assertEquals(jobId, running.id()),
                () -> assertEquals("RUNNING", running.status()),
                () -> assertEquals(50, running.batchSize()),
                () -> assertNull(dao.findRunning()),
                () -> assertEquals("COMPLETED", completed.status()),
                () -> assertEquals(2, completed.batchesProcessed()),
                () -> assertEquals(10, completed.scannedCount()),
                () -> assertEquals(9, completed.replayedCount()),
                () -> assertEquals(1, completed.failedCount()),
                () -> assertEquals(123L, completed.lastMessageId()),
                () -> assertNotNull(completed.completedAt()));
    }
}