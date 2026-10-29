package org.sucareto.androidhidkeyboard;

import org.junit.Test;
import static org.junit.Assert.*;

public class HidReportStateTest {
    @Test public void duplicateCtrlDownNeverTurnsIntoShift() {
        HidReportState state = new HidReportState();
        assertTrue(state.pressKey(1, 0, 1));
        assertFalse(state.pressKey(1, 0, 1));
        assertEquals(1, state.keyboardReport()[0]);
        state.releaseKey(1);
        state.releaseKey(1);
        assertArrayEquals(new byte[8], state.keyboardReport());
    }

    @Test public void chordReleasePreservesPhysicallyHeldCtrl() {
        HidReportState state = new HidReportState();
        state.pressKey(1, 0, 1);
        state.pressKey(2, 0x4c, 5);
        assertEquals(5, state.keyboardReport()[0]);
        state.releaseKey(2);
        assertArrayEquals(new byte[]{1, 0, 0, 0, 0, 0, 0, 0}, state.keyboardReport());
    }

    @Test public void fnLabelChangeDoesNotChangeAnExistingPress() {
        HidReportState state = new HidReportState();
        state.pressKey(65, 0x52, 0);
        state.pressKey(65, 0x4b, 0);
        assertEquals(0x52, state.keyboardReport()[2]);
        state.releaseKey(65);
        assertArrayEquals(new byte[8], state.keyboardReport());
    }

    @Test public void twoSourcesForSameKeyDoNotReleaseEachOther() {
        HidReportState state = new HidReportState();
        state.pressKey(1, 4, 0);
        state.pressKey(2, 4, 0);
        state.releaseKey(1);
        assertArrayEquals(new byte[]{0, 0, 4, 0, 0, 0, 0, 0}, state.keyboardReport());
        state.releaseKey(2);
        assertArrayEquals(new byte[8], state.keyboardReport());
    }

    @Test public void seventhHeldKeyBecomesVisibleWhenASlotIsReleased() {
        HidReportState state = new HidReportState();
        for (int key = 4; key <= 10; key++) state.pressKey(key, key, 0);
        assertArrayEquals(new byte[]{0, 0, 4, 5, 6, 7, 8, 9}, state.keyboardReport());
        state.releaseKey(4);
        assertArrayEquals(new byte[]{0, 0, 5, 6, 7, 8, 9, 10}, state.keyboardReport());
    }

    @Test public void duplicateMouseEventsCannotCarryIntoAnotherButton() {
        HidReportState state = new HidReportState();
        state.pressMouse(1, 1);
        state.pressMouse(1, 1);
        assertArrayEquals(new byte[]{1, 0, 0, 0}, state.mouseReport(0, 0, 0));
        state.releaseMouse(1);
        state.releaseMouse(1);
        assertArrayEquals(new byte[4], state.mouseReport(0, 0, 0));
    }

    @Test public void gestureClickDoesNotReleaseAnotherHeldMouseButton() {
        HidReportState state = new HidReportState();
        state.pressMouse(1, 1);
        state.pressMouse(-1, 1);
        state.releaseMouse(-1);
        assertEquals(1, state.mouseReport(0, 0, 0)[0]);
    }

    @Test public void queuedSnapshotSurvivesLaterReleaseAndResetClearsBothDevices() {
        HidReportState state = new HidReportState();
        state.pressKey(1, 4, 2);
        state.pressMouse(2, 2);
        byte[] snapshot = state.keyboardReport();
        state.clear();
        assertArrayEquals(new byte[]{2, 0, 4, 0, 0, 0, 0, 0}, snapshot);
        assertArrayEquals(new byte[8], state.keyboardReport());
        assertArrayEquals(new byte[4], state.mouseReport(0, 0, 0));
    }

    @Test public void rootBoundaryRejectsWrongReportSizeAndUnknownDevice() {
        assertTrue(HidProtocol.validReport(HidProtocol.KEYBOARD, new byte[8]));
        assertTrue(HidProtocol.validReport(HidProtocol.MOUSE, new byte[4]));
        assertFalse(HidProtocol.validReport(HidProtocol.MOUSE, new byte[8]));
        assertFalse(HidProtocol.validReport(HidProtocol.KEYBOARD, new byte[4]));
        assertFalse(HidProtocol.validReport(42, new byte[8]));
        assertFalse(HidProtocol.validReport(HidProtocol.KEYBOARD, null));
        assertFalse(HidProtocol.validReport(HidProtocol.MOUSE, new byte[]{(byte)0x80, 0, 0, 0}));
    }
}
