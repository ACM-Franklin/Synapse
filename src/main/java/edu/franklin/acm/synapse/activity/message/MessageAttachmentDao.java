package edu.franklin.acm.synapse.activity.message;

import java.util.List;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindMethods;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

/**
 * Writes message attachment rows. When a message is upserted, its old
 * attachments are deleted and re-inserted to keep the set current.
 */
public interface MessageAttachmentDao {

    @SqlUpdate("""
        INSERT INTO message_attachments (
            message_id, ext_id, filename, description,
            content_type, size, width, height, duration_secs
        ) VALUES (
            :messageId, :extId, :filename, :description,
            :contentType, :size, :width, :height, :durationSecs
        )
        ON CONFLICT (ext_id) DO NOTHING
        """)
    void insert(@BindMethods MessageAttachment attachment);

    @SqlBatch("""
        INSERT INTO message_attachments (
            message_id, ext_id, filename, description,
            content_type, size, width, height, duration_secs
        ) VALUES (
            :messageId, :extId, :filename, :description,
            :contentType, :size, :width, :height, :durationSecs
        )
        ON CONFLICT (ext_id) DO NOTHING
        """)
    void insertBatch(@BindMethods List<MessageAttachment> attachments);

    @SqlUpdate("""
        DELETE FROM message_attachments
        WHERE message_id = :messageId
        """)
    void deleteByMessageId(@Bind("messageId") long messageId);
}
