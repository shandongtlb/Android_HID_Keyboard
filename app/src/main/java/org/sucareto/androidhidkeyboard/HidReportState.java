package org.sucareto.androidhidkeyboard;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Main-thread state. Each gesture owns its original key/modifiers until release. */
final class HidReportState {
    private final Map<Integer, int[]> keys = new LinkedHashMap<>();
    private final Map<Integer, Integer> buttons = new LinkedHashMap<>();

    boolean pressKey(int source, int key, int modifiers) {
        if (keys.containsKey(source)) return false;
        keys.put(source, new int[]{key & 0xff, modifiers & 0xff});
        return true;
    }

    boolean releaseKey(int source) {
        return keys.remove(source) != null;
    }

    byte[] keyboardReport() {
        byte[] report = new byte[8];
        Set<Integer> usages = new LinkedHashSet<>();
        for (int[] stroke : keys.values()) {
            report[0] |= (byte) stroke[1];
            if (stroke[0] != 0) usages.add(stroke[0]);
        }
        int slot = 2;
        for (int usage : usages) {
            if (slot == 8) break;
            report[slot++] = (byte) usage;
        }
        return report;
    }

    boolean pressMouse(int source, int mask) {
        if (buttons.containsKey(source)) return false;
        buttons.put(source, mask & 0x1f);
        return true;
    }

    boolean releaseMouse(int source) {
        return buttons.remove(source) != null;
    }

    byte[] mouseReport(int x, int y, int wheel) {
        int mask = 0;
        for (int button : buttons.values()) mask |= button;
        return new byte[]{(byte) mask, (byte) x, (byte) y, (byte) wheel};
    }

    void clear() {
        keys.clear();
        buttons.clear();
    }
}
