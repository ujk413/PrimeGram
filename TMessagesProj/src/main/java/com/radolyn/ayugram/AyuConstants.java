package com.radolyn.ayugram;

/**
 * Static identifiers shared across the AyuGram layer.
 *
 * <p>Kept deliberately tiny: anything that is user-configurable lives in
 * {@link AyuConfig}, anything account-scoped lives in {@code GhostMode}.
 */
public final class AyuConstants {

    private AyuConstants() {
    }

    /** Product name used for on-disk paths and notifications. */
    public static final String APP_NAME = "AyuGram";

    /** Shared-preferences file backing {@link AyuConfig}. */
    public static final String PREFS_CONFIG = "ayugram";

    /** Database file backing the retention store. */
    public static final String RETENTION_DB = "ayugram.db";

    /** Broadcast emitted when ghost mode flips, so open screens can refresh. */
    public static final String ACTION_GHOST_MODE_CHANGED = "com.radolyn.ayugram.GHOST_MODE_CHANGED";

    /** Extra carrying the account index for {@link #ACTION_GHOST_MODE_CHANGED}. */
    public static final String EXTRA_ACCOUNT = "account";
}
