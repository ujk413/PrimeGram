package com.radolyn.ayugram.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.radolyn.ayugram.database.AyuDatabase;

import org.telegram.tgnet.TLRPC;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Read-only viewer for messages the server no longer has.
 *
 * <p>Shows everything {@code AyuDatabase} retained for one dialog: messages that
 * were deleted, and every superseded revision of messages that were edited. This is
 * the screen that makes the retention feature visible; without it the store fills up
 * silently.
 *
 * <p>Deliberately plain — plain text rows rather than Telegram's chat cells. A
 * faithful bubble renderer is the natural next step, but it drags in
 * {@code ChatMessageCell} and the whole theme system, which is a much larger job
 * than the data layer this screen exists to surface.
 */
public class AyuMessageHistoryActivity extends Activity {

    private LinearLayout content;
    private SimpleDateFormat format;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        format = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

        final ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#1a1a1a"));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(content);
        setContentView(scroll);

        final int account = getIntent().getIntExtra("account", 0);
        final long dialogId = getIntent().getLongExtra("dialog_id", 0);

        if (dialogId == 0) {
            header("No dialog selected");
            return;
        }

        header("Retained history");

        final AyuDatabase database = AyuDatabase.getInstance(account);
        final ArrayList<AyuDatabase.RetainedMessage> deleted = database.getDeletedMessages(dialogId, 500);
        final ArrayList<AyuDatabase.RetainedMessage> edited = database.getEditedMessages(dialogId, 500);

        section("Deleted messages (" + deleted.size() + ")");
        if (deleted.isEmpty()) {
            empty("Nothing retained yet.");
        } else {
            for (AyuDatabase.RetainedMessage row : deleted) {
                renderRow(row);
            }
        }

        section("Edited messages (" + edited.size() + ")");
        if (edited.isEmpty()) {
            empty("Nothing retained yet.");
        } else {
            for (AyuDatabase.RetainedMessage row : edited) {
                renderRow(row);
            }
        }
    }

    private void renderRow(AyuDatabase.RetainedMessage row) {
        final TLRPC.Message message = row.toMessage();
        final String text;
        if (message == null) {
            text = "(unreadable)";
        } else if (message.message != null && !message.message.isEmpty()) {
            text = message.message;
        } else if (message.media != null) {
            text = "(media)";
        } else {
            text = "(empty)";
        }

        final TextView meta = new TextView(this);
        meta.setText(format.format(new Date(row.date * 1000L)) + "  ·  id " + row.mid);
        meta.setTextColor(Color.parseColor("#7a7a7a"));
        meta.setTextSize(12);
        meta.setPadding(0, dp(10), 0, dp(2));
        content.addView(meta);

        final TextView body = new TextView(this);
        body.setText(text);
        body.setTextColor(Color.parseColor("#e0e0e0"));
        body.setTextSize(15);
        content.addView(body);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void header(String title) {
        final TextView view = new TextView(this);
        view.setText(title);
        view.setTextColor(Color.WHITE);
        view.setTextSize(22);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, dp(8));
        content.addView(view);
    }

    private void section(String title) {
        final TextView view = new TextView(this);
        view.setText(title);
        view.setTextColor(Color.parseColor("#5aa9e6"));
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(20), 0, dp(6));
        content.addView(view);
    }

    private void empty(String text) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.parseColor("#7a7a7a"));
        view.setTextSize(14);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(view);
    }
}
