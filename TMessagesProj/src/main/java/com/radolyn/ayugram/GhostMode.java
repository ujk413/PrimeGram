package com.radolyn.ayugram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-account ghost mode state.
 *
 * <p>Direct port of {@code GhostModeAccountSettings} from AyuGramDesktop v7.0.9
 * ({@code Telegram/SourceFiles/ayu/ayu_settings.h}). Every account keeps its own
 * set of toggles, because people routinely run a "public" account and a "lurker"
 * account side by side.
 *
 * <p>Two layers exist on purpose:
 * <ul>
 *   <li>the plain flags — what the user picked;</li>
 *   <li>the {@code *Locked} flags — hard overrides the app enforces regardless of
 *       what the user picked, used while a story is open or while a scheduled
 *       send is mid-flight.</li>
 * </ul>
 *
 * <p>The desktop build persists this through nlohmann/json; here it is flat
 * SharedPreferences keys prefixed with the account index.
 */
public final class GhostMode {

    /** What the desktop build calls {@code SendWithoutSoundOption}. */
    public enum SendWithoutSound {
        NEVER,
        IN_GHOST_MODE,
        ALWAYS
    }

    private static final Map<Integer, GhostMode> INSTANCES = new HashMap<>();

    private final int account;
    private final SharedPreferences prefs;

    private boolean ghostModeActive;
    private boolean sendReadMessages = true;
    private boolean sendReadStories = true;
    private boolean sendOnlinePackets = true;
    private boolean sendUploadProgress = true;
    private boolean sendOfflinePacketAfterOnline = false;
    private boolean markReadAfterAction = true;
    private boolean useScheduledMessages = false;
    private SendWithoutSound sendWithoutSound = SendWithoutSound.NEVER;
    private boolean suggestGhostModeBeforeViewingStory = true;

    private boolean sendReadMessagesLocked = false;
    private boolean sendReadStoriesLocked = false;
    private boolean sendOnlinePacketsLocked = false;
    private boolean sendUploadProgressLocked = false;
    private boolean sendOfflinePacketAfterOnlineLocked = false;

