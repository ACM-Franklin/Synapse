package edu.franklin.acm.synapse.activity.guild;

import java.nio.file.Path;
import java.util.List;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.franklin.acm.synapse.test.JdbiTestSupport;

class HistoricalScanCheckpointDaoTest {

    @TempDir
    Path tempDir;

    @Test
    void upsertCreatesAndAdvancesCheckpointWithoutRegressing() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        HistoricalScanCheckpointDao dao = JdbiTestSupport.dao(jdbi, HistoricalScanCheckpointDao.class);

        dao.upsert(9101L, 9201L, 9301L);
        dao.upsert(9101L, 9201L, 9300L);
        dao.upsert(9101L, 9201L, 9302L);

        List<HistoricalScanCheckpoint> checkpoints = dao.findByGuildExtId(9101L);
        HistoricalScanCheckpoint checkpoint = checkpoints.get(0);

        assertAll(
                () -> assertEquals(1, checkpoints.size()),
                () -> assertEquals(9101L, checkpoint.guildExtId()),
                () -> assertEquals(9201L, checkpoint.channelExtId()),
                () -> assertEquals(9302L, checkpoint.lastMessageExtId()),
                () -> assertNotNull(checkpoint.scannedAt()));
    }

    @Test
    void findByGuildExtIdReturnsOnlyRequestedGuildCheckpoints() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        HistoricalScanCheckpointDao dao = JdbiTestSupport.dao(jdbi, HistoricalScanCheckpointDao.class);

        dao.upsert(9102L, 9202L, 9302L);
        dao.upsert(9102L, 9203L, 9303L);
        dao.upsert(9103L, 9204L, 9304L);

        List<HistoricalScanCheckpoint> checkpoints = dao.findByGuildExtId(9102L);

        assertAll(
                () -> assertEquals(2, checkpoints.size()),
                () -> assertEquals(9202L, checkpoints.get(0).channelExtId()),
                () -> assertEquals(9203L, checkpoints.get(1).channelExtId()));
    }
}