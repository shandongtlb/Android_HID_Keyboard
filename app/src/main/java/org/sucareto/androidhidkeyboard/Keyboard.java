package org.sucareto.androidhidkeyboard;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import org.sucareto.androidhidkeyboard.databinding.ActivityKeyboardBinding;

public class Keyboard extends HidActivity {
    private boolean FnEnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHidContent(ActivityKeyboardBinding.inflate(getLayoutInflater()).getRoot());
        String packageName = getPackageName();
        for (int i = 1; i < 72; i++) {
            int id = getResources().getIdentifier("Btn" + i, "id", packageName);
            if (id != 0) findViewById(id).setOnTouchListener(keyTouch(false));
        }
        for (int i = 1; i < 6; i++) {
            int id = getResources().getIdentifier("CtrlBtn" + i, "id", packageName);
            if (id != 0) findViewById(id).setOnTouchListener(keyTouch(true));
        }
        findViewById(R.id.BtnSpace).setOnTouchListener(keyTouch(false));
        findViewById(R.id.BtnEnter).setOnTouchListener(keyTouch(false));
        findViewById(R.id.BtnFn).setOnTouchListener(fnTouch());
    }

    @SuppressLint("ClickableViewAccessibility")
    private View.OnTouchListener keyTouch(boolean modifier) {
        return (view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (!hid.isReady(this)) return true;
                    view.setPressed(true);
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (view.getId() == R.id.BtnSpace && FnEnable) {
                        hid.releaseAll(this);
                        startActivity(new Intent(this, Mouse.class));
                    } else if (view.getId() == R.id.BtnEnter && FnEnable) {
                        hid.pressKey(this, view.getId(), 0x4c, 0x05);
                    } else {
                        int code = Integer.parseInt(view.getTag().toString(), 16);
                        hid.pressKey(this, view.getId(), modifier ? 0 : code, modifier ? code : 0);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    // Fall through: release the captured press, regardless of the current Fn label.
                case MotionEvent.ACTION_CANCEL:
                    view.setPressed(false);
                    hid.releaseKey(this, view.getId());
                    break;
            }
            return true;
        };
    }

    @SuppressLint("ClickableViewAccessibility")
    private View.OnTouchListener fnTouch() {
        return (view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    FnEnable = true;
                    view.setPressed(true);
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                case MotionEvent.ACTION_CANCEL:
                    FnEnable = false;
                    view.setPressed(false);
                    break;
                default: return true;
            }
            updateFnLabels();
            return true;
        };
    }

    @Override
    protected void onPause() {
        FnEnable = false;
        updateFnLabels();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus && findViewById(R.id.BtnFn) != null) {
            FnEnable = false;
            findViewById(R.id.BtnFn).setPressed(false);
            updateFnLabels();
        }
    }

    @Override
    protected void onHidGeometryChanged() {
        super.onHidGeometryChanged();
        FnEnable = false;
        updateFnLabels();
    }

    private void updateFnLabels() {
            ((Button) findViewById(R.id.Btn12)).setText(getResources().getString(FnEnable ? R.string.KeyText78 : R.string.KeyText12));//Insert
            findViewById(R.id.Btn12).setTag(getResources().getString(FnEnable ? R.string.KeyCode78 : R.string.KeyCode12));

            ((Button) findViewById(R.id.Btn13)).setText(getResources().getString(FnEnable ? R.string.KeyText81 : R.string.KeyText13));//Backspace,Delete
            findViewById(R.id.Btn13).setTag(getResources().getString(FnEnable ? R.string.KeyCode81 : R.string.KeyCode13));

            ((Button) findViewById(R.id.Btn65)).setText(getResources().getString(FnEnable ? R.string.KeyText80 : R.string.KeyText84));//Up,PgUp
            findViewById(R.id.Btn65).setTag(getResources().getString(FnEnable ? R.string.KeyCode80 : R.string.KeyCode84));

            ((Button) findViewById(R.id.Btn69)).setText(getResources().getString(FnEnable ? R.string.KeyText79 : R.string.KeyText85));//Left,Home
            findViewById(R.id.Btn69).setTag(getResources().getString(FnEnable ? R.string.KeyCode79 : R.string.KeyCode85));

            ((Button) findViewById(R.id.Btn70)).setText(getResources().getString(FnEnable ? R.string.KeyText83 : R.string.KeyText86));//Down,PgDn
            findViewById(R.id.Btn70).setTag(getResources().getString(FnEnable ? R.string.KeyCode83 : R.string.KeyCode86));

            ((Button) findViewById(R.id.Btn71)).setText(getResources().getString(FnEnable ? R.string.KeyText82 : R.string.KeyText87));//Right,End
            findViewById(R.id.Btn71).setTag(getResources().getString(FnEnable ? R.string.KeyCode82 : R.string.KeyCode87));

            ((Button) findViewById(R.id.BtnSpace)).setText(getResources().getString(FnEnable ? R.string.FnSpace : R.string.KeyText70));//Space
            ((Button) findViewById(R.id.BtnEnter)).setText(getResources().getString(FnEnable ? R.string.FnEnter : R.string.KeyText54));//Enter
    }
}