    private GhostMode(int account) {
        this.account = account;
        this.prefs = ApplicationLoader.applicationContext
                .getSharedPreferences(AyuConstants.PREFS_CONFIG, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized GhostMode getInstance(int account) {
        GhostMode instance = INSTANCES.get(account);
        if (instance == null) {
            instance = new GhostMode(account);
            INSTANCES.put(account, instance);
        }
        return instance;
    }

    private String key(String name) {
        return "ghost_" + account + "_" + name;
    }

    public void load() {
        ghostModeActive = prefs.getBoolean(key("active"), false);
        sendReadMessages = prefs.getBoolean(key("sendReadMessages"), true);
        sendReadStories = prefs.getBoolean(key("sendReadStories"), true);
        sendOnlinePackets = prefs.getBoolean(key("sendOnlinePackets"), true);
        sendUploadProgress = prefs.getBoolean(key("sendUploadProgress"), true);
        sendOfflinePacketAfterOnline = prefs.getBoolean(key("sendOfflinePacketAfterOnline"), false);
        markReadAfterAction = prefs.getBoolean(key("markReadAfterAction"), true);
        useScheduledMessages = prefs.getBoolean(key("useScheduledMessages"), false);
        sendWithoutSound = SendWithoutSound.values()[
                prefs.getInt(key("sendWithoutSound"), SendWithoutSound.NEVER.ordinal())];
        suggestGhostModeBeforeViewingStory = prefs.getBoolean(key("suggestBeforeStory"), true);

        sendReadMessagesLocked = prefs.getBoolean(key("sendReadMessagesLocked"), false);
        sendReadStoriesLocked = prefs.getBoolean(key("sendReadStoriesLocked"), false);
        sendOnlinePacketsLocked = prefs.getBoolean(key("sendOnlinePacketsLocked"), false);
        sendUploadProgressLocked = prefs.getBoolean(key("sendUploadProgressLocked"), false);
        sendOfflinePacketAfterOnlineLocked = prefs.getBoolean(key("sendOfflinePacketAfterOnlineLocked"), false);
    }

    private void put(String name, boolean value) {
        prefs.edit().putBoolean(key(name), value).apply();
    }

    // ------------------------------------------------------------------ state

    public boolean isGhostModeActive() {
        return ghostModeActive;
    }

    public void setGhostModeEnabled(boolean value) {
        ghostModeActive = value;
        put("active", value);
    }

    // ------------------------------------------------------------------ flags

    public boolean sendReadMessages() {
        return sendReadMessages && !sendReadMessagesLocked;
    }

    public boolean sendReadStories() {
        return sendReadStories && !sendReadStoriesLocked;
    }

    public boolean sendOnlinePackets() {
        return sendOnlinePackets && !sendOnlinePacketsLocked;
    }

    public boolean sendUploadProgress() {
        return sendUploadProgress && !sendUploadProgressLocked;
    }

    public boolean sendOfflinePacketAfterOnline() {
        return sendOfflinePacketAfterOnline || sendOfflinePacketAfterOnlineLocked;
    }

    public boolean markReadAfterAction() {
        return markReadAfterAction;
    }

    public boolean useScheduledMessages() {
        return ghostModeActive && useScheduledMessages;
    }

    public SendWithoutSound sendWithoutSound() {
        return sendWithoutSound;
    }

    /** Whether the current send should be silenced, given the selected policy. */
    public boolean shouldSendWithoutSound() {
        switch (sendWithoutSound) {
            case ALWAYS:
                return true;
            case IN_GHOST_MODE:
                return ghostModeActive;
            case NEVER:
            default:
                return false;
        }
    }

    public boolean suggestGhostModeBeforeViewingStory() {
        return suggestGhostModeBeforeViewingStory;
    }

    // ------------------------------------------------------------- mutations

    public void setSendReadMessages(boolean value) {
        sendReadMessages = value;
        put("sendReadMessages", value);
    }

    public void setSendReadStories(boolean value) {
        sendReadStories = value;
        put("sendReadStories", value);
    }

    public void setSendOnlinePackets(boolean value) {
        sendOnlinePackets = value;
        put("sendOnlinePackets", value);
    }

    public void setSendUploadProgress(boolean value) {
        sendUploadProgress = value;
        put("sendUploadProgress", value);
    }

    public void setSendOfflinePacketAfterOnline(boolean value) {
        sendOfflinePacketAfterOnline = value;
        put("sendOfflinePacketAfterOnline", value);
    }

    public void setMarkReadAfterAction(boolean value) {
        markReadAfterAction = value;
        put("markReadAfterAction", value);
    }

    public void setUseScheduledMessages(boolean value) {
        useScheduledMessages = value;
        put("useScheduledMessages", value);
    }

    public void setSendWithoutSound(SendWithoutSound value) {
        sendWithoutSound = value;
        prefs.edit().putInt(key("sendWithoutSound"), value.ordinal()).apply();
    }

    public void setSuggestGhostModeBeforeViewingStory(boolean value) {
        suggestGhostModeBeforeViewingStory = value;
        put("suggestBeforeStory", value);
    }

    // --------------------------------------------------------- locked overrides

    public void setSendReadMessagesLocked(boolean value) {
        sendReadMessagesLocked = value;
        put("sendReadMessagesLocked", value);
    }

    public void setSendReadStoriesLocked(boolean value) {
        sendReadStoriesLocked = value;
        put("sendReadStoriesLocked", value);
    }

    public void setSendOnlinePacketsLocked(boolean value) {
        sendOnlinePacketsLocked = value;
        put("sendOnlinePacketsLocked", value);
    }

    public void setSendUploadProgressLocked(boolean value) {
        sendUploadProgressLocked = value;
        put("sendUploadProgressLocked", value);
    }

    public void setSendOfflinePacketAfterOnlineLocked(boolean value) {
        sendOfflinePacketAfterOnlineLocked = value;
        put("sendOfflinePacketAfterOnlineLocked", value);
    }
}
