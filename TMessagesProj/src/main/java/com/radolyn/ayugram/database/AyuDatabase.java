package com.radolyn.ayugram.database;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.NativeByteBuffer;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Local retention store for messages the server no longer keeps.
 *
 * <p>Port of the {@code data/ayu_database.cpp} + {@code data/messages_storage.cpp}
 * pair from AyuGramDesktop v7.0.9. Where the desktop build talks to SQLite through
 * its own thin wrapper, this reuses Telegram Android's native {@link SQLiteDatabase}
 * so the app gains no new dependency.
 *
 * <p>Two tables, both keyed by {@code (mid, uid)} so a message retained in two
 * dialogs (a forward chain, a saved copy) is stored once per dialog:
 *
 * <ul>
 *   <li>{@code deleted_messages} — rows copied out of {@code messages_v2} the
 *       instant before Telegram erases them;</li>
 *   <li>{@code edited_messages} — every superseded revision, one row per edit.</li>
 * </ul>
 *
 * <p>Rows are addressed by the raw serialized {@code TLRPC.Message} blob, exactly
 * like {@code messages_v2.data}. Nothing is re-parsed on write, so a retained
 * message survives schema drift in the TL layer.
 *
 * <p><b>Threading.</b> Retention writes happen on Telegram's storage thread
 * ({@code MessagesStorage.getStorageQueue()}), but the read-only screens and
 * {@code AyuFilterController} reach the same file from other threads, so every
 * public method here is guarded by the instance monitor. That serialises access to
 * the single native handle; it does not make a query on the UI thread cheap, so
 * callers that touch this from an Activity must still move the call off-thread.
 */
public final class AyuDatabase {

    private static final Map<Integer, AyuDatabase> INSTANCES = new HashMap<>();

    private final int account;
    private SQLiteDatabase database;
    private boolean opened;

    private AyuDatabase(int account) {
        this.account = account;
    }

    public static synchronized AyuDatabase getInstance(int account) {
        AyuDatabase instance = INSTANCES.get(account);
        if (instance == null) {
            instance = new AyuDatabase(account);
            INSTANCES.put(account, instance);
        }
        return instance;
    }

    // --------------------------------------------------------------- lifecycle

    public synchronized void open() {
        if (opened) {
            return;
        }
        try {
            File dir = ApplicationLoader.getFilesDirFixed();
            if (account != 0) {
                dir = new File(dir, "account" + account + "/");
            }
            if (!dir.exists() && !dir.mkdirs()) {
                FileLog.e("ayu: cannot create retention dir " + dir);
            }
            database = new SQLiteDatabase(new File(dir, "ayugram.db").getPath());
            // AyuFilterController opens a second connection to this same file, and the
            // read-only screens touch it from another thread. Without WAL plus a busy
            // timeout those concurrent accesses fail with SQLITE_BUSY instead of waiting.
            com.radolyn.ayugram.AyuConfig.load();
            if (com.radolyn.ayugram.AyuConfig.walMode) {
                database.executeFast("PRAGMA journal_mode = WAL").stepThis().dispose();
            }
            database.executeFast("PRAGMA busy_timeout = 3000").stepThis().dispose();
            createTables();
            opened = true;
        } catch (SQLiteException e) {
            FileLog.e("ayu: retention db open failed", e);
            database = null;
        }
    }

    private void createTables() throws SQLiteException {
        database.executeFast("CREATE TABLE IF NOT EXISTS deleted_messages(mid INTEGER, uid INTEGER, date INTEGER, data BLOB, saved_at INTEGER, PRIMARY KEY(mid, uid))").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS deleted_uid_idx ON deleted_messages(uid, mid)").stepThis().dispose();

        database.executeFast("CREATE TABLE IF NOT EXISTS edited_messages(row_id INTEGER PRIMARY KEY AUTOINCREMENT, mid INTEGER, uid INTEGER, date INTEGER, data BLOB, saved_at INTEGER)").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS edited_uid_idx ON edited_messages(uid, mid)").stepThis().dispose();

        database.executeFast("CREATE TABLE IF NOT EXISTS deleted_reactions(row_id INTEGER PRIMARY KEY AUTOINCREMENT, mid INTEGER, uid INTEGER, date INTEGER, reactions BLOB)").stepThis().dispose();
        database.executeFast("CREATE INDEX IF NOT EXISTS reactions_uid_idx ON deleted_reactions(uid, mid)").stepThis().dispose();
    }

