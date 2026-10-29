package org.sucareto.androidhidkeyboard;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Rect;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Exercises actual layouts on the oldest supported Android and the new target runtime. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 37}, qualifiers = "en-rUS-mdpi")
public class HidScreenTest {
    private Context context;

    @Before public void setUp() {
        context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(),
                R.style.Theme_AndroidHIDKeyboard);
    }

    private View content(int resource) {
        return LayoutInflater.from(context).inflate(resource, null, false);
    }

    private static void layout(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    @Test public void landscapeKeyboardUsesAvailableArea() {
        View keyboard = content(R.layout.activity_keyboard);
        HidScreen screen = new HidScreen(context, keyboard, () -> {}, () -> {}, () -> {});
        layout(screen, 1000, 420);
        assertEquals(1000, keyboard.getWidth());
        assertEquals(screen.findViewById(R.id.hid_vertical_scroll).getHeight(), keyboard.getHeight());
        assertTrue(keyboard.findViewById(R.id.BtnEnter).getHeight() > 0);
        assertTrue(keyboard.findViewById(R.id.BtnFn).getWidth() > 0);
    }

    @Test public void smallWindowKeepsKeysReachableThroughScrolling() {
        View keyboard = content(R.layout.activity_keyboard);
        HidScreen screen = new HidScreen(context, keyboard, () -> {}, () -> {}, () -> {});
        layout(screen, 360, 220);
        assertTrue(keyboard.getWidth() >= 720);
        assertTrue(keyboard.getHeight() >= 280);
        HorizontalScrollView horizontal = screen.findViewById(R.id.hid_horizontal_scroll);
        ScrollView vertical = screen.findViewById(R.id.hid_vertical_scroll);
        horizontal.scrollTo(keyboard.getWidth(), 0);
        vertical.scrollTo(0, keyboard.getHeight());
        assertTrue(horizontal.getScrollX() > 0);
        assertTrue(vertical.getScrollY() > 0);
        assertTrue(screen.findViewById(R.id.hid_retry).getWidth() > 0);
    }

    @Test public void portraitMouseRetainsAUsablePadAndButtons() {
        View mouse = content(R.layout.activity_mouse);
        HidScreen screen = new HidScreen(context, mouse, () -> {}, () -> {}, () -> {});
        layout(screen, 800, 1000);
        View pad = mouse.findViewById(R.id.touch_area);
        assertTrue(pad.getWidth() > pad.getPaddingLeft() + pad.getPaddingRight());
        assertTrue(pad.getHeight() > pad.getPaddingTop() + pad.getPaddingBottom());
        for (int id : new int[]{R.id.BtnBack, R.id.mBtn1, R.id.mBtn6,
                R.id.mouse_left, R.id.mouse_middle, R.id.mouse_right}) {
            assertTrue(mouse.findViewById(id).getWidth() >= 24);
            assertTrue(mouse.findViewById(id).getHeight() >= 24);
        }
    }

    @Test @Config(sdk = 37)
    public void systemBarsCutoutsAndGestureInsetsAreNotAccumulated() {
        HidScreen screen = new HidScreen(context, content(R.layout.activity_keyboard),
                () -> {}, () -> {}, () -> {});
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 48))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(40, 0, 0, 0))
                .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.of(0, 0, 0, 56))
                .build();
        HidScreen.applySafeInsets(screen, insets);
        HidScreen.applySafeInsets(screen, insets);
        assertEquals(40, screen.getPaddingLeft());
        assertEquals(24, screen.getPaddingTop());
        assertEquals(56, screen.getPaddingBottom());
        layout(screen, 1000, 420);
        assertEquals(40, screen.findViewById(R.id.hid_vertical_scroll).getLeft());
        View retry = screen.findViewById(R.id.hid_retry);
        Rect bounds = new Rect();
        retry.getDrawingRect(bounds);
        screen.offsetDescendantRectToMyCoords(retry, bounds);
        assertTrue(bounds.bottom <= 420 - 56);
    }

    @Test public void resizeNotifiesOnceAndCanClearPressedFeedback() {
        int[] changes = {0};
        View keyboard = content(R.layout.activity_keyboard);
        HidScreen screen = new HidScreen(context, keyboard, () -> {}, () -> {}, () -> changes[0]++);
        layout(screen, 1000, 420);
        layout(screen, 1000, 420);
        assertEquals(0, changes[0]);
        keyboard.findViewById(R.id.BtnFn).setPressed(true);
        layout(screen, 800, 800);
        assertEquals(1, changes[0]);
        screen.clearPressedViews();
        assertFalse(keyboard.findViewById(R.id.BtnFn).isPressed());
    }

    @Test public void retryRemainsAvailableAfterAConnectionFailure() {
        int[] actions = {0, 0};
        HidScreen screen = new HidScreen(context, content(R.layout.activity_keyboard),
                () -> actions[0]++, () -> actions[1]++, () -> {});
        screen.showStatus(1, false, false);
        assertFalse(screen.findViewById(R.id.hid_retry).isEnabled());
        screen.showStatus(HidProtocol.MISSING, false, false);
        assertTrue(screen.findViewById(R.id.hid_retry).isEnabled());
        assertEquals(context.getString(R.string.hid_missing),
                ((TextView) screen.findViewById(R.id.hid_status)).getText().toString());
        screen.findViewById(R.id.hid_release).performClick();
        screen.findViewById(R.id.hid_retry).performClick();
        assertArrayEquals(new int[]{1, 1}, actions);
    }
}
