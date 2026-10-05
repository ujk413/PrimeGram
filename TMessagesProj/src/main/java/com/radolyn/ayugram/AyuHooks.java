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
            if (dialogId == 0) {
                // dialogId 0 means the update carried no peer; cannot attribute the row
                return;
            }
            // Goes through the helper rather than reading the raw flag so the
            // "also save for bots" preference is actually honoured.
            if (!AyuConfig.shouldSaveDeletedMessage(account, dialogId)) {
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
            if (!AyuConfig.shouldSaveEditedMessage(account, dialogId)) {
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

    // ------------------------------------------------------------------ misc flags

    /**
     * Whether the app should pretend the account has a subscription.
     *
     * <p>Port of desktop's {@code localPremium}: it unlocks the premium *interface*
     * locally. Server-side features still need a real subscription and will be refused
     * by Telegram, which is exactly how the desktop build behaves.
     */
    public static boolean shouldFakePremium() {
        try {
            AyuConfig.load();
            return AyuConfig.localPremium;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Whether Telegram's sponsored messages must be suppressed.
     *
     * <p>Called from {@code MessagesController.getSponsoredMessages} — the single
     * place a sponsored message object is built, so returning {@code true} there
     * removes them from every surface that asks for them.
     */
    public static boolean shouldDisableAds() {
        try {
            AyuConfig.load();
            return AyuConfig.disableAds;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Whether the content-protection window flags must be dropped, so screenshots
     * of protected chats and no-forward content are allowed.
     *
     * <p>Called from the {@code FlagSecureReason} conditions in {@code ChatActivity},
     * {@code ChatMessageCell} and {@code ProfileActivity}. The passcode screen keeps
     * its own protection on purpose: this is about chat content, not the app lock.
     */
    public static boolean shouldAllowScreenshots() {
        try {
            AyuConfig.load();
            return AyuConfig.showScreenshot;
        } catch (Throwable t) {
            return false;
        }
    }
}
