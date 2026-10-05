package com.radolyn.ayugram;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Transient per-account UI state that must survive a screen rotation but not a
 * process restart.
 *
 * <p>Port of {@code ayu_state.cpp} from AyuGramDesktop v7.0.9. The desktop build
 * keeps a set of locally-hidden messages so a user can mute a message in a chat
 * without deleting it server-side; that set is the core of this class.
 *
 * <p>Unlike {@link AyuConfig}, nothing here is a user preference — it is scratch
 * state, so it lives in memory and is deliberately not part of the settings UI.
 */
public final class AyuState {

    private static final Object LOCK = new Object();

    /** Locally hidden messages, keyed {@code dialogId << 32 | mid}. */
    private static final Set<Long> HIDDEN = Collections.synchronizedSet(new HashSet<>());

    private static SharedPreferences prefs;

    private AyuState() {
    }

    private static SharedPreferences prefs() {
        if (prefs == null) {
            prefs = ApplicationLoader.applicationContext
                    .getSharedPreferences("ayugram_state", Context.MODE_PRIVATE);
            load();
        }
        return prefs;
    }

    private static long key(long dialogId, int mid) {
        return (dialogId << 32) | (mid & 0xffffffffL);
    }

    private static void load() {
        synchronized (LOCK) {
            final Set<String> raw = prefs.getStringSet("hidden", Collections.emptySet());
            HIDDEN.clear();
            for (String value : raw) {
                try {
                    HIDDEN.add(Long.parseLong(value));
                } catch (NumberFormatException ignored) {
                    // a corrupt entry should never block startup
                }
            }
        }
    }

    private static void persist() {
        synchronized (LOCK) {
            final Set<String> raw = new HashSet<>();
            synchronized (HIDDEN) {
                for (Long value : HIDDEN) {
                    raw.add(Long.toString(value));
                }
            }
            prefs().edit().putStringSet("hidden", raw).apply();
        }
    }

    /** Marks a message as locally hidden so it is not painted. */
    public static void hide(long dialogId, int mid) {
        prefs();
        if (HIDDEN.add(key(dialogId, mid))) {
            persist();
        }
    }

    /** Reverses {@link #hide(long, int)}. */
    public static void unhide(long dialogId, int mid) {
        prefs();
        if (HIDDEN.remove(key(dialogId, mid))) {
            persist();
        }
    }

    /** Whether a message is locally hidden. */
    public static boolean isHidden(long dialogId, int mid) {
        prefs();
        return HIDDEN.contains(key(dialogId, mid));
    }

    /** Drops every local hide for one dialog. */
    public static void clear(long dialogId) {
        prefs();
        synchronized (HIDDEN) {
            HIDDEN.removeIf(value -> (value >> 32) == dialogId);
        }
        persist();
    }

    /** Drops every local hide across all dialogs. */
    public static void clearAll() {
        prefs();
        HIDDEN.clear();
        persist();
    }
}
