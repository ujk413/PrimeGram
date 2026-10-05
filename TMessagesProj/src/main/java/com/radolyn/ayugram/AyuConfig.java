package com.radolyn.ayugram;

import android.content.SharedPreferences;
import android.os.Environment;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;

import java.io.File;

/**
 * Persisted configuration for the AyuGram feature set on Android.
 *
 * <p>Mirrors the desktop {@code AyuSettings} model (AyuGramDesktop v7.0.9,
 * {@code Telegram/SourceFiles/ayu/ayu_settings.h}), minus the purely desktop
 * concerns (window chrome, tray toggles, streamer-mode capture). Visual tweaks
 * that exteraGram already owns stay in {@code com.exteragram.messenger.preferences}.
 *
 * <p>Fields are public and read directly at call sites, matching the convention
 * the rest of the Telegram Android codebase uses for {@code SharedConfig}.
 * Every write goes through a setter so the change is persisted immediately.
 */
public final class AyuConfig {

    private AyuConfig() {
    }

    private static final String PREFS_NAME = "ayugram";
    private static final Object LOCK = new Object();

    private static SharedPreferences preferences;
    private static SharedPreferences.Editor editor;
    private static boolean loaded;

    // ---------------------------------------------------------------- retention

    /** Keep messages the server reports as deleted, so they stay visible locally. */
    public static boolean saveDeletedMessages = true;

    /** Keep the previous revision whenever a message is edited. */
    public static boolean saveMessagesHistory = true;

    /** Whether retention also applies to bots (most people do not want bot spam saved). */
    public static boolean saveForBots = true;

    /** Render retained deleted messages dimmed instead of hiding them outright. */
    public static boolean semiTransparentDeletedMessages = true;

    /** Persist the read timestamp reported by the server. */
    public static boolean saveReadDate = false;

    /** Remember our own last-seen online time locally. */
    public static boolean saveLocalOnline = false;

    /** Icon shown next to a retained message: 0 = none, 1 = cross, 2 = trash. */
    public static int deletedIcon = 1;

    /** Accent override for {@link #deletedIcon}; 0 means follow the theme. */
    public static int deletedIconColor = 0;

    // ------------------------------------------------------------------- media

    /** Mirror media of retained messages into the app's download directory. */
    public static boolean saveMedia = true;
    public static boolean saveMediaInPrivateChats = true;
    public static boolean saveMediaInPrivateChannels = true;
    public static boolean saveMediaInPrivateGroups = true;
    public static boolean saveMediaInPublicChannels = false;
    public static boolean saveMediaInPublicGroups = false;

    /** Byte budget for media mirroring over cellular; 0 disables the cap. */
    public static long saveMediaOnCellularDataLimit = 16L * 1024 * 1024;

    /** Byte budget for media mirroring over Wi-Fi; 0 disables the cap. */
    public static long saveMediaOnWiFiLimit = 64L * 1024 * 1024;

    /** Upper bound for the mirror cache on disk, in bytes. */
    public static int saveMediaMaxCacheSize = Integer.MAX_VALUE;

    // ------------------------------------------------------------------ filters

    /** Master switch for regex / word filters. */
    public static boolean filtersEnabled = false;

    /** Also apply filters inside open chats, not just in the dialog list. */
    public static boolean regexFiltersInChats = false;

    /** Hide messages authored by blocked peers. */
    public static boolean hideFromBlocked = false;

    // --------------------------------------------------------------------- misc

    /** Strip promoted / sponsored messages from the feed. */
    public static boolean disableAds = true;

    /** Unlock premium UI affordances locally, without a real subscription. */
    public static boolean localPremium = false;

    /** Keep the foreground service alive so retention keeps working while backgrounded. */
    public static boolean keepAliveService = true;

    /** Open the retention database in WAL mode (faster, safer on concurrent writes). */
    public static boolean walMode = true;

    /** Allow screenshots of chats the server marks as protected. */
    public static boolean showScreenshot = false;

    /** Always show the inline download button, regardless of auto-download settings. */
    public static boolean forceShowDownloadButtons = false;

