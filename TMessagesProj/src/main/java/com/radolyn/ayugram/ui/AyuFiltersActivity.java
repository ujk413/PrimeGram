package com.radolyn.ayugram.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.radolyn.ayugram.AyuConfig;
import com.radolyn.ayugram.filters.AyuFilter;
import com.radolyn.ayugram.filters.AyuFilterController;

import java.util.List;

/**
 * Filter manager for one dialog.
 *
 * <p>AyuGram filters are per-dialog on Android ({@link AyuFilter#dialogId} is required),
 * so this screen is opened from the chat it belongs to rather than from the settings
 * list. Every edit is written straight through to the filter controller, which is the
 * same store the message path consults, so a change takes effect on the next load of
 * the chat.
 *
 * <p>Built programmatically like the rest of the AyuGram screens: no XML that could
 * collide with upstream, and no new dependencies.
 */
public class AyuFiltersActivity extends Activity {

    private LinearLayout content;
    private int account;
    private long dialogId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#1a1a1a"));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(32));
        scroll.addView(content);
        setContentView(scroll);

        account = getIntent().getIntExtra("account", 0);
        dialogId = getIntent().getLongExtra("dialog_id", 0);

        header("Message filters");
        info("A message matching any enabled filter is hidden from this chat. "
                + "Outgoing messages are never filtered.");

        if (dialogId == 0) {
            info("No dialog selected.");
            return;
        }

        final Button add = new Button(this);
        add.setText("Add filter");
        add.setOnClickListener(v -> editFilter(null));
        content.addView(add);

        reload();
    }

    // ------------------------------------------------------------------- data

    private void reload() {
        new Thread(() -> {
            final List<AyuFilter> filters = AyuFilterController.getInstance(account).getFilters(dialogId);
            final AyuFilter[] snapshot = filters.toArray(new AyuFilter[0]);
            runOnUiThread(() -> render(snapshot));
        }, "ayugram-filters").start();
    }

    private void render(AyuFilter[] filters) {
        // drop everything after the "add" button, then rebuild
        while (content.getChildCount() > 3) {
            content.removeViewAt(content.getChildCount() - 1);
        }
        if (filters.length == 0) {
            info("No filters in this chat yet.");
            return;
        }
        for (final AyuFilter filter : filters) {
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));

            final TextView title = new TextView(this);
            final String what = filter.regex != null && !filter.regex.isEmpty() ? "regex: " + filter.regex : "text: " + filter.text;
            title.setText((filter.enabled ? "" : "[off] ") + what);
            title.setTextColor(Color.parseColor("#e0e0e0"));
            title.setTextSize(16);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(title);

            final TextView subtitle = new TextView(this);
            subtitle.setText("Tap to edit, long-press to delete");
            subtitle.setTextColor(Color.parseColor("#7a7a7a"));
            subtitle.setTextSize(12);
            row.addView(subtitle);

            row.setOnClickListener(v -> editFilter(filter));
            row.setOnLongClickListener(v -> {
                confirmDelete(filter);
                return true;
            });

            content.addView(row);
        }
    }

    private void editFilter(final AyuFilter existing) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);

        final EditText text = new EditText(this);
        text.setHint("word or phrase");
        text.setInputType(InputType.TYPE_CLASS_TEXT);
        if (existing != null && existing.text != null) {
            text.setText(existing.text);
        }
        box.addView(text);

        final EditText regex = new EditText(this);
        regex.setHint("regular expression");
        regex.setInputType(InputType.TYPE_CLASS_TEXT);
        if (existing != null && existing.regex != null) {
            regex.setText(existing.regex);
        }
        box.addView(regex);

        final Activity self = this;
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New filter" : "Edit filter")
                .setView(box)
                .setPositiveButton("Save", (dialog, which) -> {
                    final String textValue = text.getText().toString().trim();
                    final String regexValue = regex.getText().toString().trim();
                    if (textValue.isEmpty() && regexValue.isEmpty()) {
                        toast("Filter is empty");
                        return;
                    }
                    if (!AyuFilterController.getInstance(account).isValidRegex(regexValue)) {
                        toast("Bad regular expression");
                        return;
                    }
                    final AyuFilter filter = existing != null ? existing : new AyuFilter(dialogId);
                    filter.dialogId = dialogId;
                    filter.text = textValue;
                    filter.regex = regexValue;
                    filter.enabled = true;
                    new Thread(() -> {
                        AyuFilterController.getInstance(account).saveFilter(filter);
                        // filters live behind the master switch; turning one on implies it
                        AyuConfig.load();
                        AyuConfig.filtersEnabled = true;
                        AyuConfig.save();
                        runOnUiThread(() -> {
                            toast("Filter saved");
                            reload();
                        });
                    }, "ayugram-filters-save").start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDelete(final AyuFilter filter) {
        new AlertDialog.Builder(this)
                .setTitle("Delete filter")
                .setMessage("Remove this filter?")
                .setPositiveButton("Delete", (dialog, which) -> new Thread(() -> {
                    AyuFilterController.getInstance(account).deleteFilter(filter);
                    runOnUiThread(this::reload);
                }, "ayugram-filters-delete").start())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------- helpers

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void header(String title) {
        final TextView view = new TextView(this);
        view.setText(title);
        view.setTextColor(Color.WHITE);
        view.setTextSize(24);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, dp(8));
        content.addView(view);
    }

    private void info(String text) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.parseColor("#9a9a9a"));
        view.setTextSize(14);
        view.setPadding(0, dp(4), 0, dp(4));
        content.addView(view);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
