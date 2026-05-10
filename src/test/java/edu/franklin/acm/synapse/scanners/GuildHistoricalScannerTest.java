package edu.franklin.acm.synapse.scanners;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.franklin.acm.synapse.activity.guild.GuildMetadataDao;
import edu.franklin.acm.synapse.activity.member.MemberDao;
import edu.franklin.acm.synapse.scanners.shared.ChannelService;
import edu.franklin.acm.synapse.scanners.shared.MessagePersistenceService;
import edu.franklin.acm.synapse.scanners.shared.ThreadService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageHistory.MessageRetrieveAction;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.utils.cache.SortedSnowflakeCacheView;

class GuildHistoricalScannerTest {

    @Test
    void failedChannelPagePersistsCheckpointAndRerunResumesAfterLastCompletedPage() {
        GuildHistoricalScanner scanner = new GuildHistoricalScanner();
        scanner.memberDao = mock(MemberDao.class);
        scanner.guildMetadataDao = mock(GuildMetadataDao.class);
        scanner.channelService = mock(ChannelService.class);
        scanner.threadService = mock(ThreadService.class);
        scanner.messagePersistenceService = mock(MessagePersistenceService.class);

        Guild guild = mock(Guild.class);
        VoiceChannel channel = mock(VoiceChannel.class);
        @SuppressWarnings("unchecked")
        SortedSnowflakeCacheView<ThreadChannel> threadCache =
            mock(SortedSnowflakeCacheView.class);
        MessageRetrieveAction firstPageAction = mock(MessageRetrieveAction.class);
        MessageRetrieveAction failingSecondPageAction = mock(MessageRetrieveAction.class);
        MessageRetrieveAction resumedPageAction = mock(MessageRetrieveAction.class);
        MessageHistory firstHistory = mock(MessageHistory.class);
        MessageHistory resumedHistory = mock(MessageHistory.class);
        List<Message> firstPageMessages = messages(1L, 100L);
        List<Message> resumedPageMessages = List.of(message(101L));

        when(guild.getIdLong()).thenReturn(6001L);
        when(guild.getName()).thenReturn("Franklin ACM");
        when(guild.getTimeCreated()).thenReturn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        when(guild.getTextChannels()).thenReturn(List.of());
        when(guild.getNewsChannels()).thenReturn(List.of());
        when(guild.getVoiceChannels()).thenReturn(List.of(channel));
        when(guild.getStageChannels()).thenReturn(List.of());
        when(guild.getForumChannels()).thenReturn(List.of());
        when(guild.getThreadChannelCache()).thenReturn(threadCache);
        when(threadCache.iterator()).thenReturn(List.<ThreadChannel>of().iterator());

        when(channel.getIdLong()).thenReturn(7001L);
        when(channel.getName()).thenReturn("voice-logs");
        when(channel.getHistoryFromBeginning(100)).thenReturn(firstPageAction);
        when(channel.getHistoryAfter(100L, 100)).thenReturn(failingSecondPageAction, resumedPageAction);
        when(firstPageAction.complete()).thenReturn(firstHistory);
        when(failingSecondPageAction.complete()).thenThrow(new IllegalStateException("page two boom"));
        when(resumedPageAction.complete()).thenReturn(resumedHistory);
        when(firstHistory.getRetrievedHistory()).thenReturn(firstPageMessages);
        when(resumedHistory.getRetrievedHistory()).thenReturn(resumedPageMessages);

        when(scanner.memberDao.upsert(anyLong(), anyString(), eq(false))).thenReturn(8001L);
        when(scanner.channelService.upsertChannel(channel)).thenReturn(9001L);
        Map<Long, Long> persistedCheckpoints = new HashMap<>();

        CompletionException failure = assertThrows(CompletionException.class,
                () -> scanner.scanGuild(guild, Map.of(), persistedCheckpoints::put).join());
        assertEquals("page two boom", failure.getCause().getMessage());
        assertEquals(100L, persistedCheckpoints.get(7001L));

        Map<Long, Long> resumedCheckpoints = scanner
                .scanGuild(guild, new HashMap<>(persistedCheckpoints), persistedCheckpoints::put)
                .join();

        assertAll(
                () -> assertEquals(101L, persistedCheckpoints.get(7001L)),
                () -> assertEquals(101L, resumedCheckpoints.get(7001L)),
                () -> verify(channel, times(1)).getHistoryFromBeginning(100),
                () -> verify(channel, times(2)).getHistoryAfter(100L, 100));
    }

    private static List<Message> messages(long firstId, long lastId) {
        return LongStream.rangeClosed(firstId, lastId)
                .mapToObj(GuildHistoricalScannerTest::message)
                .toList();
    }

    private static Message message(long id) {
        Message message = mock(Message.class);
        User author = mock(User.class);
        when(message.getIdLong()).thenReturn(id);
        when(message.getTimeCreated()).thenReturn(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
        when(message.getAuthor()).thenReturn(author);
        when(author.getIdLong()).thenReturn(5000L + id);
        when(author.getName()).thenReturn("user-" + id);
        when(author.isBot()).thenReturn(false);
        return message;
    }
}