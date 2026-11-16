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
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.journeyapps.barcodescanner.CaptureActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Original OpenFit starter UI and conservative BLE Heart Rate Service client. */
public final class MainActivity extends Activity {
    private static final int REQUEST_PERMISSIONS = 10;
    private static final int REQUEST_ENABLE_BLUETOOTH = 11;
    private static final int REQUEST_QR_SCAN = 12;
    private static final int REQUEST_CAMERA_PERMISSION = 13;
    private static final int C_BG = Color.rgb(8, 8, 8);
    private static final int C_CARD = Color.rgb(17, 17, 17);
    private static final int C_CARD_ALT = Color.rgb(27, 27, 27);
    private static final int C_TEXT = Color.rgb(245, 245, 245);
    private static final int C_MUTED = Color.rgb(143, 143, 143);
    private static final int C_RED = Color.rgb(245, 40, 60);
    private static final int C_ACCENT = Color.rgb(255, 255, 255);
    private static final int C_BORDER = Color.rgb(53, 53, 53);
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
    private LinearLayout homePanel;
    private LinearLayout healthPanel;
    private LinearLayout devicePanel;
    private LinearLayout sleepPanel;
    private LinearLayout bottomNavigation;
    private final List<TextView> tabLabels = new ArrayList<>();
    private TextView homeWatchSummary;
    private TextView connectionStatus;
    private TextView deviceStatus;
    private TextView heartRateValue;
    private TextView heartSummary;
    private TextView scanStatus;
    private TextView sleepLog;
    private LinearLayout deviceResults;
    private Button reconnectButton;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner scanner;
    private BluetoothGatt gatt;
    private ScanCallback scanCallback;
    private boolean scanning;
    private int selectedTab;
    private Runnable scanTimeout;
    private Runnable connectionTimeout;
    private BluetoothDevice pendingBondDevice;
    private boolean bondInProgress;
    private boolean bondReceiverRegistered;
    private boolean reconnectAfterPermission;
    private boolean qrConnectionPending;
    private boolean qrScanPending;
    private String qrDeviceAddress;
    private final java.util.Map<String, String> watchNames = new java.util.HashMap<>();
    private int latestHeartRate = -1;
    private String connectedDeviceName = "No watch connected";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();
        registerBondReceiver();
        render();
        refreshConnectionState();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (allGranted(grantResults) && qrScanPending) {
                qrScanPending = false;
                launchQrScanner();
            } else {
                qrScanPending = false;
                toast("Camera permission is needed to scan a pairing QR code.");
            }
            return;
        }
        if (requestCode == REQUEST_PERMISSIONS && allGranted(grantResults)) {
            if (qrDeviceAddress != null) { reconnectAfterPermission = false; saveAndConnectQrDevice(); }
            else if (reconnectAfterPermission) reconnectSavedDevice();
            else startScan();
        } else if (requestCode == REQUEST_PERMISSIONS) {
            reconnectAfterPermission = false;
            qrDeviceAddress = null;
            toast("Bluetooth permission is needed to find your watch.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_QR_SCAN) {
            try {
                IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
                if (result != null && result.getContents() != null) handlePairingQr(result.getContents());
            } catch (RuntimeException e) {
                toast("The QR scan could not be read. Try again or use Bluetooth search.");
            }
            return;
        }
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode != RESULT_OK) {
                qrConnectionPending = false;
                reconnectAfterPermission = false;
                connectionStatus.setText("Bluetooth is off · turn it on to pair your watch");
                return;
            }
            if (qrConnectionPending) {
                qrConnectionPending = false;
                if (qrDeviceAddress != null) connect(bluetoothAdapter.getRemoteDevice(qrDeviceAddress));
                else reconnectSavedDevice();
            } else startScan();
        }
    }

    private void render() {
        getWindow().setStatusBarColor(C_BG);
        getWindow().setNavigationBarColor(C_BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(C_BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22), dp(24), dp(22), dp(20));
        page.setBackgroundColor(C_BG);
        scroll.addView(page);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(shell);

        homePanel = panel();
        page.addView(homePanel);
        LinearLayout masthead = new LinearLayout(this);
        masthead.setGravity(Gravity.CENTER_VERTICAL);
        homePanel.addView(masthead, lp(-1, -2, 0, 0, 0, 25));
        TextView mark = text("●", 15, C_RED, true);
        masthead.addView(mark, lp(dp(24), -2));
        TextView brand = text("OPENFIT", 17, C_TEXT, true);
        brand.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        brand.setLetterSpacing(.12f);
        masthead.addView(brand, lp(-2, -2));
        TextView edition = text("  /  DAILY DEVICE LOG", 9, C_MUTED, true);
        edition.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL);
        edition.setLetterSpacing(.03f);
        masthead.addView(edition, lp(-2, -2, 5, 3, 0, 0));

        TextView greeting = text(new java.text.SimpleDateFormat("EEE  d MMM", Locale.getDefault())
                .format(new Date()).toUpperCase(Locale.ROOT) + "  ·  HEALTH STATUS", 10, C_MUTED, true);
        greeting.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        greeting.setLetterSpacing(.04f);
        homePanel.addView(greeting, lp(-1, -2, 0, 0, 0, 9));
        TextView headline = text("Less noise.\nMore signal.", 34, C_TEXT, true);
        headline.setLineSpacing(dp(-1), 1f);
        homePanel.addView(headline, lp(-1, -2));
        TextView subtitle = text("Your health, at a glance.", 13, C_MUTED, false);
        homePanel.addView(subtitle, lp(-1, -2, 0, 7, 0, 18));
        LinearLayout overview = card();
        homePanel.addView(overview, lp(-1, -2, 0, 0, 0, 14));
        TextView overviewLabel = text("YOUR WATCH", 10, C_MUTED, true);
        overviewLabel.setLetterSpacing(.12f);
        overview.addView(overviewLabel, lp(-1, -2));
        homeWatchSummary = text("No watch connected", 18, C_TEXT, true);
        overview.addView(homeWatchSummary, lp(-1, -2, 0, 10, 0, 0));
        TextView overviewHint = text("Pair a watch to see supported data and connection details.", 12, C_MUTED, false);
        overviewHint.setLineSpacing(dp(2), 1f);
        overview.addView(overviewHint, lp(-1, -2, 0, 5, 0, 10));
        Button openWatch = secondaryButton("Manage watch   →");
        openWatch.setOnClickListener(v -> selectTab(2));
        overview.addView(openWatch, lp(-1, dp(44)));
        Button openHealth = secondaryButton("View health   →");
        openHealth.setOnClickListener(v -> selectTab(1));
        homePanel.addView(openHealth, lp(-1, dp(48), 0, 0, 0, 0));

        devicePanel = panel();
        page.addView(devicePanel);
        addPanelHeading(devicePanel, "DEVICES", "Pair and manage your watch");
        LinearLayout deviceCard = card();
        devicePanel.addView(deviceCard, lp(-1, -2, 0, 0, 0, 13));
        LinearLayout deviceHeading = new LinearLayout(this);
        deviceHeading.setGravity(Gravity.CENTER_VERTICAL);
        deviceCard.addView(deviceHeading, lp(-1, -2));
        TextView eyebrow = text("MY WATCH", 11, C_MUTED, true);
        eyebrow.setLetterSpacing(.13f);
        deviceHeading.addView(eyebrow, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView liveMark = text("●  ON DEVICE", 9, C_RED, true);
        liveMark.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        liveMark.setLetterSpacing(.04f);
        deviceHeading.addView(liveMark, lp(-2, -2));
        deviceStatus = text("No watch connected", 21, C_TEXT, true);
        deviceCard.addView(deviceStatus, lp(-1, -2, 0, 18, 0, 0));
        connectionStatus = text("Bring your ColorFit close to get started.", 13, C_MUTED, false);
        deviceCard.addView(connectionStatus, lp(-1, -2, 0, 6, 0, 0));
        scanStatus = text("●  READY / NEARBY DEVICE", 10, C_RED, true);
        scanStatus.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        scanStatus.setLetterSpacing(.02f);
        scanStatus.setOnClickListener(v -> showGattDiagnostics());
        deviceCard.addView(scanStatus, lp(-1, -2, 0, 16, 0, 0));
        Button pair = button("Add a device   +");
        pair.setOnClickListener(v -> showPairingOptions());
        deviceCard.addView(pair, lp(-1, dp(52), 0, 16, 0, 0));
        reconnectButton = secondaryButton("Reconnect saved watch");
        reconnectButton.setVisibility(View.GONE);
        reconnectButton.setOnClickListener(v -> reconnectSavedDevice());
        deviceCard.addView(reconnectButton, lp(-1, dp(48), 0, 8, 0, 0));
        deviceResults = new LinearLayout(this);
        deviceResults.setOrientation(LinearLayout.VERTICAL);
        deviceCard.addView(deviceResults, lp(-1, -2, 0, 12, 0, 0));

        TextView section = text("YOUR HEALTH", 10, C_MUTED, true);
        section.setLetterSpacing(.14f);
        healthPanel = panel();
        page.addView(healthPanel);
        addPanelHeading(healthPanel, "HEALTH", "Your data, at a glance");
        healthPanel.addView(section, lp(-1, -2, 1, 12, 0, 10));

        LinearLayout heartCard = card();
        healthPanel.addView(heartCard, lp(-1, -2, 0, 0, 0, 12));
        LinearLayout heartHeading = new LinearLayout(this);
        heartHeading.setGravity(Gravity.CENTER_VERTICAL);
        heartCard.addView(heartHeading, lp(-1, -2));
        TextView heartTitle = text("Heart rate", 16, C_TEXT, true);
        heartHeading.addView(heartTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView heartIcon = text("♥", 16, C_RED, true);
        heartHeading.addView(heartIcon, lp(-2, -2));
        LinearLayout metricRow = new LinearLayout(this);
        metricRow.setGravity(Gravity.BOTTOM);
        heartCard.addView(metricRow, lp(-1, -2, 0, 9, 0, 0));
        heartRateValue = text("--", 58, C_TEXT, true);
        metricRow.addView(heartRateValue, lp(-2, -2));
        TextView bpm = text(" bpm", 13, C_MUTED, false);
        metricRow.addView(bpm, lp(-2, -2, 0, 0, 0, 10));
        TextView heartNote = text("Latest reading from your connected device", 12, C_MUTED, false);
        heartNote.setLineSpacing(dp(2), 1f);
        heartCard.addView(heartNote, lp(-1, -2, 0, 3, 0, 0));
        heartSummary = text("No readings yet", 12, C_MUTED, false);
        heartSummary.setLineSpacing(dp(2), 1f);
        heartCard.addView(heartSummary, lp(-1, -2, 0, 10, 0, 0));

        sleepPanel = panel();
        page.addView(sleepPanel);
        addPanelHeading(sleepPanel, "SLEEP", "Personal log");
        LinearLayout sleepCard = card();
        sleepPanel.addView(sleepCard, lp(-1, -2, 0, 0, 0, 14));
        LinearLayout sleepHeading = new LinearLayout(this);
        sleepHeading.setGravity(Gravity.CENTER_VERTICAL);
        sleepCard.addView(sleepHeading, lp(-1, -2));
        TextView sleepTitle = text("Sleep", 16, C_TEXT, true);
        sleepHeading.addView(sleepTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView moon = text("◌", 18, C_RED, true);
        sleepHeading.addView(moon, lp(-2, -2));
        sleepLog = text("No sleep entry yet", 20, C_TEXT, true);
        sleepCard.addView(sleepLog, lp(-1, -2, 0, 12, 0, 0));
        TextView sleepNote = text("A private note, stored only on this phone.", 12, C_MUTED, false);
        sleepNote.setLineSpacing(dp(2), 1f);
        sleepCard.addView(sleepNote, lp(-1, -2, 0, 5, 0, 0));
        Button sleep = secondaryButton("Add sleep duration   +");
        sleep.setOnClickListener(v -> showSleepEditor());
        sleepCard.addView(sleep, lp(-1, dp(48), 0, 14, 0, 0));

        TextView privacy = text("PRIVATE BY DESIGN     ·     STORED ON THIS PHONE", 9, C_MUTED, true);
        privacy.setLetterSpacing(.06f);
        privacy.setGravity(Gravity.CENTER);
        privacy.setLineSpacing(dp(3), 1f);
        homePanel.addView(privacy, lp(-1, -2, 0, 9, 0, 0));
        buildBottomNavigation(shell);
        selectTab(0);
    }

    private LinearLayout panel() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setVisibility(View.GONE);
        return result;
    }

    private void addPanelHeading(LinearLayout target, String eyebrow, String title) {
        TextView label = text(eyebrow, 10, C_RED, true);
        label.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        label.setLetterSpacing(.12f);
        target.addView(label, lp(-1, -2, 0, 0, 0, 8));
        TextView heading = text(title, 27, C_TEXT, true);
        target.addView(heading, lp(-1, -2, 0, 0, 0, 22));
    }

    private void buildBottomNavigation(LinearLayout shell) {
        bottomNavigation = new LinearLayout(this);
        bottomNavigation.setOrientation(LinearLayout.HORIZONTAL);
        bottomNavigation.setGravity(Gravity.CENTER);
        bottomNavigation.setPadding(dp(8), dp(8), dp(8), dp(8));
        bottomNavigation.setBackgroundColor(C_CARD);
        String[] labels = {"⌂\nHome", "♥\nHealth", "◉\nDevices", "◌\nSleep"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = text(labels[i], 11, C_MUTED, true);
            item.setGravity(Gravity.CENTER);
            item.setLineSpacing(dp(2), 1f);
            item.setPadding(dp(4), dp(5), dp(4), dp(5));
            item.setOnClickListener(v -> selectTab(index));
            bottomNavigation.addView(item, new LinearLayout.LayoutParams(0, dp(50), 1f));
            tabLabels.add(item);
        }
        shell.addView(bottomNavigation, lp(-1, dp(66)));
    }

    private void selectTab(int tab) {
        selectedTab = Math.max(0, Math.min(tab, 3));
        if (homePanel == null) return;
        homePanel.setVisibility(selectedTab == 0 ? View.VISIBLE : View.GONE);
        healthPanel.setVisibility(selectedTab == 1 ? View.VISIBLE : View.GONE);
        devicePanel.setVisibility(selectedTab == 2 ? View.VISIBLE : View.GONE);
        sleepPanel.setVisibility(selectedTab == 3 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabLabels.size(); i++) {
            tabLabels.get(i).setTextColor(i == selectedTab ? C_TEXT : C_MUTED);
            tabLabels.get(i).setBackgroundColor(i == selectedTab ? C_CARD_ALT : Color.TRANSPARENT);
        }
        if (page != null) page.scrollTo(0, 0);
    }

    private void showGattDiagnostics() {
        if (gatt == null) {
            toast("Connect a watch first to inspect its GATT services.");
            return;
        }
        StringBuilder report = new StringBuilder("GATT service report\n\n");
        List<BluetoothGattService> services = gatt.getServices();
        if (services == null || services.isEmpty()) {
            report.append("No services discovered yet. Keep the watch connected and check again.");
        } else {
            for (BluetoothGattService service : services) {
                report.append("SERVICE  ").append(service.getUuid()).append('\n');
                for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                    int p = characteristic.getProperties();
                    report.append("  CHAR  ").append(characteristic.getUuid()).append("  [")
                            .append((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0 ? "READ " : "")
                            .append((p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ? "WRITE " : "")
                            .append((p & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 ? "WRITE_NO_RESPONSE " : "")
                            .append((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ? "NOTIFY " : "")
                            .append((p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 ? "INDICATE " : "")
                            .append("]\n");
                }
                report.append('\n');
            }
            report.append("UUIDs and properties only. No proprietary writes have been sent.");
        }
        TextView body = text(report.toString(), 12, C_TEXT, false);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(dp(18), dp(14), dp(18), dp(14));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        new AlertDialog.Builder(this).setTitle("Watch diagnostics")
                .setView(scroll).setPositiveButton("Done", null).show();
    }

    private void showPairingOptions() {
        new AlertDialog.Builder(this)
                .setTitle("Add your watch")
                .setItems(new String[] {"Search for my ColorFit", "Scan a pairing QR code"}, (dialog, which) -> {
                    if (which == 0) beginPermissionFlow();
                    else startQrScan();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startQrScan() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            qrScanPending = true;
            requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        launchQrScanner();
    }

    private void launchQrScanner() {
        try {
            new IntentIntegrator(this)
                    .setRequestCode(REQUEST_QR_SCAN)
                    .setCaptureActivity(CaptureActivity.class)
                    .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                    .setPrompt("Scan the watch pairing code")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                    .initiateScan();
        } catch (SecurityException e) {
            toast("Camera access was denied. Allow camera permission to scan a QR code.");
        } catch (RuntimeException e) {
            toast("Could not open the QR scanner. Check that the camera is available.");
        }
    }

    private void handlePairingQr(String contents) {
        Matcher address = Pattern.compile("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}").matcher(contents);
        if (!address.find()) {
            toast("This QR code doesn’t contain a Bluetooth address OpenFit can use.");
            return;
        }
        if (bluetoothAdapter == null) { toast("This phone does not have Bluetooth."); return; }
        qrDeviceAddress = address.group().toUpperCase(Locale.ROOT);
        try { bluetoothAdapter.getRemoteDevice(qrDeviceAddress); }
        catch (IllegalArgumentException e) { toast("The QR code contains an invalid Bluetooth address."); return; }
        String[] permissions = requiredPermissions();
        List<String> missing = new ArrayList<>();
        for (String permission : permissions) if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) missing.add(permission);
        if (!missing.isEmpty()) {
            reconnectAfterPermission = true;
            requestPermissions(missing.toArray(new String[0]), REQUEST_PERMISSIONS);
            return;
        }
        saveAndConnectQrDevice();
    }

    private void saveAndConnectQrDevice() {
        if (qrDeviceAddress == null) return;
        if (!bluetoothAdapter.isEnabled()) {
            qrConnectionPending = true;
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BLUETOOTH);
            return;
        }
        connect(bluetoothAdapter.getRemoteDevice(qrDeviceAddress));
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
        watchNames.clear();
        try {
            scanner = bluetoothAdapter.getBluetoothLeScanner();
            if (scanner == null) { toast("Bluetooth LE scanning is unavailable."); return; }
            scanning = true;
            deviceResults.removeAllViews();
            scanStatus.setText("●  SCANNING / 12 SEC");
            scanCallback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) { addDevice(result); }
                @Override public void onBatchScanResults(List<ScanResult> results) { for (ScanResult r : results) addDevice(r); }
                @Override public void onScanFailed(int errorCode) {
                    scanning = false;
                    runOnUiThread(() -> scanStatus.setText("●  SCAN ERROR / " + errorCode));
                }
            };
            scanner.startScan(scanCallback);
            scanTimeout = () -> {
                if (!scanning) return;
                stopScan();
                scanStatus.setText(foundDevices.isEmpty() ? "●  NO DEVICE / RETRY WHEN AWAKE" : "●  DEVICE FOUND / SELECT TO CONNECT");
                showDeviceChoices();
            };
            handler.postDelayed(scanTimeout, SCAN_WINDOW_MS);
        } catch (SecurityException e) {
            scanning = false;
            scanCallback = null;
            scanStatus.setText("Bluetooth permission was not granted.");
        } catch (RuntimeException e) {
            scanning = false;
            scanCallback = null;
            scanStatus.setText("Could not start BLE scan · " + e.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("MissingPermission")
    private void addDevice(ScanResult result) {
        if (result == null || result.getDevice() == null) return;
        BluetoothDevice device = result.getDevice();
        ScanRecord record = result.getScanRecord();
        String advertisedName = record == null ? null : record.getDeviceName();
        runOnUiThread(() -> {
            String cachedName;
            try { cachedName = device.getName(); } catch (SecurityException e) { cachedName = null; }
            String name = isTargetWatchName(advertisedName) ? advertisedName : cachedName;
            if (!isTargetWatchName(name)) return;
            for (BluetoothDevice found : foundDevices) if (found.getAddress().equals(device.getAddress())) return;
            foundDevices.add(device);
            watchNames.put(device.getAddress(), name);
            scanStatus.setText("●  WATCH FOUND / CHECKING NEARBY");
        });
    }

    private boolean isTargetWatchName(String name) {
        if (name == null) return false;
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("noisefit") || normalized.contains("noise")
                || normalized.contains("colorfit") || normalized.contains("ultra buzz");
    }

    @SuppressWarnings("MissingPermission")
    private void showDeviceChoices() {
        deviceResults.removeAllViews();
        if (foundDevices.isEmpty()) {
            TextView empty = text("No recognizable Noise / ColorFit watch was found. Keep it awake and nearby, or pair by QR.", 12, C_MUTED, false);
            empty.setLineSpacing(dp(2), 1f);
            deviceResults.addView(empty, lp(-1, -2, 0, 0, 0, 8));
            Button retry = secondaryButton("Search again");
            retry.setOnClickListener(v -> beginPermissionFlow());
            deviceResults.addView(retry, lp(-1, dp(46)));
            return;
        }
        for (BluetoothDevice device : new ArrayList<>(foundDevices)) {
            String name = watchNames.get(device.getAddress());
            if (name == null) name = "Noise / ColorFit watch";
            Button choice = secondaryButton(name + "   ·   Connect  ↗");
            BluetoothDevice selected = device;
            choice.setOnClickListener(v -> connect(selected));
            deviceResults.addView(choice, lp(-1, dp(48), 0, 0, 0, 7));
        }
    }

    @SuppressWarnings("MissingPermission")
    private void connect(BluetoothDevice device) {
        stopScan();
        closeGatt();
        try {
            String name = watchNames.get(device.getAddress());
            if (name == null || name.trim().isEmpty()) name = device.getName();
            connectedDeviceName = name == null || name.trim().isEmpty() ? "BLE watch" : name;
            connectionStatus.setText("Connecting over Bluetooth LE…");
            scanStatus.setText("Connecting");
            gatt = device.connectGatt(this, false, new BluetoothGattCallback() {
                @Override public void onConnectionStateChange(BluetoothGatt source, int status, int newState) {
                    runOnUiThread(() -> {
                        if (source != gatt) return;
                        if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                            cancelConnectionTimeout();
                            deviceStatus.setText(connectedDeviceName);
                            homeWatchSummary.setText(connectedDeviceName);
                            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                                    .putString(KEY_DEVICE_ADDRESS, source.getDevice().getAddress())
                                    .putString(KEY_DEVICE_NAME, connectedDeviceName)
                                    .apply();
                            reconnectButton.setText("Reconnect to " + connectedDeviceName);
                            reconnectButton.setVisibility(View.VISIBLE);
                            connectionStatus.setText("Connected over Bluetooth LE · checking watch services");
                            scanStatus.setText("Discovering standard BLE services…");
                            try { source.discoverServices(); } catch (SecurityException e) { scanStatus.setText("Bluetooth permission needed"); }
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            cancelConnectionTimeout();
                            if ((status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
                                    || status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION)
                                    && handleGattSecurityFailure(source.getDevice(), status)) return;
                            deviceStatus.setText("No watch connected");
                            homeWatchSummary.setText("No watch connected");
                            connectionStatus.setText(status == BluetoothGatt.GATT_SUCCESS
                                    ? "Disconnected · reconnect when you’re ready"
                                    : "Connection failed · GATT status " + status);
                            scanStatus.setText(status == BluetoothGatt.GATT_SUCCESS ? "Bluetooth discovery" : "Check that the watch is awake and nearby, then retry");
                            gatt = null;
                            source.close();
                        }
                    });
                }
                @Override public void onServicesDiscovered(BluetoothGatt source, int status) {
                    runOnUiThread(() -> {
                        if (source != gatt) return;
                        if (status != BluetoothGatt.GATT_SUCCESS) {
                            if (!handleGattSecurityFailure(source.getDevice(), status)) {
                                connectionStatus.setText("Connected · service discovery failed (GATT " + status + ")");
                                scanStatus.setText("Could not inspect watch services");
                            }
                            return;
                        }
                        BluetoothGattService service = source.getService(HEART_RATE_SERVICE);
                        BluetoothGattCharacteristic characteristic = service == null ? null : service.getCharacteristic(HEART_RATE_MEASUREMENT);
                        if (characteristic == null) {
                            connectionStatus.setText("Connected · no standard heart-rate characteristic");
                            scanStatus.setText(describeDiscoveredServices(source));
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
                            int writeResult;
                            if (Build.VERSION.SDK_INT >= 33) {
                                writeResult = source.writeDescriptor(descriptor, configuration);
                            } else {
                                descriptor.setValue(configuration);
                                writeResult = source.writeDescriptor(descriptor)
                                        ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_FAILURE;
                            }
                            if (writeResult != BluetoothGatt.GATT_SUCCESS) {
                                connectionStatus.setText("Connected · could not queue heart-rate notifications");
                                scanStatus.setText("Live heart-rate data unavailable");
                            }
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
                        } else if (!handleGattSecurityFailure(source.getDevice(), status)) {
                            connectionStatus.setText("Connected · watch rejected heart-rate notifications (GATT " + status + ")");
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
            if (gatt == null) {
                connectionStatus.setText("Could not start the Bluetooth connection · retry pairing");
                scanStatus.setText("Bluetooth connection unavailable");
                return;
            }
            BluetoothGatt attempt = gatt;
            connectionTimeout = () -> {
                if (gatt != attempt) return;
                connectionTimeout = null;
                connectionStatus.setText("Connection timed out · keep the watch awake and nearby");
                scanStatus.setText("No Bluetooth connection was established");
                closeGatt();
            };
            handler.postDelayed(connectionTimeout, 20_000L);
        } catch (SecurityException e) {
            connectionStatus.setText("Bluetooth permission was not granted.");
            scanStatus.setText("Allow Bluetooth access, then retry");
        } catch (IllegalArgumentException | IllegalStateException e) {
            connectionStatus.setText("Could not connect · retry pairing");
            scanStatus.setText("Bluetooth connection unavailable");
        }
    }

    private void registerBondReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(bondReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bondReceiver, filter);
        }
        bondReceiverRegistered = true;
    }

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction()) || !bondInProgress) return;
            BluetoothDevice device;
            if (Build.VERSION.SDK_INT >= 33) {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            } else {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            }
            if (device == null || pendingBondDevice == null) return;
            try {
                if (!pendingBondDevice.getAddress().equals(device.getAddress())) return;
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                if (state == BluetoothDevice.BOND_BONDED) {
                    bondInProgress = false;
                    pendingBondDevice = null;
                    connectionStatus.setText("Bluetooth pairing accepted · reconnecting to verify watch access");
                    scanStatus.setText("Bluetooth bond complete · checking watch authentication");
                    handler.postDelayed(() -> connect(device), 500L);
                } else if (state == BluetoothDevice.BOND_NONE) {
                    bondInProgress = false;
                    pendingBondDevice = null;
                    connectionStatus.setText("Bluetooth pairing was cancelled or rejected");
                    scanStatus.setText("Keep the watch awake and accept its pairing prompt, then retry");
                }
            } catch (SecurityException e) {
                bondInProgress = false;
                pendingBondDevice = null;
                connectionStatus.setText("Bluetooth permission is needed to finish pairing");
            }
        }
    };

    private boolean handleGattSecurityFailure(BluetoothDevice device, int status) {
        if (status != BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
                && status != BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) return false;
        try {
            int bondState = device.getBondState();
            if (bondState == BluetoothDevice.BOND_BONDED) {
                connectionStatus.setText("Bluetooth is paired, but the watch requires NoiseFit authentication not implemented here");
                scanStatus.setText("No proprietary authentication command was sent");
                return true;
            }
            if (bondInProgress) return true;
            connectionStatus.setText("Watch requests Bluetooth security · opening Android pairing");
            scanStatus.setText("Accept the system Bluetooth pairing prompt if shown");
            closeGatt();
            pendingBondDevice = device;
            bondInProgress = true;
            if (!device.createBond()) {
                bondInProgress = false;
                pendingBondDevice = null;
                connectionStatus.setText("Android could not start Bluetooth pairing");
                scanStatus.setText("Retry while the watch is awake and nearby");
            }
        } catch (SecurityException e) {
            bondInProgress = false;
            pendingBondDevice = null;
            connectionStatus.setText("Bluetooth permission is needed to pair the watch");
            scanStatus.setText("Allow Bluetooth access, then retry");
        } catch (IllegalStateException e) {
            bondInProgress = false;
            pendingBondDevice = null;
            connectionStatus.setText("Could not start system Bluetooth pairing");
            scanStatus.setText("Retry while the watch is awake and nearby");
        }
        return true;
    }

    private String describeDiscoveredServices(BluetoothGatt source) {
        List<BluetoothGattService> services = source.getServices();
        if (services == null || services.isEmpty()) return "No GATT services returned by the watch";
        StringBuilder summary = new StringBuilder("GATT ").append(services.size()).append(" service(s): ");
        int shown = Math.min(3, services.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) summary.append(" · ");
            String uuid = services.get(i).getUuid().toString();
            summary.append(uuid.substring(0, 8));
        }
        if (services.size() > shown) summary.append(" · +").append(services.size() - shown);
        return summary.toString();
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
            latest += "\nToday · " + prefs.getInt("heart_rate_min", bpm) + "–"
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
        TextView separator = text("h", 16, C_MUTED, true);
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
            homeWatchSummary.setText(name);
            connectionStatus.setText("Saved watch · reconnect when nearby");
            reconnectButton.setText("Reconnect to " + name);
            reconnectButton.setVisibility(View.VISIBLE);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void stopScan() {
        if (scanTimeout != null) {
            handler.removeCallbacks(scanTimeout);
            scanTimeout = null;
        }
        if (scanner != null && scanCallback != null && scanning) {
            try { scanner.stopScan(scanCallback); } catch (SecurityException ignored) { }
        }
        scanning = false;
        scanCallback = null;
    }

    private void cancelConnectionTimeout() {
        if (connectionTimeout != null) {
            handler.removeCallbacks(connectionTimeout);
            connectionTimeout = null;
        }
    }

    @SuppressWarnings("MissingPermission")
    private void closeGatt() {
        cancelConnectionTimeout();
        if (gatt != null) {
            try { gatt.disconnect(); gatt.close(); } catch (SecurityException ignored) { }
            gatt = null;
        }
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        scanTimeout = null;
        stopScan();
        closeGatt();
        if (bondReceiverRegistered) {
            unregisterReceiver(bondReceiver);
            bondReceiverRegistered = false;
        }
        super.onDestroy();
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(19), dp(18), dp(19), dp(18));
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(C_CARD);
        shape.setCornerRadius(dp(4));
        shape.setStroke(dp(1), C_BORDER);
        box.setBackground(shape);
        if (Build.VERSION.SDK_INT >= 21) box.setElevation(dp(1));
        return box;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(9, 9, 9));
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setStateListAnimator(null);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(C_ACCENT);
        shape.setCornerRadius(dp(8));
        b.setBackground(shape);
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(C_TEXT);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setStateListAnimator(null);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(C_CARD_ALT);
        shape.setCornerRadius(dp(8));
        shape.setStroke(dp(1), C_BORDER);
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