    public synchronized void close() {
        if (database != null) {
            database.close();
            database = null;
        }
        opened = false;
    }

    // --------------------------------------------------------------- retention

    /**
     * Copies the given message ids out of the live {@code messages_v2} table into
     * the retention store, before Telegram deletes them.
     *
     * <p>Called from {@code MessagesStorage.markMessagesAsDeletedInternal} with the
     * storage thread's own database handle, so the read happens inside the same
     * transaction window as the delete.
     *
     * @param source   the live cache database owned by {@code MessagesStorage}
     * @param dialogId the dialog the messages belong to ({@code messages_v2.uid})
     * @param ids      message ids about to be erased
     */
    public synchronized void retainDeleted(SQLiteDatabase source, long dialogId, ArrayList<Integer> ids) {
        if (source == null || ids == null || ids.isEmpty()) {
            return;
        }
        open();
        if (database == null) {
            return;
        }

        SQLiteCursor cursor = null;
        SQLitePreparedStatement state = null;
        try {
            final String joined = TextUtils.join(",", ids);
            cursor = source.queryFinalized(String.format(
                    "SELECT mid, uid, date, data FROM messages_v2 WHERE uid = %d AND mid IN(%s)",
                    dialogId, joined));

            state = database.executeFast("REPLACE INTO deleted_messages VALUES(?, ?, ?, ?, ?)");
            final long now = System.currentTimeMillis() / 1000L;

            while (cursor.next()) {
                final int mid = cursor.intValue(0);
                final long uid = cursor.longValue(1);
                final int date = cursor.intValue(2);
                final NativeByteBuffer blob = cursor.byteBufferValue(3);
                if (blob == null) {
                    continue;
                }
                final byte[] bytes = toBytes(blob);
                blob.reuse();

                state.requery();
                state.bindInteger(1, mid);
                state.bindLong(2, uid);
                state.bindInteger(3, date);
                state.bindByteBuffer(4, ByteBuffer.wrap(bytes));
                state.bindLong(5, now);
                state.step();
            }
        } catch (Exception e) {
            FileLog.e("ayu: retainDeleted failed", e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
            if (state != null) {
                state.dispose();
            }
        }
    }

    /**
     * Stores one superseded revision of a message. Called for every edit, so a
     * message edited five times yields five rows, newest last.
     */
    public synchronized void retainEdited(long dialogId, int mid, int date, NativeByteBuffer blob) {
        if (blob == null) {
            return;
        }
        open();
        if (database == null) {
            return;
        }
        SQLitePreparedStatement state = null;
        try {
            final byte[] bytes = toBytes(blob);
            state = database.executeFast("INSERT INTO edited_messages(mid, uid, date, data, saved_at) VALUES(?, ?, ?, ?, ?)");
            state.requery();
            state.bindInteger(1, mid);
            state.bindLong(2, dialogId);
            state.bindInteger(3, date);
            state.bindByteBuffer(4, ByteBuffer.wrap(bytes));
            state.bindLong(5, System.currentTimeMillis() / 1000L);
            state.step();
        } catch (Exception e) {
            FileLog.e("ayu: retainEdited failed", e);
        } finally {
            if (state != null) {
                state.dispose();
            }
        }
    }

    // ------------------------------------------------------------------ queries

    /** Raw retained blobs for a dialog, newest first. Caller deserializes. */
    public synchronized ArrayList<RetainedMessage> getDeletedMessages(long dialogId, int limit) {
        return queryBlobs("deleted_messages", dialogId, limit);
    }

    /** Raw superseded blobs for a dialog, newest first. Caller deserializes. */
    public synchronized ArrayList<RetainedMessage> getEditedMessages(long dialogId, int limit) {
        return queryBlobs("edited_messages", dialogId, limit);
    }

    /** Every superseded revision of a single message, oldest first. */
    public synchronized ArrayList<RetainedMessage> getEditsFor(int mid, long dialogId) {
        open();
        final ArrayList<RetainedMessage> result = new ArrayList<>();
        if (database == null) {
            return result;
        }
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(String.format(
                    "SELECT mid, uid, date, data FROM edited_messages WHERE uid = %d AND mid = %d ORDER BY row_id ASC",
                    dialogId, mid));
            while (cursor.next()) {
                result.add(readRow(cursor));
            }
        } catch (Exception e) {
            FileLog.e("ayu: getEditsFor failed", e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return result;
    }

    private ArrayList<RetainedMessage> queryBlobs(String table, long dialogId, int limit) {
        open();
        final ArrayList<RetainedMessage> result = new ArrayList<>();
        if (database == null) {
            return result;
        }
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized(String.format(
                    "SELECT mid, uid, date, data FROM %s WHERE uid = %d ORDER BY mid DESC LIMIT %d",
                    table, dialogId, limit));
            while (cursor.next()) {
                result.add(readRow(cursor));
            }
        } catch (Exception e) {
            FileLog.e("ayu: query " + table + " failed", e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return result;
    }

    private RetainedMessage readRow(SQLiteCursor cursor) throws SQLiteException {
        final RetainedMessage row = new RetainedMessage();
        row.mid = cursor.intValue(0);
        row.dialogId = cursor.longValue(1);
        row.date = cursor.intValue(2);
        final NativeByteBuffer blob = cursor.byteBufferValue(3);
        if (blob != null) {
            row.data = toBytes(blob);
            blob.reuse();
        }
        return row;
    }

    /** Wipes everything retained for this account. */
    public synchronized void clear() {
        open();
        if (database == null) {
            return;
        }
        try {
            database.executeFast("DELETE FROM deleted_messages").stepThis().dispose();
            database.executeFast("DELETE FROM edited_messages").stepThis().dispose();
            database.executeFast("DELETE FROM deleted_reactions").stepThis().dispose();
        } catch (Exception e) {
            FileLog.e("ayu: clear failed", e);
        }
    }

    /** Row counts, for the settings screen. */
    public synchronized int getDeletedCount() {
        return count("deleted_messages");
    }

    public synchronized int getEditedCount() {
        return count("edited_messages");
    }

    private int count(String table) {
        open();
        if (database == null) {
            return 0;
        }
        SQLiteCursor cursor = null;
        try {
            cursor = database.queryFinalized("SELECT COUNT(*) FROM " + table);
            if (cursor.next()) {
                return cursor.intValue(0);
            }
        } catch (Exception e) {
            FileLog.e("ayu: count " + table + " failed", e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ helpers

    /** Copies a native blob buffer into a plain array; the caller owns the copy. */
    private static byte[] toBytes(NativeByteBuffer blob) {
        final ByteBuffer buffer = blob.buffer;
        buffer.position(0);
        final byte[] bytes = new byte[buffer.limit()];
        buffer.get(bytes);
        return bytes;
    }

    /** One retained message, still serialized. */
    public static final class RetainedMessage {
        public int mid;
        public long dialogId;
        public int date;
        public byte[] data;

        /** Deserializes the stored blob back into a TL message. */
        public org.telegram.tgnet.TLRPC.Message toMessage() {
            if (data == null) {
                return null;
            }
            NativeByteBuffer buffer = null;
            try {
                buffer = new NativeByteBuffer(data.length + 4);
                buffer.writeInt32(data.length);
                buffer.writeBytes(data);
                buffer.rewind();
                return org.telegram.tgnet.TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(false), false);
            } catch (Exception e) {
                FileLog.e("ayu: retained message deserialize failed", e);
                return null;
            } finally {
                if (buffer != null) {
                    buffer.reuse();
                }
            }
        }
    }
}
