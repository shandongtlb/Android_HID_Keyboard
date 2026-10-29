package org.sucareto.androidhidkeyboard;

/** The root endpoint accepts reports for these two fixed devices only. */
final class HidProtocol {
    static final int KEYBOARD = 1;
    static final int MOUSE = 2;
    static final int OK = 0;
    static final int MISSING = -1;
    static final int DENIED = -2;
    static final int TIMEOUT = -3;
    static final int IO_ERROR = -4;
    static final int INVALID = -5;
    static final int OVERFLOW = -6;
    static final int DISCONNECTED = -7;

    static boolean validReport(int device, byte[] report) {
        if (report == null) return false;
        if (device == KEYBOARD) return report.length == 8 && report[1] == 0;
        return device == MOUSE && report.length == 4 && (report[0] & 0xe0) == 0;
    }

    private HidProtocol() {}
}
