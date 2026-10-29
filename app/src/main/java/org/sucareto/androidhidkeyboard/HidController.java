package org.sucareto.androidhidkeyboard;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.ipc.RootService;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** One foreground session shared by the keyboard and mouse activities. */
final class HidController {
    interface Listener {
        void onStatus(int status, boolean ready, boolean mouseAvailable);
    }

    private static HidController instance;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HidReportState state = new HidReportState();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "hid-writer");
                thread.setDaemon(true);
                return thread;
            });
    private Object owner;
    private Listener listener;
    private ServiceConnection connection;
    private IHidService remote;
    private volatile long generation;
    private boolean ready;
    private boolean foreground;
    private int devices;
    private int pendingX, pendingY, pendingWheel;
    private boolean motionScheduled;
    private final Runnable flushMotion = this::flushMotion;
    private final Runnable stopWhenIdle = () -> {
        if (owner == null) disconnect();
    };

    static HidController get(Context context) {
        if (instance == null) instance = new HidController(context.getApplicationContext());
        return instance;
    }

    private HidController(Context context) {
        this.context = context;
        Shell.setDefaultBuilder(Shell.Builder.create().setContext(context).setTimeout(10));
    }

    void start(Object newOwner, Listener newListener) {
        main.removeCallbacks(stopWhenIdle);
        resetAndRelease();
        owner = newOwner;
        foreground = true;
        listener = newListener;
        if (connection == null) connect();
        else notifyStatus(ready ? HidProtocol.OK : 1);
    }

    void stop(Object oldOwner) {
        if (owner != oldOwner) return;
        resetAndRelease();
        owner = null;
        foreground = false;
        listener = null;
        // Keep one binding while switching between the two foreground activities.
        main.postDelayed(stopWhenIdle, 100);
    }

    void retry(Object caller) {
        if (owner != caller) return;
        disconnect();
        connect();
    }

    boolean isReady(Object caller) {
        return owner == caller && ready && foreground;
    }

    void pause(Object caller) {
        if (owner == caller) {
            resetAndRelease();
            foreground = false;
        }
    }

    void resume(Object caller) {
        if (owner == caller) foreground = true;
    }

    void releaseAll(Object caller) {
        if (owner == caller) resetAndRelease();
    }

    void pressKey(Object caller, int source, int usage, int modifiers) {
        if (!isReady(caller)) return;
        if (state.pressKey(source, usage, modifiers)) send(HidProtocol.KEYBOARD, state.keyboardReport());
    }

    void releaseKey(Object caller, int source) {
        if (!isReady(caller)) return;
        if (state.releaseKey(source)) send(HidProtocol.KEYBOARD, state.keyboardReport());
    }

    void pressMouse(Object caller, int source, int mask) {
        if (!isReady(caller) || (devices & HidProtocol.MOUSE) == 0) return;
        flushMotion();
        if (state.pressMouse(source, mask)) send(HidProtocol.MOUSE, state.mouseReport(0, 0, 0));
    }

    void releaseMouse(Object caller, int source) {
        if (!isReady(caller) || (devices & HidProtocol.MOUSE) == 0) return;
        flushMotion();
        if (state.releaseMouse(source)) send(HidProtocol.MOUSE, state.mouseReport(0, 0, 0));
    }

    void clickMouse(Object caller, int mask) {
        pressMouse(caller, -1, mask);
        releaseMouse(caller, -1);
    }

    void moveMouse(Object caller, int x, int y, int wheel) {
        if (!isReady(caller) || (devices & HidProtocol.MOUSE) == 0) return;
        pendingX = bounded((long) pendingX + x);
        pendingY = bounded((long) pendingY + y);
        pendingWheel = bounded((long) pendingWheel + wheel);
        if (!motionScheduled) {
            motionScheduled = true;
            main.postDelayed(flushMotion, 16);
        }
    }

    private static int bounded(long value) {
        return (int) Math.max(-4096, Math.min(4096, value));
    }

    private void flushMotion() {
        main.removeCallbacks(flushMotion);
        motionScheduled = false;
        while (ready && (pendingX != 0 || pendingY != 0 || pendingWheel != 0)) {
            int x = Math.max(-127, Math.min(127, pendingX));
            int y = Math.max(-127, Math.min(127, pendingY));
            int wheel = Math.max(-127, Math.min(127, pendingWheel));
            pendingX -= x;
            pendingY -= y;
            pendingWheel -= wheel;
            send(HidProtocol.MOUSE, state.mouseReport(x, y, wheel));
        }
    }

    private void resetAndRelease() {
        main.removeCallbacks(flushMotion);
        motionScheduled = false;
        pendingX = pendingY = pendingWheel = 0;
        state.clear();
        worker.getQueue().removeIf(runnable -> runnable instanceof ReportTask);
        if (ready) {
            send(HidProtocol.KEYBOARD, new byte[8]);
            if ((devices & HidProtocol.MOUSE) != 0) send(HidProtocol.MOUSE, new byte[4]);
        }
    }

    private void send(int device, byte[] report) {
        if (!ready || remote == null) return;
        try {
            worker.execute(new ReportTask(generation, remote, device, report.clone()));
        } catch (RejectedExecutionException e) {
            fail(generation, HidProtocol.OVERFLOW);
        }
    }

    private final class ReportTask implements Runnable {
        final long token;
        final IHidService service;
        final int device;
        final byte[] report;

        ReportTask(long token, IHidService service, int device, byte[] report) {
            this.token = token;
            this.service = service;
            this.device = device;
            this.report = report;
        }

        @Override
        public void run() {
            if (token != generation) return;
            int result;
            try {
                result = service.writeReport(device, report);
            } catch (Exception e) {
                result = HidProtocol.DISCONNECTED;
            }
            if (result != HidProtocol.OK) {
                int error = result;
                main.post(() -> fail(token, error));
            }
        }
    }

    private void connect() {
        final long token = ++generation;
        ready = false;
        notifyStatus(1);
        ServiceConnection conn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                if (token != generation) {
                    unbind(this);
                    return;
                }
                IHidService service = IHidService.Stub.asInterface(binder);
                remote = service;
                worker.execute(() -> {
                    if (token != generation) return;
                    int result;
                    try { result = service.openDevices(); }
                    catch (Exception e) { result = HidProtocol.DENIED; }
                    int opened = result;
                    main.post(() -> {
                        if (token != generation) return;
                        if (opened < 0) {
                            fail(token, opened);
                            return;
                        }
                        devices = opened;
                        ready = true;
                        state.clear();
                        notifyStatus(HidProtocol.OK);
                    });
                });
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                fail(token, HidProtocol.DISCONNECTED);
            }
        };
        connection = conn;
        try {
            RootService.bind(new Intent(context, HidRootService.class), conn);
        } catch (Exception e) {
            fail(token, HidProtocol.DENIED);
        }
        main.postDelayed(() -> {
            if (token == generation && !ready) fail(token, HidProtocol.DENIED);
        }, 15000);
    }

    private void fail(long token, int error) {
        if (token != generation) return;
        disconnect();
        notifyStatus(error);
    }

    private void disconnect() {
        ++generation;
        ready = false;
        devices = 0;
        resetAndRelease();
        IHidService oldRemote = remote;
        ServiceConnection oldConnection = connection;
        remote = null;
        connection = null;
        // Cleanup follows any in-flight report; it must not run on the UI thread.
        if (oldConnection != null) worker.execute(() -> {
            try {
                if (oldRemote != null) oldRemote.closeDevices();
            } catch (Exception ignored) {
            } finally {
                main.post(() -> unbind(oldConnection));
            }
        });
    }

    private static void unbind(ServiceConnection connection) {
        try { RootService.unbind(connection); } catch (Exception ignored) { }
    }

    private void notifyStatus(int status) {
        if (listener != null) listener.onStatus(status, ready, (devices & HidProtocol.MOUSE) != 0);
    }
}
