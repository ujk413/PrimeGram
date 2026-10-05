package com.radolyn.ayugram.filters;

import android.text.TextUtils;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * CRUD and matching for {@link AyuFilter}, backed by the retention database.
 *
 * <p>Port of {@code features/filters/filters_controller.cpp} plus the compiled
 * cache from {@code filters_cache_controller.cpp}. The desktop build keeps a
 * compiled-regex cache because Qt's regex engine is slow to construct; the same
 * applies to {@link Pattern}, so compiled patterns are memoised per filter id and
 * invalidated whenever the filter's regex changes.
 *
 * <p>Threading: the compiled-pattern and filter caches are shared mutable state, so
 * every entry point here is guarded by the instance monitor and may be called from
 * any thread. {@link #matches(long, String, boolean)} is pure once the filter list
 * is loaded.
 */
public final class AyuFilterController {

    private static final Map<Integer, AyuFilterController> INSTANCES = new HashMap<>();

    private final int account;
    private final Map<Long, List<AyuFilter>> cache = new HashMap<>();
    private final Map<Long, Pattern> compiled = new HashMap<>();

    private SQLiteDatabase database;
    private boolean opened;

    private AyuFilterController(int account) {
        this.account = account;
    }

    public static synchronized AyuFilterController getInstance(int account) {
        AyuFilterController instance = INSTANCES.get(account);
        if (instance == null) {
            instance = new AyuFilterController(account);
            INSTANCES.put(account, instance);
        }
        return instance;
    }

    // --------------------------------------------------------------- lifecycle

    private synchronized void open() {
        if (opened) {
            return;
        }
        try {
            File dir = ApplicationLoader.getFilesDirFixed();
            if (account != 0) {
                dir = new File(dir, "account" + account + "/");
            }
            if (!dir.exists() && !dir.mkdirs()) {
                FileLog.e("ayu: cannot create filters dir " + dir);
            }
            database = new SQLiteDatabase(new File(dir, "ayugram.db").getPath());
            // Second connection to the same file the retention store writes to, so it
            // needs the same WAL/busy-timeout treatment or concurrent access fails.
            // Mirrors AyuDatabase.open() so both connections agree on the journal mode.
            com.radolyn.ayugram.AyuConfig.load();
            if (com.radolyn.ayugram.AyuConfig.walMode) {
                database.executeFast("PRAGMA journal_mode = WAL").stepThis().dispose();
            }
            database.executeFast("PRAGMA busy_timeout = 3000").stepThis().dispose();
            database.executeFast("CREATE TABLE IF NOT EXISTS filters(id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER, regex TEXT, text TEXT, enabled INTEGER, exclude_out INTEGER)").stepThis().dispose();
            database.executeFast("CREATE INDEX IF NOT EXISTS filters_uid_idx ON filters(uid)").stepThis().dispose();
            opened = true;
        } catch (Exception e) {
            FileLog.e("ayu: filters db open failed", e);
            database = null;
        }
    }

    // -------------------------------------------------------------------- CRUD

    /** All filters for a dialog, loading them on first use. */
    public synchronized List<AyuFilter> getFilters(long dialogId) {
        List<AyuFilter> list = cache.get(dialogId);
        if (list != null) {
            return list;
        }
        list = new ArrayList<>();
        open();
        if (database != null) {
            SQLiteCursor cursor = null;
            try {
                cursor = database.queryFinalized(String.format(
                        "SELECT id, uid, regex, text, enabled, exclude_out FROM filters WHERE uid = %d", dialogId));
                while (cursor.next()) {
                    final AyuFilter filter = new AyuFilter();
                    filter.id = cursor.longValue(0);
                    filter.dialogId = cursor.longValue(1);
                    filter.regex = cursor.stringValue(2);
                    filter.text = cursor.stringValue(3);
                    filter.enabled = cursor.intValue(4) != 0;
                    filter.excludeOutgoing = cursor.intValue(5) != 0;
                    list.add(filter);
                }
            } catch (Exception e) {
                FileLog.e("ayu: getFilters failed", e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
        }
        cache.put(dialogId, list);
        return list;
    }

    /** Inserts or updates a filter and drops any stale compiled pattern. */
    public synchronized void saveFilter(AyuFilter filter) {
        open();
        if (database == null) {
            return;
        }
        SQLitePreparedStatement state = null;
        try {
            if (filter.id == 0) {
                state = database.executeFast("INSERT INTO filters(uid, regex, text, enabled, exclude_out) VALUES(?, ?, ?, ?, ?)");
            } else {
                state = database.executeFast("UPDATE filters SET uid = ?, regex = ?, text = ?, enabled = ?, exclude_out = ? WHERE id = ?");
            }
            state.requery();
            int p = 1;
            state.bindLong(p++, filter.dialogId);
            state.bindString(p++, filter.regex == null ? "" : filter.regex);
            state.bindString(p++, filter.text == null ? "" : filter.text);
            state.bindInteger(p++, filter.enabled ? 1 : 0);
            state.bindInteger(p++, filter.excludeOutgoing ? 1 : 0);
            if (filter.id != 0) {
                state.bindLong(p, filter.id);
            }
            state.step();
            if (filter.id == 0) {
                state.dispose();
                state = null;
                final SQLiteCursor cursor = database.queryFinalized("SELECT last_insert_rowid()");
                if (cursor.next()) {
                    filter.id = cursor.longValue(0);
                }
                cursor.dispose();
            }
        } catch (Exception e) {
            FileLog.e("ayu: saveFilter failed", e);
        } finally {
            if (state != null) {
                state.dispose();
            }
        }
        invalidate(filter.dialogId);
    }

    public synchronized void deleteFilter(AyuFilter filter) {
        open();
        if (database == null) {
            return;
        }
        try {
            database.executeFast("DELETE FROM filters WHERE id = " + filter.id).stepThis().dispose();
        } catch (Exception e) {
            FileLog.e("ayu: deleteFilter failed", e);
        }
        invalidate(filter.dialogId);
    }

    private void invalidate(long dialogId) {
        cache.remove(dialogId);
        compiled.clear();
    }

    // ----------------------------------------------------------------- matching

    /**
     * Whether the message should be hidden.
     *
     * @param dialogId dialog the message belongs to
     * @param text     the message text
     * @param outgoing whether the message is ours
     */
    public synchronized boolean matches(long dialogId, String text, boolean outgoing) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        final List<AyuFilter> filters = getFilters(dialogId);
        for (int i = 0, n = filters.size(); i < n; i++) {
            final AyuFilter filter = filters.get(i);
            if (!filter.enabled || filter.isEmpty()) {
                continue;
            }
            if (filter.excludeOutgoing && outgoing) {
                continue;
            }
            if (filter.text != null && !filter.text.isEmpty()
                    && text.toLowerCase().contains(filter.text.toLowerCase())) {
                return true;
            }
            final Pattern pattern = compiledPattern(filter);
            if (pattern != null && pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /** Validates a regex without saving it, so the editor can show an error early. */
    public boolean isValidRegex(String regex) {
        if (regex == null || regex.isEmpty()) {
            return true;
        }
        try {
            Pattern.compile(regex);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private Pattern compiledPattern(AyuFilter filter) {
        if (filter.regex == null || filter.regex.isEmpty()) {
            return null;
        }
        Pattern pattern = compiled.get(filter.id);
        if (pattern != null) {
            return pattern;
        }
        try {
            pattern = Pattern.compile(filter.regex);
            compiled.put(filter.id, pattern);
            return pattern;
        } catch (PatternSyntaxException e) {
            return null;
        }
    }
}
