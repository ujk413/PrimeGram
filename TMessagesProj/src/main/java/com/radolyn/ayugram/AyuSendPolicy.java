package com.radolyn.ayugram;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.ConnectionsManager;

/**
 * Outgoing-send policy for ghost mode.
 *
 * <p>Port of {@code applyGhostScheduling} from AyuGramDesktop v7.0.9
 * ({@code ayu/utils/telegram_helpers.cpp}): while ghost mode is on, an outgoing
 * message can be forced silent, and pushed into the future as a scheduled message so
 * the peer never sees the moment it was typed in.
 *
 * <p>Called from {@code SendMessagesHelper.sendMessage(SendMessageParams)} — the one
 * entry point every outgoing message goes through.
 *
 * <p>Not ported here: desktop's {@code markReadAfterAction}. Its purpose is to keep
 * the local unread state sane while read receipts are suppressed, and Telegram
 * Android already marks a dialog read locally the moment it is opened, with
 * {@code AyuGhost} dropping the server packet. The behaviour is therefore inherent
 * and the flag has no separate effect to implement.
 */
public final class AyuSendPolicy {

    /** Desktop default: {@code applyGhostScheduling(..., int delaySeconds = 12)}. */
    private static final int GHOST_SCHEDULE_DELAY_SECONDS = 12;

    private AyuSendPolicy() {
    }

    public static void apply(int account, SendMessagesHelper.SendMessageParams params) {
        if (params == null) {
            return;
        }
        try {
            AyuConfig.load();
            final GhostMode ghost = GhostMode.getInstance(account);
            if (!ghost.isGhostModeActive()) {
                return;
            }
            if (ghost.shouldSendWithoutSound()) {
                params.notify = false;
            }
            if (ghost.useScheduledMessages() && params.scheduleDate == 0) {
                params.scheduleDate = ConnectionsManager.getInstance(account).getCurrentTime()
                        + GHOST_SCHEDULE_DELAY_SECONDS;
            }
        } catch (Throwable t) {
            // a failed policy must never block the message itself
            FileLog.e("ayu: ghost send policy failed", t);
        }
    }
}
