package edu.franklin.acm.synapse.scanners.handlers;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.jdbi.v3.core.Jdbi;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import edu.franklin.acm.synapse.activity.channel.CategoryDao;
import edu.franklin.acm.synapse.activity.channel.ChannelDao;
import edu.franklin.acm.synapse.activity.thread.ForumTagDao;
import edu.franklin.acm.synapse.activity.thread.ThreadDao;
import edu.franklin.acm.synapse.activity.thread.ThreadTagDao;
import edu.franklin.acm.synapse.scanners.shared.ChannelService;
import edu.franklin.acm.synapse.scanners.shared.ThreadService;
import edu.franklin.acm.synapse.test.JdbiTestSupport;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel.AutoArchiveDuration;
import net.dv8tion.jda.api.entities.channel.unions.ChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateArchivedEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateLockedEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;

class ChannelEventHandlerTest {

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-01-01T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void categoryRenameUpdatesStoredCategoryName() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        ChannelEventHandler handler = channelEventHandler(jdbi);
        JdbiTestSupport.dao(jdbi, CategoryDao.class).upsert(6001L, "old-category", CREATED_AT.toString());

        Category category = category(6001L, "new-category");

        ChannelUpdateNameEvent event = mock(ChannelUpdateNameEvent.class);
        when(event.getChannel()).thenReturn((ChannelUnion) category);
        when(event.getOldValue()).thenReturn("old-category");
        when(event.getNewValue()).thenReturn("new-category");

        handler.handleNameUpdate(event);

        Map<String, Object> row = JdbiTestSupport.queryRow(jdbi, """
                SELECT name, is_active FROM categories WHERE ext_id = :extId
                """, "extId", 6001L);

