package com.radolyn.ayugram;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
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
            if (!ghost.sendOnlinePackets() && isStatusRequest(request)) {
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
        if (request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents) {
            return new TLRPC.TL_messages_affectedMessages();
        }
        // channels.readHistory, messages.readDiscussion, stories.readStories and
        // account.updateStatus all answer with a bare Bool.
        return new TLRPC.TL_boolTrue();
    }

    // ------------------------------------------------------------------ shapes

    private static boolean isReadRequest(TLObject request) {
        return request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents;
    }

    private static boolean isStatusRequest(TLObject request) {
        return request instanceof TL_account.updateStatus;
    }

    private static boolean isStoriesReadRequest(TLObject request) {
        return request instanceof TL_stories.TL_stories_readStories
                || request instanceof TL_stories.TL_stories_incrementStoryViews;
    }
}
