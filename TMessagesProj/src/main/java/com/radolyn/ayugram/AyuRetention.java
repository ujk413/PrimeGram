package com.radolyn.ayugram;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Makes retained messages visible inside a chat.
 *
 * <p>Port of the desktop behaviour where a saved deleted message stays in the message
 * list, dimmed, instead of vanishing ({@code HistoryItem::setDeleted} +
 * {@code Element::deletedOpacity} in AyuGramDesktop v7.0.9). On Android the messages
 * are merged into the list the chat is about to insert, so all of Telegram's own
 * grouping, date separators and ordering still apply.
 *
 * <p>Display-only by design: retained objects never enter the message cache, so
 * scrolling, search and read state keep working exactly as before. A live message
 * always wins over its retained copy, so nothing is duplicated once the server sends
 * the message again.
 *
 * <p>Never throws: a failure here must leave the chat list untouched rather than break
 * the screen.
 */
public final class AyuRetention {

    /** How many retained messages are pulled into a chat per load. */
    private static final int RETAINED_LIMIT = 200;

    private AyuRetention() {
    }

    /**
     * Cheap "does this dialog have anything retained" check, used to decide whether the
     * viewer entry belongs in the context menu. A single indexed row is read.
     */
    public static boolean hasRetainedMessages(int account, long dialogId) {
        try {
            if (dialogId == 0) {
                return false;
            }
            AyuConfig.load();
            if (!AyuConfig.saveDeletedMessages) {
                return false;
            }
            return !com.radolyn.ayugram.database.AyuDatabase.getInstance(account).getDeletedMessages(dialogId, 1).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Appends the dialog's retained deleted messages to the freshly loaded list.
     *
     * @param account the Telegram account index
     * @param dialogId dialog the list belongs to
     * @param messArr  the list the chat is about to insert, newest first
     * @param known    ids already present in the chat's dict, or {@code null}
     */
    public static void injectRetainedMessages(int account, long dialogId, ArrayList<MessageObject> messArr, HashMap<Integer, MessageObject> known) {
        try {
            if (messArr == null || dialogId == 0) {
                return;
            }
            AyuConfig.load();
            if (!AyuConfig.saveDeletedMessages) {
                return;
            }
            final ArrayList<com.radolyn.ayugram.database.AyuDatabase.RetainedMessage> rows =
                    com.radolyn.ayugram.database.AyuDatabase.getInstance(account).getDeletedMessages(dialogId, RETAINED_LIMIT);
            if (rows.isEmpty()) {
                return;
            }

            boolean added = false;
            for (int i = 0, n = rows.size(); i < n; i++) {
                final com.radolyn.ayugram.database.AyuDatabase.RetainedMessage row = rows.get(i);
                if (known != null && known.containsKey(row.mid)) {
                    // the live message is already in the chat
                    continue;
                }
                boolean alreadyLoaded = false;
                for (int j = 0, m = messArr.size(); j < m; j++) {
                    if (messArr.get(j).getId() == row.mid) {
                        alreadyLoaded = true;
                        break;
                    }
                }
                if (alreadyLoaded) {
                    continue;
                }
                final TLRPC.Message message = row.toMessage();
                if (message == null) {
                    continue;
                }
                message.dialog_id = dialogId;
                final MessageObject object = new MessageObject(account, message, null, null, true, false, false);
                object.ayuDeleted = true;
                messArr.add(object);
                added = true;
            }

            if (added) {
                // keep the newest-first order the chat expects
                Collections.sort(messArr, (a, b) -> {
                    if (a.messageOwner.date == b.messageOwner.date && a.getId() >= 0 && b.getId() >= 0) {
                        return b.getId() - a.getId();
                    }
                    return b.messageOwner.date - a.messageOwner.date;
                });
            }
        } catch (Throwable t) {
            FileLog.e("ayu: retained message injection failed", t);
        }
    }
}
