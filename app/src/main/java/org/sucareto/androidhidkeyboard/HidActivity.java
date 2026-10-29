package org.sucareto.androidhidkeyboard;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

abstract class HidActivity extends AppCompatActivity {
    protected HidController hid;
    private HidScreen screen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hid = HidController.get(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(),
                getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        bars.hide(WindowInsetsCompat.Type.statusBars());
    }

    protected void setHidContent(View content) {
        screen = new HidScreen(this, content, () -> hid.releaseAll(this),
                () -> hid.retry(this), this::onHidGeometryChanged);
        setContentView(screen);
        ViewCompat.requestApplyInsets(screen);
    }

    /** Resizing can cancel a gesture without pausing this activity on recent Android releases. */
    protected void onHidGeometryChanged() {
        hid.releaseAll(this);
        if (screen != null) screen.clearPressedViews();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        onHidGeometryChanged();
        if (screen != null) ViewCompat.requestApplyInsets(screen);
    }

    @Override
    protected void onStart() {
        super.onStart();
        hid.start(this, screen::showStatus);
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
