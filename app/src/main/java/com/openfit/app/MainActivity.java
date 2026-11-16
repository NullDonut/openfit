package com.openfit.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Original OpenFit starter UI and conservative BLE Heart Rate Service client. */
public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 10;
    private static final int REQUEST_ENABLE_BLUETOOTH = 11;
    private static final UUID HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    private static final UUID HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    private static final UUID CLIENT_CONFIGURATION = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final byte[] ENABLE_NOTIFICATION = new byte[] {0x01, 0x00};
    private static final long SCAN_WINDOW_MS = 12_000L;
    private static final String PREFS = "openfit_local";
    private static final String KEY_DEVICE_ADDRESS = "device_address";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_SLEEP_MINUTES = "sleep_minutes";
    private static final String KEY_SLEEP_DATE = "sleep_date";
    private static final String KEY_HEART_RATE = "heart_rate";
    private static final String KEY_HEART_RATE_TIME = "heart_rate_time";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<BluetoothDevice> foundDevices = new ArrayList<>();
    private LinearLayout page;
    private TextView connectionStatus;
    private TextView deviceStatus;
    private TextView heartRateValue;
    private TextView heartSummary;
    private TextView scanStatus;
    private TextView sleepLog;
    private Button reconnectButton;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private ScanCallback scanCallback;
    private boolean scanning;
    private boolean reconnectAfterPermission;
    private int latestHeartRate = -1;
    private String connectedDeviceName = "No watch connected";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();
        render();
        refreshConnectionState();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS && allGranted(grantResults)) {
            if (reconnectAfterPermission) reconnectSavedDevice();
            else startScan();
        } else if (requestCode == REQUEST_PERMISSIONS) {
            reconnectAfterPermission = false;
            toast("Bluetooth permission is needed to find your watch.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH && resultCode == RESULT_OK) startScan();
    }

    private void render() {
        int bg = Color.rgb(246, 248, 250);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(22), dp(22), dp(30));
        page.setBackgroundColor(bg);
        scroll.addView(page);
        setContentView(scroll);

        TextView brand = text("openfit", 30, Color.rgb(23, 37, 55), true);
        page.addView(brand, lp(-1, -2));
        TextView subtitle = text("YOUR HEALTH, YOUR DATA", 11, Color.rgb(109, 123, 139), true);
        subtitle.setLetterSpacing(.13f);
        page.addView(subtitle, lp(-1, -2, 0, 3, 0, 20));

        LinearLayout deviceCard = card();
        page.addView(deviceCard, lp(-1, -2, 0, 4, 0, 12));
        TextView eyebrow = text("YOUR DEVICE", 11, Color.rgb(100, 116, 132), true);
        eyebrow.setLetterSpacing(.1f);
        deviceCard.addView(eyebrow, lp(-1, -2));
        deviceStatus = text("No watch connected", 19, Color.rgb(27, 43, 60), true);
        deviceCard.addView(deviceStatus, lp(-1, -2, 0, 12, 0, 0));
        connectionStatus = text("Connect your ColorFit Ultra Buzz to get started.", 13, Color.rgb(108, 123, 138), false);
        deviceCard.addView(connectionStatus, lp(-1, -2, 0, 4, 0, 0));
        scanStatus = text("Bluetooth discovery", 12, Color.rgb(31, 133, 123), true);
        deviceCard.addView(scanStatus, lp(-1, -2, 0, 16, 0, 0));
        Button pair = button("Pair a watch");
        pair.setOnClickListener(v -> beginPermissionFlow());
        deviceCard.addView(pair, lp(-1, dp(50), 0, 15, 0, 0));
        reconnectButton = button("Reconnect saved watch");
        reconnectButton.setVisibility(View.GONE);
        reconnectButton.setOnClickListener(v -> reconnectSavedDevice());
        deviceCard.addView(reconnectButton, lp(-1, dp(48), 0, 8, 0, 0));

        TextView section = text("TODAY", 12, Color.rgb(101, 117, 134), true);
        section.setLetterSpacing(.12f);
        page.addView(section, lp(-1, -2, 2, 14, 0, 8));

        LinearLayout heartCard = card();
        page.addView(heartCard, lp(-1, -2, 0, 0, 0, 10));
        TextView heartTitle = text("Heart rate", 16, Color.rgb(28, 44, 61), true);
        heartCard.addView(heartTitle, lp(-1, -2));
        LinearLayout metricRow = new LinearLayout(this);
        metricRow.setGravity(Gravity.BOTTOM);
        heartCard.addView(metricRow, lp(-1, -2, 0, 11, 0, 0));
        heartRateValue = text("--", 42, Color.rgb(20, 109, 101), true);
        metricRow.addView(heartRateValue, lp(-2, -2));
        TextView bpm = text(" bpm", 14, Color.rgb(100, 115, 132), false);
        metricRow.addView(bpm, lp(-2, -2, 0, 0, 0, 7));
        TextView heartNote = text("Live reading when the watch exposes the standard Heart Rate Service.", 12, Color.rgb(108, 123, 138), false);
        heartNote.setLineSpacing(dp(2), 1f);
        heartCard.addView(heartNote, lp(-1, -2, 0, 7, 0, 0));
        heartSummary = text("No readings yet", 12, Color.rgb(108, 123, 138), false);
        heartSummary.setLineSpacing(dp(2), 1f);
        heartCard.addView(heartSummary, lp(-1, -2, 0, 8, 0, 0));

        LinearLayout sleepCard = card();
        page.addView(sleepCard, lp(-1, -2, 0, 0, 0, 12));
        TextView sleepTitle = text("Sleep", 16, Color.rgb(28, 44, 61), true);
        sleepCard.addView(sleepTitle, lp(-1, -2));
        sleepLog = text("No sleep entry yet", 18, Color.rgb(28, 44, 61), true);
        sleepCard.addView(sleepLog, lp(-1, -2, 0, 10, 0, 0));
        TextView sleepNote = text("Sleep from the watch is not yet decoded. Add a private local note for now.", 12, Color.rgb(108, 123, 138), false);
        sleepNote.setLineSpacing(dp(2), 1f);
        sleepCard.addView(sleepNote, lp(-1, -2, 0, 5, 0, 0));
        Button sleep = button("Add sleep duration");
        sleep.setOnClickListener(v -> showSleepEditor());
        sleepCard.addView(sleep, lp(-1, dp(48), 0, 13, 0, 0));

        TextView privacy = text("On-device starter · no account · no analytics · no data leaves this phone", 11, Color.rgb(115, 127, 141), false);
        privacy.setGravity(Gravity.CENTER);
        privacy.setLineSpacing(dp(2), 1f);
        page.addView(privacy, lp(-1, -2, 0, 9, 0, 0));
    }

    private void beginPermissionFlow() {
        if (bluetoothAdapter == null) { toast("This phone does not have Bluetooth."); return; }
        String[] permissions = requiredPermissions();
        List<String> missing = new ArrayList<>();
        for (String permission : permissions) if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
            return;
        }
        startScan();
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= 31) return new String[] {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT};
        if (Build.VERSION.SDK_INT >= 23) return new String[] {Manifest.permission.ACCESS_FINE_LOCATION};
        return new String[0];
    }

    private boolean allGranted(int[] grants) {
        if (grants.length == 0) return false;
        for (int grant : grants) if (grant != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    @SuppressWarnings("MissingPermission")
    private void startScan() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) { beginPermissionFlow(); return; }
        stopScan();
        foundDevices.clear();
        try {
            scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner == null) { toast("Bluetooth LE scanning is unavailable."); return; }
            scanning = true;
            scanStatus.setText("Looking for nearby BLE devices…");
            scanCallback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) { addDevice(result.getDevice()); }
                @Override public void onBatchScanResults(List<ScanResult> results) { for (ScanResult r : results) addDevice(r.getDevice()); }
                @Override public void onScanFailed(int errorCode) {
                    scanning = false;
                    runOnUiThread(() -> scanStatus.setText("Scan failed · code " + errorCode));
                }
            };
            scanner.startScan(scanCallback);
            handler.postDelayed(() -> {
                stopScan();
                scanStatus.setText(foundDevices.isEmpty() ? "No BLE devices found. Keep the watch nearby and awake, then retry." : "Choose your watch below.");
                showDeviceChoices();
            }, SCAN_WINDOW_MS);
        } catch (SecurityException e) {
            scanStatus.setText("Bluetooth permission was not granted.");
        } catch (IllegalStateException e) {
            scanStatus.setText("Bluetooth is unavailable right now.");
        }
    }

    @SuppressWarnings("MissingPermission")
    private void addDevice(BluetoothDevice device) {
        if (device == null) return;
        runOnUiThread(() -> {
            for (BluetoothDevice found : foundDevices) if (found.getAddress().equals(device.getAddress())) return;
            foundDevices.add(device);
            scanStatus.setText("Found " + foundDevices.size() + " nearby device" + (foundDevices.size() == 1 ? "" : "s") + "…");
        });
    }

    @SuppressWarnings("MissingPermission")
    private void showDeviceChoices() {
        if (foundDevices.isEmpty()) {
            Button retry = button("Scan again");
            retry.setOnClickListener(v -> beginPermissionFlow());
            page.addView(retry, lp(-1, dp(48), 0, 0, 0, 12));
            return;
        }
        TextView heading = text("NEARBY DEVICES", 12, Color.rgb(101, 117, 134), true);
        heading.setLetterSpacing(.1f);
        page.addView(heading, lp(-1, -2, 2, 0, 0, 8));
        for (BluetoothDevice device : new ArrayList<>(foundDevices)) {
            String name;
            try { name = device.getName(); } catch (SecurityException e) { name = null; }
            if (name == null || name.trim().isEmpty()) name = "Unnamed device";
            Button choice = button(name + "  ·  Connect");
            BluetoothDevice selected = device;
            choice.setOnClickListener(v -> connect(selected));
            page.addView(choice, lp(-1, dp(48), 0, 0, 0, 7));
        }
    }

    @SuppressWarnings("MissingPermission")
    private void connect(BluetoothDevice device) {
        stopScan();
        closeGatt();
        try {
            String name = device.getName();
            connectedDeviceName = name == null || name.trim().isEmpty() ? "BLE watch" : name;
            connectionStatus.setText("Connecting securely over Bluetooth LE…");
            scanStatus.setText("Connecting");
            gatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt source, int status, int newState) {
                    runOnUiThread(() -> {
                        if (source != gatt) return;
                        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                            deviceStatus.setText(connectedDeviceName);
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putString(KEY_DEVICE_ADDRESS, source.getDevice().getAddress())
                                    .putString(KEY_DEVICE_NAME, connectedDeviceName)
                                    .apply();
                            reconnectButton.setText("Reconnect to " + connectedDeviceName);
                            reconnectButton.setVisibility(View.VISIBLE);
                            connectionStatus.setText("Connected · checking for heart-rate support");
                            scanStatus.setText("Discovering standard BLE services…");
                            try { source.discoverServices(); } catch (SecurityException e) { scanStatus.setText("Bluetooth permission needed"); }
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            deviceStatus.setText("No watch connected");
                            connectionStatus.setText("Disconnected · pair again when you’re ready");
                            scanStatus.setText("Bluetooth discovery");
                            gatt = null;
                            source.close();
                        }
                    });
                }
                @Override public void onServicesDiscovered(BluetoothGatt source, int status) {
                    runOnUiThread(() -> {
                        if (source != gatt) return;
                        BluetoothGattService service = source.getService(HEART_RATE_SERVICE);
                        BluetoothGattCharacteristic characteristic = service == null ? null : service.getCharacteristic(HEART_RATE_MEASUREMENT);
                        if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null) {
                            connectionStatus.setText("Connected · this watch doesn’t advertise standard heart-rate notifications");
                            scanStatus.setText("Proprietary watch protocol not yet supported");
                            return;
                        }
                        int properties = characteristic.getProperties();
                        boolean notify = (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
                        boolean indicate = (properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
                        if (!notify && !indicate) {
                            connectionStatus.setText("Connected · heart-rate characteristic is not notifiable");
                            scanStatus.setText("Live heart-rate notifications unavailable");
                            return;
                        }
                        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CONFIGURATION);
                        if (descriptor == null) {
                            connectionStatus.setText("Connected · watch has no notification configuration descriptor");
                            scanStatus.setText("Live heart-rate notifications unavailable");
                            return;
                        }
                        try {
                            if (!source.setCharacteristicNotification(characteristic, true)) {
                                connectionStatus.setText("Connected · could not enable heart-rate notifications");
                                return;
                            }
                            byte[] configuration = notify ? ENABLE_NOTIFICATION : new byte[] {0x02, 0x00};
                            connectionStatus.setText("Connected · enabling heart-rate notifications");
                            if (Build.VERSION.SDK_INT >= 33) source.writeDescriptor(descriptor, configuration);
                            else { descriptor.setValue(configuration); source.writeDescriptor(descriptor); }
                        } catch (SecurityException e) { scanStatus.setText("Permission needed to enable notifications"); }
                    });
                }
                @Override public void onDescriptorWrite(BluetoothGatt source, BluetoothGattDescriptor descriptor, int status) {
                    if (!CLIENT_CONFIGURATION.equals(descriptor.getUuid())) return;
                    runOnUiThread(() -> {
                        if (source != gatt) return;
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            connectionStatus.setText("Connected · receiving standard heart-rate data");
                            scanStatus.setText("Heart-rate notifications enabled");
                        } else {
                            connectionStatus.setText("Connected · watch rejected heart-rate notifications");
                            scanStatus.setText("Live heart-rate data unavailable");
                        }
                    });
                }
                @Override public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic) {
                    updateHeartRate(characteristic.getValue());
                }
                @Override public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic, byte[] value) {
                    updateHeartRate(value);
                }
            }, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            connectionStatus.setText("Bluetooth permission was not granted.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            connectionStatus.setText("Could not connect · retry pairing");
        }
    }

    private void updateHeartRate(byte[] value) {
        int bpm = HeartRateParser.parse(value);
        if (bpm < 0) return;
        runOnUiThread(() -> {
            heartRateValue.setText(String.valueOf(bpm));
            recordHeartRate(bpm);
        });
    }

    private void recordHeartRate(int bpm) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long minute = now / 60_000L;
        String today = java.time.LocalDate.now().toString();
        android.content.SharedPreferences.Editor editor = prefs.edit()
                .putInt(KEY_HEART_RATE, bpm)
                .putLong(KEY_HEART_RATE_TIME, now);
        if (!today.equals(prefs.getString("heart_rate_day", ""))) {
            editor.putString("heart_rate_day", today)
                    .putInt("heart_rate_min", bpm)
                    .putInt("heart_rate_max", bpm)
                    .putInt("heart_rate_samples", 1)
                    .putLong("heart_rate_sample_minute", minute);
        } else {
            editor.putInt("heart_rate_min", Math.min(bpm, prefs.getInt("heart_rate_min", bpm)))
                    .putInt("heart_rate_max", Math.max(bpm, prefs.getInt("heart_rate_max", bpm)));
            if (prefs.getLong("heart_rate_sample_minute", -1L) != minute) {
                editor.putInt("heart_rate_samples", prefs.getInt("heart_rate_samples", 0) + 1)
                        .putLong("heart_rate_sample_minute", minute);
            }
        }
        editor.apply();
        refreshHeartSummary();
    }

    private void refreshHeartSummary() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int bpm = prefs.getInt(KEY_HEART_RATE, -1);
        if (bpm < 0) { heartSummary.setText("No readings yet"); return; }
        String latest = "Latest · " + DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault())
                .format(new Date(prefs.getLong(KEY_HEART_RATE_TIME, 0L)));
        if (java.time.LocalDate.now().toString().equals(prefs.getString("heart_rate_day", ""))) {
            latest += "\\nToday · " + prefs.getInt("heart_rate_min", bpm) + "–"
                    + prefs.getInt("heart_rate_max", bpm) + " bpm · "
                    + prefs.getInt("heart_rate_samples", 1) + " minute samples";
        }
        heartSummary.setText(latest);
    }

    private void showSleepEditor() {
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setGravity(Gravity.CENTER);
        fields.setPadding(dp(24), dp(12), dp(24), dp(12));
        NumberPicker hours = new NumberPicker(this);
        hours.setMinValue(0);
        hours.setMaxValue(16);
        hours.setWrapSelectorWheel(false);
        NumberPicker minutes = new NumberPicker(this);
        minutes.setMinValue(0);
        minutes.setMaxValue(3);
        minutes.setDisplayedValues(new String[] {"00", "15", "30", "45"});
        minutes.setWrapSelectorWheel(false);
        fields.addView(hours, lp(dp(92), dp(150)));
        TextView separator = text("h", 16, Color.rgb(80, 95, 110), true);
        fields.addView(separator, lp(dp(25), -2));
        fields.addView(minutes, lp(dp(92), dp(150)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Log last night’s sleep")
                .setMessage("Choose the total duration. This note stays on this phone.")
                .setView(fields)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int duration = hours.getValue() * 60 + minutes.getValue() * 15;
            if (duration <= 0) { toast("Choose a sleep duration greater than zero."); return; }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putInt(KEY_SLEEP_MINUTES, duration)
                    .putString(KEY_SLEEP_DATE, java.time.LocalDate.now().toString())
                    .apply();
            refreshSleepSummary();
            dialog.dismiss();
            toast("Sleep note saved on this phone.");
        }));
        dialog.show();
    }

    private void refreshSleepSummary() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int duration = prefs.getInt(KEY_SLEEP_MINUTES, 0);
        if (duration <= 0) { sleepLog.setText("No sleep entry yet"); return; }
        int hours = duration / 60;
        int minutes = duration % 60;
        String date = prefs.getString(KEY_SLEEP_DATE, "");
        String label = date.equals(java.time.LocalDate.now().toString()) ? "Last night" : date;
        sleepLog.setText(String.format(Locale.getDefault(), "%s · %dh %02dm", label, hours, minutes));
    }

    @SuppressWarnings("MissingPermission")
    private void reconnectSavedDevice() {
        if (bluetoothAdapter == null) { toast("This phone does not have Bluetooth."); return; }
        String[] permissions = requiredPermissions();
        List<String> missing = new ArrayList<>();
        for (String permission : permissions) if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        if (!missing.isEmpty()) {
            reconnectAfterPermission = true;
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
            return;
        }
        reconnectAfterPermission = false;
        if (!bluetoothAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
            return;
        }
        String address = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_DEVICE_ADDRESS, null);
        if (address == null) { toast("Pair a watch first."); return; }
        try { connect(bluetoothAdapter.getRemoteDevice(address)); }
        catch (IllegalArgumentException e) { toast("The saved watch address is invalid. Scan and pair again."); }
    }

    private void refreshConnectionState() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        refreshSleepSummary();
        refreshHeartSummary();
        String address = prefs.getString(KEY_DEVICE_ADDRESS, null);
        if (address != null) {
            String name = prefs.getString(KEY_DEVICE_NAME, "saved watch");
            connectedDeviceName = name;
            deviceStatus.setText(name);
            connectionStatus.setText("Saved watch · reconnect when nearby");
            reconnectButton.setText("Reconnect to " + name);
            reconnectButton.setVisibility(View.VISIBLE);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void stopScan() {
        if (scanner != null && scanCallback != null && scanning) {
            try { scanner.stopScan(scanCallback); } catch (SecurityException ignored) { }
        }
        scanning = false;
        scanCallback = null;
    }

    @SuppressWarnings("MissingPermission")
    private void closeGatt() {
        if (gatt != null) {
            try { gatt.disconnect(); gatt.close(); } catch (SecurityException ignored) { }
            gatt = null;
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        stopScan();
        closeGatt();
        super.onDestroy();
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(17), dp(18), dp(17));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.WHITE);
        shape.setCornerRadius(dp(18));
        box.setBackground(shape);
        if (Build.VERSION.SDK_INT >= 21) box.setElevation(dp(1));
        return box;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.rgb(20, 109, 101));
        shape.setCornerRadius(dp(14));
        b.setBackground(shape);
        return b;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int width, int height) { return lp(width, height, 0, 0, 0, 0); }
    private LinearLayout.LayoutParams lp(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        return p;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
