package com.radolyn.ayugram.ui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.radolyn.ayugram.AyuConfig;
import com.radolyn.ayugram.GhostMode;
import com.radolyn.ayugram.database.AyuDatabase;

import org.telegram.messenger.MessagesController;

/**
 * Standalone settings screen for the AyuGram feature set.
 *
 * <p>Built programmatically rather than from a layout resource: the Telegram
 * Android tree keeps almost all of its UI in code, and a self-contained screen
 * here means the port adds no XML that could collide with upstream.
 *
 * <p>Every toggle writes straight through to {@link AyuConfig} / {@link GhostMode}
 * and persists immediately, so there is no save button and no way to lose a change
 * by backing out.
 */
public class AyuGramPreferencesActivity extends Activity {

    private LinearLayout content;

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

        final int account = getIntent().getIntExtra("account", 0);

        header("PrimeGram");

        section("Message retention");
        toggle("Save deleted messages", AyuConfig.saveDeletedMessages, value -> {
            AyuConfig.saveDeletedMessages = value;
            AyuConfig.save();
        });
        toggle("Save edit history", AyuConfig.saveMessagesHistory, value -> {
            AyuConfig.saveMessagesHistory = value;
            AyuConfig.save();
        });
        toggle("Also save for bots", AyuConfig.saveForBots, value -> {
            AyuConfig.saveForBots = value;
            AyuConfig.save();
        });
        toggle("Dim deleted messages", AyuConfig.semiTransparentDeletedMessages, value -> {
            AyuConfig.semiTransparentDeletedMessages = value;
            AyuConfig.save();
        });

        section("Ghost mode");
        final GhostMode ghost = GhostMode.getInstance(account);
        toggle("Ghost mode active", ghost.isGhostModeActive(), value -> {
            ghost.setGhostModeEnabled(value);
            toast(value ? "Ghost mode on" : "Ghost mode off");
        });
        toggle("Send read receipts", ghost.sendReadMessages(), ghost::setSendReadMessages);
        toggle("Send read receipts for stories", ghost.sendReadStories(), ghost::setSendReadStories);
        toggle("Send online status", ghost.sendOnlinePackets(), ghost::setSendOnlinePackets);
        toggle("Send offline packet anyway", ghost.sendOfflinePacketAfterOnline(), ghost::setSendOfflinePacketAfterOnline);
        toggle("Send typing and upload progress", ghost.sendUploadProgress(), ghost::setSendUploadProgress);
        toggle("Send messages as scheduled", ghost.useScheduledMessages(), ghost::setUseScheduledMessages);
        toggle("Send messages without sound", ghost.sendWithoutSound() == GhostMode.SendWithoutSound.IN_GHOST_MODE, value ->
                ghost.setSendWithoutSound(value ? GhostMode.SendWithoutSound.IN_GHOST_MODE : GhostMode.SendWithoutSound.NEVER));

        section("Filters");
        toggle("Enable filters", AyuConfig.filtersEnabled, value -> {
            AyuConfig.filtersEnabled = value;
            AyuConfig.save();
        });
        toggle("Apply filters inside chats", AyuConfig.regexFiltersInChats, value -> {
            AyuConfig.regexFiltersInChats = value;
            AyuConfig.save();
        });
        toggle("Hide messages from blocked users", AyuConfig.hideFromBlocked, value -> {
            AyuConfig.hideFromBlocked = value;
            AyuConfig.save();
        });

        section("Appearance and misc");
        toggle("Disable sponsored messages", AyuConfig.disableAds, value -> {
            AyuConfig.disableAds = value;
            AyuConfig.save();
        });
        toggle("Local premium", AyuConfig.localPremium, value -> {
            AyuConfig.localPremium = value;
            AyuConfig.save();
        });
        toggle("Allow screenshots in protected chats", AyuConfig.showScreenshot, value -> {
            AyuConfig.showScreenshot = value;
            AyuConfig.save();
        });
        toggle("Keep retention service alive", MessagesController.getMainSettings(account).getBoolean("keepAliveService", false), value -> {
            // the app's own keep-alive switch: it is what keeps the process around long
            // enough for deletions to be captured, so write the real setting rather than
            // a private copy.
            MessagesController.getInstance(account).keepAliveService = value;
            MessagesController.getMainSettings(account).edit().putBoolean("keepAliveService", value).apply();
        });

        section("Storage");
        // The retention database lives on Telegram's storage thread; querying it inline
        // here would stall the frame and race the retention writer, so the counts are
        // filled in from a worker thread.
        final TextView deletedInfo = info("Deleted messages retained: ...");
        final TextView editedInfo = info("Edited revisions retained: ...");
        final AyuDatabase database = AyuDatabase.getInstance(account);
        new Thread(() -> {
            final int deleted = database.getDeletedCount();
            final int edited = database.getEditedCount();
            runOnUiThread(() -> {
                deletedInfo.setText("Deleted messages retained: " + deleted);
                editedInfo.setText("Edited revisions retained: " + edited);
            });
        }, "ayugram-stats").start();
    }

    // ------------------------------------------------------------------ helpers

    private interface OnToggle {
        void onChanged(boolean value);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics());
    }

    private void header(String title) {
        final TextView view = new TextView(this);
        view.setText(title);
        view.setTextColor(Color.WHITE);
        view.setTextSize(26);
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

    private TextView info(String text) {
        final TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.parseColor("#9a9a9a"));
        view.setTextSize(14);
        view.setPadding(0, dp(4), 0, dp(4));
        content.addView(view);
        return view;
    }

    private void toggle(String title, boolean initial, OnToggle callback) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        final TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Color.parseColor("#e0e0e0"));
        label.setTextSize(16);
        final LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        label.setLayoutParams(labelParams);
        row.addView(label);

        final Switch toggle = new Switch(this);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> callback.onChanged(isChecked));
        row.addView(toggle);

        content.addView(row);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
