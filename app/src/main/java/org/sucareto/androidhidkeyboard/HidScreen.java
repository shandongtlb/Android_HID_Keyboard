package org.sucareto.androidhidkeyboard;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Keeps controls clear of system UI and reachable in resizable Android 17 windows. */
final class HidScreen extends LinearLayout {
    private final TextView status;
    private final Button retry;

    HidScreen(Context context, View content, Runnable release, Runnable reconnect,
              Runnable geometryChanged) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.BLACK);

        // Do not squeeze a full keyboard into an arbitrarily small split-screen window.
        // Normal landscape windows fill the viewport; smaller windows can scroll both axes.
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        content.setMinimumWidth(dp(720 * fontScale));
        content.setMinimumHeight(dp(280 * fontScale));
        if (content instanceof ConstraintLayout) {
            ((ConstraintLayout) content).setMinWidth(dp(720 * fontScale));
            ((ConstraintLayout) content).setMinHeight(dp(280 * fontScale));
        }
        ScrollView vertical = new ScrollView(context);
        vertical.setId(R.id.hid_vertical_scroll);
        vertical.setFillViewport(true);
        HorizontalScrollView horizontal = new HorizontalScrollView(context);
        horizontal.setId(R.id.hid_horizontal_scroll);
        horizontal.setFillViewport(true);
        horizontal.addView(content, new ViewGroup.LayoutParams(-2, -1));
        vertical.addView(horizontal, new ViewGroup.LayoutParams(-1, -2));
        addView(vertical, new LayoutParams(-1, 0, 1));

        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), 0, dp(8), 0);
        status = new TextView(context);
        status.setId(R.id.hid_status);
        status.setTextColor(Color.WHITE);
        status.setTextSize(12);
        status.setMaxLines(2);
        bar.addView(status, new LayoutParams(0, -2, 1));
        Button releaseButton = action(R.string.release_all);
        releaseButton.setId(R.id.hid_release);
        releaseButton.setOnClickListener(v -> release.run());
        bar.addView(releaseButton);
        retry = action(R.string.retry);
        retry.setId(R.id.hid_retry);
        retry.setOnClickListener(v -> reconnect.run());
        bar.addView(retry);
        addView(bar, new LayoutParams(-1, -2));

        ViewCompat.setOnApplyWindowInsetsListener(this, (view, insets) -> {
            int left = view.getPaddingLeft(), top = view.getPaddingTop();
            int right = view.getPaddingRight(), bottom = view.getPaddingBottom();
            applySafeInsets(view, insets);
            if (view.isLaidOut() && (left != view.getPaddingLeft() || top != view.getPaddingTop()
                    || right != view.getPaddingRight() || bottom != view.getPaddingBottom())) {
                geometryChanged.run();
            }
            return insets;
        });
        addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop,
                                   oldRight, oldBottom) -> {
            if (oldRight > oldLeft && oldBottom > oldTop
                    && (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop)) {
                geometryChanged.run();
            }
        });
    }

    private Button action(int text) {
        Button button = new Button(getContext());
        button.setText(text);
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(12), 0, dp(12), 0);
        return button;
    }

    static void applySafeInsets(View view, WindowInsetsCompat insets) {
        Insets safe = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout()
                | WindowInsetsCompat.Type.mandatorySystemGestures());
        // Assign absolute padding so repeated inset dispatches never accumulate it.
        view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
    }

    void showStatus(int code, boolean ready, boolean mouseAvailable) {
        int message;
        if (ready) message = mouseAvailable ? R.string.hid_ready : R.string.hid_keyboard_only;
        else switch (code) {
            case HidProtocol.MISSING: message = R.string.hid_missing; break;
            case HidProtocol.DENIED: message = R.string.hid_denied; break;
            case HidProtocol.TIMEOUT: message = R.string.hid_timeout; break;
            case HidProtocol.OVERFLOW: message = R.string.hid_overflow; break;
            case 1: message = R.string.hid_connecting; break;
            default: message = R.string.hid_disconnected;
        }
        status.setText(message);
        retry.setEnabled(code != 1);
    }

    void clearPressedViews() {
        clearPressed(this);
    }

    private static void clearPressed(View view) {
        view.setPressed(false);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) clearPressed(group.getChildAt(i));
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
