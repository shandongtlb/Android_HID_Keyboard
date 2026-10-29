package org.sucareto.androidhidkeyboard;

import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.ipc.RootService;

import java.io.FileDescriptor;
import java.io.InterruptedIOException;

/** Bound, non-daemon root helper. Never creates devices or changes USB configuration. */
public final class HidRootService extends RootService {
    private static final int WRITE_TIMEOUT_MS = 200;
    private final Object lock = new Object();
    private FileDescriptor keyboard;
    private FileDescriptor mouse;
    private int ownerUid;

    @Override
    public void onCreate() {
        ownerUid = getApplicationInfo().uid;
    }

    private void checkCaller() {
        if (Process.myUid() != 0 || Binder.getCallingUid() != ownerUid) {
            throw new SecurityException("HID service caller is not the owning application");
        }
    }

    private final IHidService.Stub binder = new IHidService.Stub() {
        @Override
        public int openDevices() {
            checkCaller();
            synchronized (lock) {
                releaseAndClose();
                try {
                    keyboard = openCharacterDevice("/dev/hidg0");
                    try {
                        mouse = openCharacterDevice("/dev/hidg1");
                    } catch (ErrnoException e) {
                        if (e.errno != OsConstants.ENOENT) throw e;
                    }
                    int result = write(keyboard, new byte[8]);
                    if (result == HidProtocol.OK && mouse != null) {
                        result = write(mouse, new byte[4]);
                    }
                    if (result != HidProtocol.OK) {
                        closeDescriptors();
                        return result;
                    }
                    return HidProtocol.KEYBOARD | (mouse == null ? 0 : HidProtocol.MOUSE);
                } catch (ErrnoException e) {
                    closeDescriptors();
                    if (e.errno == OsConstants.ENOENT) return HidProtocol.MISSING;
                    if (e.errno == OsConstants.EACCES || e.errno == OsConstants.EPERM) {
                        return HidProtocol.DENIED;
                    }
                    return HidProtocol.IO_ERROR;
                }
            }
        }

        @Override
        public int writeReport(int device, byte[] report) {
            checkCaller();
            if (!HidProtocol.validReport(device, report)) return HidProtocol.INVALID;
            synchronized (lock) {
                return write(device == HidProtocol.KEYBOARD ? keyboard : mouse, report);
            }
        }

        @Override
        public void closeDevices() {
            checkCaller();
            synchronized (lock) {
                releaseAndClose();
            }
        }
    };

    private FileDescriptor openCharacterDevice(String path) throws ErrnoException {
        // O_NOFOLLOW + fstat reject redirected paths and ordinary/block files.
        int flags = OsConstants.O_WRONLY | OsConstants.O_NONBLOCK | OsConstants.O_NOFOLLOW;
        // O_CLOEXEC is public from Android 8.1; this helper never spawns child processes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) flags |= OsConstants.O_CLOEXEC;
        FileDescriptor fd = Os.open(path, flags, 0);
        try {
            if (!OsConstants.S_ISCHR(Os.fstat(fd).st_mode)) {
                throw new ErrnoException("Not a character device", OsConstants.EINVAL);
            }
            return fd;
        } catch (ErrnoException e) {
            close(fd);
            throw e;
        }
    }

    private int write(FileDescriptor fd, byte[] report) {
        if (fd == null) return HidProtocol.MISSING;
        long deadline = SystemClock.uptimeMillis() + WRITE_TIMEOUT_MS;
        StructPollfd poll = new StructPollfd();
        poll.fd = fd;
        poll.events = (short) OsConstants.POLLOUT;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                // One complete HID report per write; never concatenate or split reports.
                return Os.write(fd, report, 0, report.length) == report.length
                        ? HidProtocol.OK : HidProtocol.IO_ERROR;
            } catch (InterruptedIOException e) {
                return HidProtocol.IO_ERROR;
            } catch (ErrnoException e) {
                if (e.errno == OsConstants.EINTR) continue;
                if (e.errno != OsConstants.EAGAIN) return HidProtocol.IO_ERROR;
            }
            int remaining = (int) (deadline - SystemClock.uptimeMillis());
            if (remaining <= 0) break;
            try {
                if (Os.poll(new StructPollfd[]{poll}, remaining) == 0) break;
                if ((poll.revents & (OsConstants.POLLERR | OsConstants.POLLHUP
                        | OsConstants.POLLNVAL)) != 0) return HidProtocol.IO_ERROR;
            } catch (ErrnoException e) {
                if (e.errno != OsConstants.EINTR) return HidProtocol.IO_ERROR;
            }
        }
        return HidProtocol.TIMEOUT;
    }

    private void releaseAndClose() {
        try {
            if (keyboard != null) write(keyboard, new byte[8]);
            if (mouse != null) write(mouse, new byte[4]);
        } finally {
            closeDescriptors();
        }
    }

    private void closeDescriptors() {
        close(keyboard);
        close(mouse);
        keyboard = null;
        mouse = null;
    }

    private static void close(FileDescriptor fd) {
        if (fd != null) {
            try { Os.close(fd); } catch (ErrnoException ignored) { }
        }
    }

    @Override
    public IBinder onBind(@NonNull Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        synchronized (lock) {
            releaseAndClose();
        }
    }
}
