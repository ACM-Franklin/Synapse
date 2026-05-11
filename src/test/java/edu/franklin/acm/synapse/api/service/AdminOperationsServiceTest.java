package edu.franklin.acm.synapse.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.annotation.Annotation;
import java.util.Iterator;

import org.junit.jupiter.api.Test;

import edu.franklin.acm.synapse.activity.rules.RewardReplayJob;
import edu.franklin.acm.synapse.activity.rules.RewardReplayJobDao;
import edu.franklin.acm.synapse.bot.SynapseBot;
import edu.franklin.acm.synapse.rules.engine.MessageRewardReplayRunSummary;
import edu.franklin.acm.synapse.rules.engine.MessageRewardReplayService;
import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

class AdminOperationsServiceTest {

    @Test
    void replayRejectsMissingConfirm() {
        AdminOperationsService svc = newService(null, mock(MessageRewardReplayService.class), mock(RewardReplayJobDao.class));
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> svc.startMessageRewardReplay("oops", 10));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void replayRejectsBadBatchSize() {
        AdminOperationsService svc = newService(null, mock(MessageRewardReplayService.class), mock(RewardReplayJobDao.class));
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> svc.startMessageRewardReplay(AdminOperationsService.REPLAY_CONFIRM, 0));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
        void replayRejectsWhenAnotherJobIsRunning() {
        RewardReplayJobDao jobDao = mock(RewardReplayJobDao.class);
        when(jobDao.findRunning()).thenReturn(job(9L, "RUNNING"));

        AdminOperationsService svc = newService(null, mock(MessageRewardReplayService.class), jobDao);
        WebApplicationException ex = assertThrows(WebApplicationException.class,
            () -> svc.startMessageRewardReplay(AdminOperationsService.REPLAY_CONFIRM, 50));

        assertEquals(Response.Status.CONFLICT.getStatusCode(), ex.getResponse().getStatus());
        }

        @Test
        void replayQueuesJobAndMarksItCompletedOnValidInput() {
        MessageRewardReplayService replay = mock(MessageRewardReplayService.class);
        RewardReplayJobDao jobDao = mock(RewardReplayJobDao.class);
        MessageRewardReplayRunSummary summary = new MessageRewardReplayRunSummary(1, 2, 2, 0, 99L);
        when(replay.replayAllActiveMessages(50)).thenReturn(summary);
        when(jobDao.findRunning()).thenReturn(null);
        when(jobDao.insertStarted(50)).thenReturn(42L);
        when(jobDao.findById(42L)).thenReturn(job(42L, "COMPLETED"));

        AdminOperationsService svc = newService(null, replay, jobDao);
        RewardReplayJob result = svc.startMessageRewardReplay(
                AdminOperationsService.REPLAY_CONFIRM, 50);

        assertEquals(42L, result.id());
        verify(jobDao).markCompleted(42L, 1, 2, 2, 0, 99L);
    }

    @Test
    void scanRejectsMissingConfirm() {
        AdminOperationsService svc = newService(null, mock(MessageRewardReplayService.class), mock(RewardReplayJobDao.class));
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> svc.startHistoricalScan(""));
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void scanReportsServiceUnavailableWhenBotMissing() {
        AdminOperationsService svc = newService(null, mock(MessageRewardReplayService.class), mock(RewardReplayJobDao.class));
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> svc.startHistoricalScan(AdminOperationsService.SCAN_CONFIRM));
        assertEquals(Response.Status.SERVICE_UNAVAILABLE.getStatusCode(), ex.getResponse().getStatus());
    }

    @Test
    void scanDelegatesToBotWhenAvailable() {
        SynapseBot bot = mock(SynapseBot.class);
        when(bot.triggerHistoricalScan()).thenReturn(42L);

        AdminOperationsService svc = newService(bot, mock(MessageRewardReplayService.class), mock(RewardReplayJobDao.class));
        long jobId = svc.startHistoricalScan(AdminOperationsService.SCAN_CONFIRM);
        assertEquals(42L, jobId);
    }

    private static AdminOperationsService newService(SynapseBot bot, MessageRewardReplayService replay,
                                                     RewardReplayJobDao jobDao) {
        AdminOperationsService svc = new AdminOperationsService();
        svc.botInstance = new FixedInstance<>(bot);
        svc.replayService = replay;
        svc.rewardReplayJobDao = jobDao;
        return svc;
    }

    private static RewardReplayJob job(long id, String status) {
        return new RewardReplayJob(id, status, 50, 0, 0, 0, 0, 0L, null, null, null);
    }

    /** Minimal {@link Instance} stub — we only call {@code isResolvable} and {@code get}. */
    private static final class FixedInstance<T> implements Instance<T> {
        private final T value;
        FixedInstance(T value) { this.value = value; }
        @Override public boolean isResolvable() { return value != null; }
        @Override public boolean isUnsatisfied() { return value == null; }
        @Override public boolean isAmbiguous() { return false; }
        @Override public T get() { return value; }

        @Override public Instance<T> select(Annotation... q) { throw new UnsupportedOperationException(); }
        @Override public <U extends T> Instance<U> select(Class<U> c, Annotation... q) { throw new UnsupportedOperationException(); }
        @Override public <U extends T> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> l, Annotation... q) { throw new UnsupportedOperationException(); }
        @Override public void destroy(T instance) { /* nothing to do */ }
        @Override public jakarta.enterprise.inject.Instance.Handle<T> getHandle() { throw new UnsupportedOperationException(); }
        @Override public Iterable<? extends jakarta.enterprise.inject.Instance.Handle<T>> handles() { throw new UnsupportedOperationException(); }
        @Override public Iterator<T> iterator() { throw new UnsupportedOperationException(); }
    }
}
