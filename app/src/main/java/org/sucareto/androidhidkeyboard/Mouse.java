package org.sucareto.androidhidkeyboard;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.core.view.GestureDetectorCompat;

import org.sucareto.androidhidkeyboard.databinding.ActivityMouseBinding;

public class Mouse extends HidActivity {
    private int moveSpeed = 3;
    private float scrollData;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHidContent(ActivityMouseBinding.inflate(getLayoutInflater()).getRoot());
        findViewById(R.id.BtnBack).setOnClickListener(v -> finish());
        for (int i = 1; i < 7; i++) {
            int id = getResources().getIdentifier("mBtn" + i, "id", getPackageName());
            if (id != 0) findViewById(id).setOnTouchListener(buttonTouch(false));
        }
        for (int id : new int[]{R.id.mouse_left, R.id.mouse_middle, R.id.mouse_right}) {
            findViewById(id).setOnTouchListener(buttonTouch(true));
        }
        GestureDetectorCompat touchDetector = new GestureDetectorCompat(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) { return true; }

                    @Override
                    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                        hid.clickMouse(Mouse.this, 1);
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(@NonNull MotionEvent e) {
                        hid.clickMouse(Mouse.this, 1);
                        hid.clickMouse(Mouse.this, 1);
                        return true;
                    }

                    @Override
                    public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2,
                                            float distanceX, float distanceY) {
                        hid.moveMouse(Mouse.this, Math.round(-distanceX * moveSpeed),
                                Math.round(-distanceY * moveSpeed), 0);
                        return true;
                    }

                    @Override
                    public void onLongPress(@NonNull MotionEvent e) {
                        hid.clickMouse(Mouse.this, 2);
                    }
                });
        findViewById(R.id.touch_area).setOnTouchListener((view, event) -> {
            keepPadGesture(view, event);
            touchDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP) view.performClick();
            return true;
        });
        GestureDetectorCompat scrollDetector = new GestureDetectorCompat(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) {
                        scrollData = 0;
                        return true;
                    }

                    @Override
                    public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2,
                                            float distanceX, float distanceY) {
                        scrollData += distanceY;
                        int steps = (int) (scrollData / 20);
                        if (steps != 0) {
                            scrollData -= steps * 20;
                            hid.moveMouse(Mouse.this, 0, 0, -steps);
                        }
                        return true;
                    }
                });
        findViewById(R.id.scroll_area).setOnTouchListener((view, event) -> {
            keepPadGesture(view, event);
            scrollDetector.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP) view.performClick();
            return true;
        });
        ((SeekBar) findViewById(R.id.sbMouseMoveSpeed)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                moveSpeed = progress;
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
    }

    private static void keepPadGesture(View view, MotionEvent event) {
        // A touchpad drag controls the remote pointer, even inside a scrollable small window.
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) view.getParent().requestDisallowInterceptTouchEvent(true);
        else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            view.getParent().requestDisallowInterceptTouchEvent(false);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private View.OnTouchListener buttonTouch(boolean mouse) {
        return (view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!hid.isReady(this)) return true;
                    view.setPressed(true);
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    int code = Integer.parseInt(view.getTag().toString(), 16);
                    if (mouse) hid.pressMouse(this, view.getId(), code);
                    else hid.pressKey(this, view.getId(), code, 0);
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    if (mouse) hid.releaseMouse(this, view.getId());
                    else hid.releaseKey(this, view.getId());
                    break;
            }
            return true;
        };
    }
}
