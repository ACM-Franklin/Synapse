package edu.franklin.acm.synapse.activity.guild;

import java.nio.file.Path;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.test.JdbiTestSupport;

class HistoricalScanJobDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void insertStartedCreatesRunningJobRow() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        HistoricalScanJobDao dao = JdbiTestSupport.dao(jdbi, HistoricalScanJobDao.class);

        long jobId = dao.insertStarted(9001L);
        HistoricalScanJob job = dao.findById(jobId);

        assertAll(
                () -> assertEquals(jobId, job.id()),
                () -> assertEquals(9001L, job.guildExtId()),
                () -> assertEquals("RUNNING", job.status()),
                () -> assertEquals(0, job.checkpointCount()),
                () -> assertNull(job.completedAt()),
                () -> assertNull(job.errorMessage()),
                () -> assertNotNull(job.startedAt()));
    }

    @Test
    void markCompletedRecordsCompletionState() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        HistoricalScanJobDao dao = JdbiTestSupport.dao(jdbi, HistoricalScanJobDao.class);

        long jobId = dao.insertStarted(9002L);
        dao.markCompleted(jobId, 4);
        HistoricalScanJob job = dao.findById(jobId);

        assertAll(
                () -> assertEquals("COMPLETED", job.status()),
                () -> assertEquals(4, job.checkpointCount()),
                () -> assertNotNull(job.completedAt()),
                () -> assertNull(job.errorMessage()));
    }

    @Test
    void markFailedRecordsFailureMessage() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        HistoricalScanJobDao dao = JdbiTestSupport.dao(jdbi, HistoricalScanJobDao.class);

        long jobId = dao.insertStarted(9003L);
        dao.markFailed(jobId, "boom");
        HistoricalScanJob job = dao.findById(jobId);

        assertAll(
                () -> assertEquals("FAILED", job.status()),
                () -> assertEquals("boom", job.errorMessage()),
                () -> assertNotNull(job.completedAt()));
    }
}