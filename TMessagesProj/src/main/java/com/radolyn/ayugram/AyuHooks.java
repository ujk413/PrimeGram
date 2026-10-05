package com.radolyn.ayugram;

import org.telegram.SQLite.SQLiteDatabase;

import java.util.ArrayList;

/**
 * The seam between Telegram's own code and the AyuGram retention layer.
 *
 * <p>Kept as one tiny class on purpose: every call site inside
 * {@code org.telegram.*} that needs to notify AyuGram goes through a static
 * method here. That keeps the upstream diff to a single line per hook, which is
 * what makes future merges of the official Telegram source survivable.
 *
 * <p>Nothing in this class throws. A failure in retention must never take down
 * message delivery, so every entry point is defensive and logs instead.
 */
public final class AyuHooks {

    private AyuHooks() {
    }

    /**
     * Called from {@code MessagesStorage.markMessagesAsDeletedInternal} right
     * before the rows are erased.
     *
     * @param account  the Telegram account index owning the storage
     * @param source   the live cache database, so the blobs can be read in place
     * @param dialogId dialog id ({@code messages_v2.uid})
     * @param ids      message ids about to be deleted
     * @param mode     storage mode; only regular deletions (0) are retained
     */
    public static void onMessagesDeleted(int account, SQLiteDatabase source, long dialogId, ArrayList<Integer> ids, int mode) {
        if (mode != 0) {
            // scheduled / quick-reply / welcome-message churn is not a "deleted message"
            return;
        }
        try {
            AyuConfig.load();
            if (!AyuConfig.saveDeletedMessages) {
                return;
            }
            if (dialogId == 0) {
                // dialogId 0 means the update carried no peer; cannot attribute the row
                return;
            }
            AyuDatabaseHolder.get(account).retainDeleted(source, dialogId, ids);
        } catch (Throwable t) {
            org.telegram.messenger.FileLog.e("ayu: onMessagesDeleted hook failed", t);
        }
    }

    /**
     * Called when a message is about to be overwritten by an edit, carrying the
     * pre-edit revision.
     */
    public static void onMessageEdited(int account, long dialogId, int mid, int date, org.telegram.tgnet.NativeByteBuffer oldBlob) {
        try {
            AyuConfig.load();
            if (!AyuConfig.saveMessagesHistory) {
                return;
            }
            AyuDatabaseHolder.get(account).retainEdited(dialogId, mid, date, oldBlob);
        } catch (Throwable t) {
            org.telegram.messenger.FileLog.e("ayu: onMessageEdited hook failed", t);
        }
    }

    /** Convenience indirection so the hook never imports the database package directly. */
    private static final class AyuDatabaseHolder {
        static com.radolyn.ayugram.database.AyuDatabase get(int account) {
            return com.radolyn.ayugram.database.AyuDatabase.getInstance(account);
        }
    }
}