    /** Ask the other logged-in accounts before assuming a peer's identity is unknown. */
    public static boolean probeUsingOtherAccounts = true;

    /** Disable hooking into Telegram's internals; debug escape hatch. */
    public static boolean disableHook = false;

    // ------------------------------------------------------------- ghost status

    /** Show a persistent indicator while ghost mode is active. */
    public static boolean displayGhostStatus = false;

    // ------------------------------------------------------------ one-shot flags

    public static boolean sawFirstLaunchAlert = false;
    public static boolean sawLocalPremiumAlert = false;
    public static boolean sawSaveAttachmentsAlert = false;

    // ------------------------------------------------------------- persistence

    public static void load() {
        synchronized (LOCK) {
            if (loaded) {
                return;
            }
            preferences = ApplicationLoader.applicationContext
                    .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE);
            editor = preferences.edit();

            saveDeletedMessages = preferences.getBoolean("saveDeletedMessages", saveDeletedMessages);
            saveMessagesHistory = preferences.getBoolean("saveMessagesHistory", saveMessagesHistory);
            saveForBots = preferences.getBoolean("saveForBots", saveForBots);
            semiTransparentDeletedMessages = preferences.getBoolean("semiTransparentDeletedMessages", semiTransparentDeletedMessages);
            saveReadDate = preferences.getBoolean("saveReadDate", saveReadDate);
            saveLocalOnline = preferences.getBoolean("saveLocalOnline", saveLocalOnline);
            deletedIcon = preferences.getInt("deletedIcon", deletedIcon);
            deletedIconColor = preferences.getInt("deletedIconColor", deletedIconColor);

            saveMedia = preferences.getBoolean("saveMedia", saveMedia);
            saveMediaInPrivateChats = preferences.getBoolean("saveMediaInPrivateChats", saveMediaInPrivateChats);
            saveMediaInPrivateChannels = preferences.getBoolean("saveMediaInPrivateChannels", saveMediaInPrivateChannels);
            saveMediaInPrivateGroups = preferences.getBoolean("saveMediaInPrivateGroups", saveMediaInPrivateGroups);
            saveMediaInPublicChannels = preferences.getBoolean("saveMediaInPublicChannels", saveMediaInPublicChannels);
            saveMediaInPublicGroups = preferences.getBoolean("saveMediaInPublicGroups", saveMediaInPublicGroups);
            saveMediaOnCellularDataLimit = preferences.getLong("saveMediaOnCellularDataLimit", saveMediaOnCellularDataLimit);
            saveMediaOnWiFiLimit = preferences.getLong("saveMediaOnWiFiLimit", saveMediaOnWiFiLimit);
            saveMediaMaxCacheSize = preferences.getInt("saveMediaMaxCacheSize", saveMediaMaxCacheSize);

            filtersEnabled = preferences.getBoolean("filtersEnabled", filtersEnabled);
            regexFiltersInChats = preferences.getBoolean("regexFiltersInChats", regexFiltersInChats);
            hideFromBlocked = preferences.getBoolean("hideFromBlocked", hideFromBlocked);

            disableAds = preferences.getBoolean("disableAds", disableAds);
            localPremium = preferences.getBoolean("localPremium", localPremium);
            keepAliveService = preferences.getBoolean("keepAliveService", keepAliveService);
            walMode = preferences.getBoolean("walMode", walMode);
            showScreenshot = preferences.getBoolean("showScreenshot", showScreenshot);
            forceShowDownloadButtons = preferences.getBoolean("forceShowDownloadButtons", forceShowDownloadButtons);
            probeUsingOtherAccounts = preferences.getBoolean("probeUsingOtherAccounts", probeUsingOtherAccounts);
            disableHook = preferences.getBoolean("disableHook", disableHook);

            displayGhostStatus = preferences.getBoolean("displayGhostStatus", displayGhostStatus);

            sawFirstLaunchAlert = preferences.getBoolean("sawFirstLaunchAlert", sawFirstLaunchAlert);
            sawLocalPremiumAlert = preferences.getBoolean("sawLocalPremiumAlert", sawLocalPremiumAlert);
            sawSaveAttachmentsAlert = preferences.getBoolean("sawSaveAttachmentsAlert", sawSaveAttachmentsAlert);

            loaded = true;
        }
    }

    public static void reload() {
        synchronized (LOCK) {
            loaded = false;
        }
        load();
    }

    public static void save() {
        synchronized (LOCK) {
            editor.putBoolean("saveDeletedMessages", saveDeletedMessages);
            editor.putBoolean("saveMessagesHistory", saveMessagesHistory);
            editor.putBoolean("saveForBots", saveForBots);
            editor.putBoolean("semiTransparentDeletedMessages", semiTransparentDeletedMessages);
            editor.putBoolean("saveReadDate", saveReadDate);
            editor.putBoolean("saveLocalOnline", saveLocalOnline);
            editor.putInt("deletedIcon", deletedIcon);
            editor.putInt("deletedIconColor", deletedIconColor);

            editor.putBoolean("saveMedia", saveMedia);
            editor.putBoolean("saveMediaInPrivateChats", saveMediaInPrivateChats);
            editor.putBoolean("saveMediaInPrivateChannels", saveMediaInPrivateChannels);
            editor.putBoolean("saveMediaInPrivateGroups", saveMediaInPrivateGroups);
            editor.putBoolean("saveMediaInPublicChannels", saveMediaInPublicChannels);
            editor.putBoolean("saveMediaInPublicGroups", saveMediaInPublicGroups);
            editor.putLong("saveMediaOnCellularDataLimit", saveMediaOnCellularDataLimit);
            editor.putLong("saveMediaOnWiFiLimit", saveMediaOnWiFiLimit);
            editor.putInt("saveMediaMaxCacheSize", saveMediaMaxCacheSize);

            editor.putBoolean("filtersEnabled", filtersEnabled);
            editor.putBoolean("regexFiltersInChats", regexFiltersInChats);
            editor.putBoolean("hideFromBlocked", hideFromBlocked);

            editor.putBoolean("disableAds", disableAds);
            editor.putBoolean("localPremium", localPremium);
            editor.putBoolean("keepAliveService", keepAliveService);
            editor.putBoolean("walMode", walMode);
            editor.putBoolean("showScreenshot", showScreenshot);
            editor.putBoolean("forceShowDownloadButtons", forceShowDownloadButtons);
            editor.putBoolean("probeUsingOtherAccounts", probeUsingOtherAccounts);
            editor.putBoolean("disableHook", disableHook);

            editor.putBoolean("displayGhostStatus", displayGhostStatus);

            editor.putBoolean("sawFirstLaunchAlert", sawFirstLaunchAlert);
            editor.putBoolean("sawLocalPremiumAlert", sawLocalPremiumAlert);
            editor.putBoolean("sawSaveAttachmentsAlert", sawSaveAttachmentsAlert);

            editor.apply();
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Where mirrored attachments are written when no per-chat override applies.
     * Sits next to the public Downloads folder rather than inside app-private
     * storage, so the files survive a reinstall.
     */
    public static File getDefaultSavePath() {
        final File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(new File(downloads, AyuConstants.APP_NAME), "AyuGram");
    }

    /** Whether a deleted message from {@code dialogId} should be retained. */
    public static boolean shouldSaveDeletedMessage(int account, long dialogId) {
        if (!saveDeletedMessages) {
            return false;
        }
        if (saveForBots) {
            return true;
        }
        final TLRPC.User user = MessagesController.getInstance(account).getUser(Math.abs(dialogId));
        return user == null || !user.bot;
    }

    /** Whether an edited message from {@code dialogId} should have its history retained. */
    public static boolean shouldSaveEditedMessage(int account, long dialogId) {
        if (!saveMessagesHistory) {
            return false;
        }
        if (saveForBots) {
            return true;
        }
        final TLRPC.User user = MessagesController.getInstance(account).getUser(Math.abs(dialogId));
        return user == null || !user.bot;
    }
}