        assertAll(
                () -> assertEquals("new-category", row.get("name")),
                () -> assertEquals(1, intValue(row, "is_active")));
    }

    @Test
    void threadCreateRenameAndDeletePersistCurrentState() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        ChannelEventHandler handler = channelEventHandler(jdbi);
        ThreadChannel createdThread = thread(7001L, "thread-a", false, false);
        ChannelCreateEvent createEvent = mock(ChannelCreateEvent.class);
        when(createEvent.getChannel()).thenReturn((ChannelUnion) createdThread);

        handler.handleCreate(createEvent);

        ThreadChannel renamedThread = thread(7001L, "thread-renamed", false, false);
        ChannelUpdateNameEvent renameEvent = mock(ChannelUpdateNameEvent.class);
        when(renameEvent.getChannel()).thenReturn((ChannelUnion) renamedThread);
        when(renameEvent.getOldValue()).thenReturn("thread-a");
        when(renameEvent.getNewValue()).thenReturn("thread-renamed");
        handler.handleNameUpdate(renameEvent);

        ChannelDeleteEvent deleteEvent = mock(ChannelDeleteEvent.class);
        when(deleteEvent.getChannel()).thenReturn((ChannelUnion) renamedThread);
        handler.handleDelete(deleteEvent);

        Map<String, Object> threadRow = JdbiTestSupport.queryRow(jdbi, """
                SELECT name, is_active FROM threads WHERE ext_id = :extId
                """, "extId", 7001L);
        Map<String, Object> parentChannelRow = JdbiTestSupport.queryRow(jdbi, """
                SELECT name, type, is_active FROM channels WHERE ext_id = :extId
                """, "extId", 7101L);

        assertAll(
                () -> assertEquals("thread-renamed", threadRow.get("name")),
                () -> assertEquals(0, intValue(threadRow, "is_active")),
                () -> assertEquals("parent-7101", parentChannelRow.get("name")),
                () -> assertEquals("TEXT", parentChannelRow.get("type")),
                () -> assertEquals(1, intValue(parentChannelRow, "is_active")));
    }

    @Test
    void threadArchiveAndLockUpdatesPersistFlags() {
        Jdbi jdbi = JdbiTestSupport.sqliteWithSchema(tempDir);
        ChannelEventHandler handler = channelEventHandler(jdbi);
        ThreadChannel baseThread = thread(8001L, "thread-flags", false, false);
        ChannelCreateEvent createEvent = mock(ChannelCreateEvent.class);
        when(createEvent.getChannel()).thenReturn((ChannelUnion) baseThread);
        handler.handleCreate(createEvent);

        ThreadChannel archivedThread = thread(8001L, "thread-flags", true, false);
        ChannelUpdateArchivedEvent archivedEvent = mock(ChannelUpdateArchivedEvent.class);
        when(archivedEvent.getChannel()).thenReturn((ChannelUnion) archivedThread);
        handler.handleArchivedUpdate(archivedEvent);

        ThreadChannel lockedThread = thread(8001L, "thread-flags", true, true);
        ChannelUpdateLockedEvent lockedEvent = mock(ChannelUpdateLockedEvent.class);
        when(lockedEvent.getChannel()).thenReturn((ChannelUnion) lockedThread);
        handler.handleLockedUpdate(lockedEvent);

        Map<String, Object> row = JdbiTestSupport.queryRow(jdbi, """
                SELECT is_archived, is_locked, auto_archive_duration
                FROM threads WHERE ext_id = :extId
                """, "extId", 8001L);

        assertAll(
                () -> assertEquals(1, intValue(row, "is_archived")),
                () -> assertEquals(1, intValue(row, "is_locked")),
                () -> assertEquals(60, intValue(row, "auto_archive_duration")));
    }

    private static ChannelEventHandler channelEventHandler(Jdbi jdbi) {
        ChannelService channelService = new ChannelService();
        inject(channelService, "channelDao", JdbiTestSupport.dao(jdbi, ChannelDao.class));
        inject(channelService, "categoryDao", JdbiTestSupport.dao(jdbi, CategoryDao.class));

        ThreadService threadService = new ThreadService();
        inject(threadService, "threadDao", JdbiTestSupport.dao(jdbi, ThreadDao.class));
        inject(threadService, "forumTagDao", JdbiTestSupport.dao(jdbi, ForumTagDao.class));
        inject(threadService, "threadTagDao", JdbiTestSupport.dao(jdbi, ThreadTagDao.class));
        inject(threadService, "channelService", channelService);

        ChannelEventHandler handler = new ChannelEventHandler();
        handler.channelService = channelService;
        handler.threadService = threadService;
        handler.channelDao = JdbiTestSupport.dao(jdbi, ChannelDao.class);
        handler.categoryDao = JdbiTestSupport.dao(jdbi, CategoryDao.class);
        handler.threadDao = JdbiTestSupport.dao(jdbi, ThreadDao.class);
        return handler;
    }

    private static ThreadChannel thread(long threadExtId, String name, boolean archived, boolean locked) {
        ThreadChannel thread = (ThreadChannel) mock(ChannelUnion.class,
            withSettings().extraInterfaces(ThreadChannel.class));
        IThreadContainerUnion parentChannel = parentChannel(threadExtId + 100L);
        when(thread.getIdLong()).thenReturn(threadExtId);
        when(thread.getName()).thenReturn(name);
        when(thread.getType()).thenReturn(ChannelType.GUILD_PUBLIC_THREAD);
        when(thread.getParentChannel()).thenReturn(parentChannel);
        when(thread.getOwnerIdLong()).thenReturn(9001L);
        when(thread.isArchived()).thenReturn(archived);
        when(thread.isLocked()).thenReturn(locked);
        when(thread.isPinned()).thenReturn(false);
        when(thread.getMessageCount()).thenReturn(12);
        when(thread.getSlowmode()).thenReturn(5);
        when(thread.getAutoArchiveDuration()).thenReturn(AutoArchiveDuration.TIME_1_HOUR);
        when(thread.getTimeCreated()).thenReturn(CREATED_AT);
        when(thread.getAppliedTags()).thenReturn(List.of());
        return thread;
    }

    private static Category category(long extId, String name) {
        Category category = (Category) mock(ChannelUnion.class,
                withSettings().extraInterfaces(Category.class));
        when(category.getIdLong()).thenReturn(extId);
        when(category.getName()).thenReturn(name);
        when(category.getTimeCreated()).thenReturn(CREATED_AT);
        return category;
    }

    private static IThreadContainerUnion parentChannel(long parentExtId) {
        IThreadContainerUnion parent = mock(IThreadContainerUnion.class);
        when(parent.getIdLong()).thenReturn(parentExtId);
        when(parent.getName()).thenReturn("parent-" + parentExtId);
        when(parent.getType()).thenReturn(ChannelType.TEXT);
        when(parent.getTimeCreated()).thenReturn(CREATED_AT);
        return parent;
    }

    private static void inject(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to inject test dependency " + fieldName, e);
        }
    }

    private static int intValue(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }
}