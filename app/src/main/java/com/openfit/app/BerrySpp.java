package com.openfit.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Classic (RFCOMM) link for the vendor "Berry" protocol.
 *
 * <p>NoiseFit reaches Berry watches through a bonded classic-Bluetooth socket on the standard
 * SPP UUID ({@code 00001101-…}); that is the only transport it uses for the account bind on
 * those models. The BLE side of the same watch can still expose the protobuf service, which is
 * why OpenFit probes the SE account commands first and only falls back here.
 *
 * <p>The socket is opened on a background thread because {@link BluetoothSocket#connect()}
 * blocks. Callbacks arrive on background threads; callers are responsible for hopping to the UI
 * thread. This class only moves bytes: frame assembly lives in {@code BerryFrameReader}.
 */
final class BerrySpp {
    /** Bluetooth SIG Serial Port Profile. */
    static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    interface Listener {
        /** Progress and failure detail for the pairing log. May be called on any thread. */
        void onLog(String message);

        /** The RFCOMM socket is connected and ready for frames. */
        void onConnected();

        /** Raw bytes from the watch; may contain partial or several Berry frames. */
        void onData(byte[] chunk);

        /** The link failed or closed. Not called after {@link BerrySpp#close()}. */
        void onFailed(String reason);
    }

    private final BluetoothAdapter adapter;
    private final BluetoothDevice device;
    private final Listener listener;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "openfit-berry-write");
        thread.setDaemon(true);
        return thread;
    });

    private volatile BluetoothSocket socket;
    private volatile OutputStream output;
    private volatile boolean closed;

    BerrySpp(BluetoothAdapter adapter, BluetoothDevice device, Listener listener) {
        this.adapter = adapter;
        this.device = device;
        this.listener = listener;
    }

    void connect() {
        Thread thread = new Thread(this::open, "openfit-berry-spp");
        thread.setDaemon(true);
        thread.start();
    }

    private void open() {
        try {
            if (adapter != null) adapter.cancelDiscovery();
            listener.onLog("opening the classic SPP socket");
            BluetoothSocket local = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket = local;
            // Blocking: only ever called from the background thread above.
            local.connect();
            output = local.getOutputStream();
            listener.onLog("SPP socket connected");
            listener.onConnected();
            readLoop(local.getInputStream());
        } catch (SecurityException e) {
            fail("Bluetooth permission is needed for the classic link");
        } catch (IOException e) {
            fail(describe(e));
        }
    }

    private void readLoop(InputStream input) {
        byte[] buffer = new byte[1024];
        try {
            while (!closed) {
                int read = input.read(buffer);
                if (read < 0) {
                    fail("the watch closed the classic link");
                    return;
                }
                if (read == 0) continue;
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                listener.onData(chunk);
            }
        } catch (IOException e) {
            if (!closed) fail(describe(e));
        }
    }

    /** Queues one frame; writes never block the caller. */
    void send(byte[] data) {
        if (data == null || data.length == 0 || closed) return;
        try {
            writer.execute(() -> {
                OutputStream stream = output;
                if (closed || stream == null) return;
                try {
                    stream.write(data);
                    stream.flush();
                } catch (IOException e) {
                    fail(describe(e));
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // The link was closed between the check above and the queue insert.
        }
    }

    private void fail(String reason) {
        if (closed) return;
        close();
        listener.onFailed(reason);
    }

    void close() {
        if (closed) return;
        closed = true;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
        output = null;
        writer.shutdownNow();
    }

    /** Short, log-friendly description; the vendor stack only ever logs the class name. */
    static String describe(IOException e) {
        String message = e.getMessage();
        String type = e.getClass().getSimpleName();
        return message == null || message.isEmpty() ? type : type + " · " + message;
    }
}
