package com.radolyn.ayugram;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

/**
 * Network-layer enforcement of ghost mode.
 *
 * <p>Port of the request interception in AyuGramDesktop's {@code ayu_worker.cpp}
 * and the {@code MTP::} suppression hooks. Instead of rewriting every call site
 * that reports a read or an online status, the check lives at the single choke
 * point every outgoing request passes through —
 * {@code ConnectionsManager.sendRequestInternal}.
 *
 * <p>When a request is suppressed we do not silently drop it. Dropping would leave
 * the caller's completion callback unfired and can wedge UI that waits on it, so we
 * hand back the same response the server would have produced and let the caller
 * proceed as if it succeeded. Local read state is still tracked; only the server is
 * kept in the dark.
 */
public final class AyuGhost {

    private AyuGhost() {
    }

    /**
     * Whether the request must not leave the device.
     *
     * @param account Telegram account index
     * @param request the outgoing TL request
     */
    public static boolean shouldSuppress(int account, TLObject request) {
        if (request == null) {
            return false;
        }
        try {
            AyuConfig.load();
            final GhostMode ghost = GhostMode.getInstance(account);
            if (!ghost.isGhostModeActive()) {
                return false;
            }
            if (!ghost.sendReadMessages() && isReadRequest(request)) {
                return true;
            }
            if (!ghost.sendReadStories() && isStoriesReadRequest(request)) {
                return true;
            }
            if (!ghost.sendUploadProgress() && isSendProgressRequest(request)) {
                // Desktop gates its whole SendProgressManager here, which covers typing,
                // recording and upload-progress actions alike.
                return true;
            }
            if (!ghost.sendOnlinePackets() && isStatusRequest(request)) {
                // The offline packet is the one that resets a stale "online" flag on the
                // server, so the user can let it through while online packets stay hidden.
                if (ghost.sendOfflinePacketAfterOnline() && isOfflineStatus(request)) {
                    return false;
                }
                return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The response to fabricate for a suppressed request, matching the type the
     * server would have returned so callers can cast it safely.
     */
    public static TLObject syntheticResponse(TLObject request) {
        // The fabricated object must match the type the request's own
        // deserializeResponse() would have produced, or the first caller that casts
        // the response throws. Checked against each class in org.telegram.tgnet.
        if (request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_messages_readMessageContents) {
            return new TLRPC.TL_messages_affectedMessages();
        }
        if (request instanceof TL_stories.TL_stories_readStories) {
            // stories.readStories answers with Vector<int>, not a Bool.
            return new Vector<Vector.Int>(Vector.Int::TLDeserialize);
        }
        // channels.readHistory, channels.readMessageContents, messages.readDiscussion,
        // messages.readSavedHistory, messages.readEncryptedHistory, messages.setTyping,
        // messages.setEncryptedTyping, stories.incrementStoryViews and account.updateStatus
        // all answer with a bare Bool.
        return new TLRPC.TL_boolTrue();
    }

    /**
     * Whether read receipts inside a secret chat must be withheld.
     *
     * <p>Secret-chat receipts never travel as a plain TL request — they are
     * {@code decryptedMessageActionReadMessages} service messages — so they cannot be
     * caught by {@link #shouldSuppress} and get their own gate at the send site.
     */
    public static boolean shouldSuppressSecretReadReceipt(int account) {
        try {
            AyuConfig.load();
            final GhostMode ghost = GhostMode.getInstance(account);
            return ghost.isGhostModeActive() && !ghost.sendReadMessages();
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ shapes

    private static boolean isReadRequest(TLObject request) {
        return request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents
                || request instanceof TLRPC.TL_messages_readSavedHistory
                || request instanceof TLRPC.TL_messages_readEncryptedHistory;
    }

    private static boolean isStatusRequest(TLObject request) {
        return request instanceof TL_account.updateStatus;
    }

    /** True for the {@code offline = true} flavour of {@code account.updateStatus}. */
    private static boolean isOfflineStatus(TLObject request) {
        return request instanceof TL_account.updateStatus && ((TL_account.updateStatus) request).offline;
    }

    /** Typing / recording / upload-progress actions, all sent as {@code messages.setTyping}. */
    private static boolean isSendProgressRequest(TLObject request) {
        return request instanceof TLRPC.TL_messages_setTyping
                || request instanceof TLRPC.TL_messages_setEncryptedTyping;
    }

    private static boolean isStoriesReadRequest(TLObject request) {
        return request instanceof TL_stories.TL_stories_readStories
                || request instanceof TL_stories.TL_stories_incrementStoryViews;
    }
}
