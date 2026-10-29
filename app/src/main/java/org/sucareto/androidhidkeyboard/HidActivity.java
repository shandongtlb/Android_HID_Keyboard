package org.sucareto.androidhidkeyboard;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

abstract class HidActivity extends AppCompatActivity {
    protected HidController hid;
    private TextView status;
    private Button retry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hid = HidController.get(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    protected void setHidContent(View content) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int padding = Math.round(8 * getResources().getDisplayMetrics().density);
        bar.setPadding(padding, 0, padding, 0);
        status = new TextView(this);
        status.setTextSize(12);
        status.setMaxLines(2);
        bar.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        Button release = new Button(this);
        release.setText(R.string.release_all);
        release.setTextSize(12);
        release.setOnClickListener(v -> hid.releaseAll(this));
        bar.addView(release);
        retry = new Button(this);
        retry.setText(R.string.retry);
        retry.setTextSize(12);
        retry.setOnClickListener(v -> hid.retry(this));
        bar.addView(retry);
        page.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        setContentView(page);
    }

    @Override
    protected void onStart() {
        super.onStart();
        hid.start(this, (code, ready, mouseAvailable) -> {
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
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        hid.resume(this);
    }

    @Override
    protected void onPause() {
        hid.pause(this);
        super.onPause();
    }

    @Override
    protected void onStop() {
        hid.stop(this);
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hid != null) {
            if (hasFocus) hid.resume(this);
            else hid.pause(this);
        }
    }
}
