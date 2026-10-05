package com.radolyn.ayugram.filters;

/**
 * One regex / word filter, scoped to a single dialog.
 *
 * <p>Port of {@code features/filters/filters_utils.h} from AyuGramDesktop v7.0.9.
 * A filter matches when either the regex hits, or any of the plain-text needles
 * appear as a substring (case-insensitive). {@link #enabled} lets a user keep a
 * filter around without arming it.
 */
public final class AyuFilter {

    /** 0 until the row is written; then the database id. */
    public long id;

    /** Dialog this filter is scoped to ({@code messages_v2.uid}). */
    public long dialogId;

    /** Java regex applied to the message text; may be empty. */
    public String regex = "";

    /** Plain-text needles, matched as substrings; may be empty. */
    public String text = "";

    /** Whether the filter currently hides matching messages. */
    public boolean enabled = true;

    /** Only apply to messages from other people, not our own. */
    public boolean excludeOutgoing = false;

    public AyuFilter() {
    }

    public AyuFilter(long dialogId) {
        this.dialogId = dialogId;
    }

    /** Whether this filter has anything to match on. */
    public boolean isEmpty() {
        return (regex == null || regex.isEmpty()) && (text == null || text.isEmpty());
    }
}
