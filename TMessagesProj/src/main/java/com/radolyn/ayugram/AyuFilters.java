package com.radolyn.ayugram;

import android.text.TextUtils;

import com.radolyn.ayugram.filters.AyuFilterController;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;

/**
 * Enforcement of the AyuGram word / regex filters.
 *
 * <p>Port of {@code FiltersController::filtered} from AyuGramDesktop v7.0.9
 * ({@code ayu/features/filters/filters_controller.cpp}). The desktop effect is a full
 * hide — the matching message element is never laid out, so the message vanishes from
 * the chat and produces no notification; there is no dialog-list or blur behaviour in
 * the reference to reproduce.
 *
 * <p>Rules carried over verbatim from the reference:
 * <ul>
 *   <li>the master switch must be on;</li>
 *   <li>outgoing messages are never filtered ({@code if (item->out()) return false;});</li>
 *   <li>when "apply filters inside chats" is off, only broadcast channels are filtered
 *       ({@code filtersEnabled && (filtersEnabledInChats || peer->isBroadcast())}).</li>
 * </ul>
 *
 * <p>Called from {@code MessagesController.processLoadedMessages} for every message the
 * dialog loads, which is the single point both the cached and the server path go
 * through. Filters therefore apply to a chat when it is loaded; changing them takes
 * effect on the next load of that chat.
 */
public final class AyuFilters {

    private AyuFilters() {
    }

    /** Whether the message must be dropped from the chat. Never throws. */
    public static boolean shouldHideMessage(int account, long dialogId, MessageObject object) {
        try {
            if (object == null || object.messageOwner == null) {
                return false;
            }
            AyuConfig.load();
            if (!AyuConfig.filtersEnabled) {
                return false;
            }
            if (object.isOut()) {
                return false;
            }
            if (!AyuConfig.regexFiltersInChats && !ChatObject.isChannelAndNotMegaGroup(dialogId, account)) {
                return false;
            }
            if (AyuConfig.hideFromBlocked && isFromBlockedPeer(account, object)) {
                return true;
            }
            final String text = object.messageOwner.message;
            if (TextUtils.isEmpty(text)) {
                return false;
            }
            return AyuFilterController.getInstance(account).matches(dialogId, text, false);
        } catch (Throwable t) {
            // a broken filter must never take the chat down
            FileLog.e("ayu: filter check failed", t);
            return false;
        }
    }

    /**
     * Blocked senders, the {@code filterBlocked} half of the desktop reference.
     * {@code MessagesController.blockePeers} is keyed by user id or {@code -chatId},
     * which is exactly what {@link MessageObject#getSenderId()} returns.
     */
    private static boolean isFromBlockedPeer(int account, MessageObject object) {
        final long senderId = object.getSenderId();
        return senderId != 0 && MessagesController.getInstance(account).blockePeers.indexOfKey(senderId) >= 0;
    }
}
