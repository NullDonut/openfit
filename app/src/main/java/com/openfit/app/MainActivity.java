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
import com.openfit.app.protocol.BerryBinding;
import com.openfit.app.protocol.BerryFrame;
import com.openfit.app.protocol.BerryFrameReader;
import com.openfit.app.protocol.BerryProfile;
import com.openfit.app.protocol.BerrySewear;
import com.openfit.app.protocol.SeWear;
import com.openfit.app.protocol.SeGatt;
import com.openfit.app.protocol.SePairing;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
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
    private static final byte[] ENABLE_INDICATION = new byte[] {0x02, 0x00};
    private static final long SCAN_WINDOW_MS = 12_000L;
    /** Bounded GATT retry: the first attempt often fails with status 133 right after pairing. */
    private static final int MAX_CONNECT_ATTEMPTS = 4;
    private static final long CONNECT_TIMEOUT_MS = 15_000L;
    private static final long DISCOVERY_TIMEOUT_MS = 10_000L;
    private static final long RETRY_DELAY_MS = 1_000L;
    private static final long PAIRING_TIMEOUT_MS = 45_000L;
    private static final long NOTIFICATION_TIMEOUT_MS = 15_000L;
    private static final int PAIRING_MTU_REQUEST = 247;
    private static final long PAIRING_MTU_TIMEOUT_MS = 3_000L;
    private static final long SE_ACK_TIMEOUT_MS = 2_000L;
    private static final long SE_STEP_TIMEOUT_MS = 8_000L;
    private static final long SE_FIRST_COMMAND_DELAY_MS = 400L;
    private static final long CLASSIC_BOND_TIMEOUT_MS = 40_000L;
    private static final long BERRY_STEP_TIMEOUT_MS = 8_000L;
    /** A write-no-response chunk can complete without an onCharacteristicWrite callback on some
     *  stacks; the watchdog keeps the queue moving instead of stalling the bind. */
    private static final long BERRY_WRITE_TIMEOUT_MS = 1_500L;
    private static final long SE_CHUNK_GAP_MS = 15L;
    private static final long SE_BUSY_DELAY_MS = 300L;
    private static final String PREFS = "openfit_local";
    private static final String KEY_DEVICE_ADDRESS = "device_address";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_WATCH_TOKEN_PREFIX = "watch_token_";
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
    private Runnable pairingTimeout;
    private Runnable notificationTimeout;
    private Runnable retryConnection;
    private BluetoothDevice pendingBondDevice;
    private boolean bondInProgress;
    private boolean bondRequestedByApp;
    private boolean bondReceiverRegistered;
    private boolean reconnectAfterPermission;
    private boolean qrConnectionPending;
    private boolean qrScanPending;
    private String qrDeviceAddress;
    private final java.util.Map<String, String> watchNames = new java.util.HashMap<>();
    private int latestHeartRate = -1;
    private String connectedDeviceName = "No watch connected";
    private int connectAttempt;
    private String lastConnectAddress;
    private boolean connectionReady;
    private boolean notificationSetupFailed;
    private boolean bondFallbackTried;
    private String watchProfile = "unknown";
    private final List<BluetoothGattCharacteristic> notificationQueue = new ArrayList<>();
    private int notificationIndex;
    private final List<String> connectionLog = new ArrayList<>();

    // SE / Apricot vendor pairing (account bind) state. The command channel lives on the
    // 16186f00 service; it uses a chunked protobuf transport, not the Berry SPP frames.
    private final SeGatt.Transfer seTransfer = new SeGatt.Transfer();
    private BluetoothGattCharacteristic seWriteChar;   // 16186f02: announce + chunks + control
    private BluetoothGattCharacteristic seInboundChar; // characteristic that announced a reply
    private int seMtu = 23;
    private boolean seSetupStarted;
    private boolean sePairStarted;
    private boolean sePairFinished;
    private int seStepAttempts;
    private String seStep = "idle";
    private String seState = "not started";
    private boolean seAwaitingReady;
    private int seAnnounceRetries;
    private SeWrite seCurrentWrite;
    private final List<SeWrite> seWriteQueue = new ArrayList<>();
    private final List<String> pairingLog = new ArrayList<>();
    private Runnable seMtuTimeout;
    private Runnable seStepTimeout;
    private Runnable seAckTimeout;
    private byte[] seLastPayload;
    private String seLastLabel;

    // Berry pairing. NoiseFit has two Berry transports for the account exchange and both carry
    // exactly the same private 2/3/4 frames: a BLE channel on the aa01 service (its "Berry-Test"
    // stack, write aa02 / notify aa03) and a bonded classic RFCOMM socket on the SPP UUID (the
    // "Berry" stack). OpenFit tries BLE first — it needs no bond prompt and this watch does expose
    // aa01 — and falls back to SPP only when that channel stays silent.
    private BluetoothGattCharacteristic berryWriteChar;  // aa02
    private final List<BluetoothGattCharacteristic> berryNotifyChars = new ArrayList<>();
    private final List<byte[]> berryWriteChunks = new ArrayList<>();
    private boolean berryWriteBusy;
    private int berryWriteRetries;
    private final Runnable berryWriteTimeout = this::onBerryWriteTimeout;
    private BerrySpp berrySpp;
    private final BerryFrameReader berryReader = new BerryFrameReader();
    private String berryTransport = "";
    private boolean berryStarted;
    private boolean berryPhase;
    private boolean berryFinished;
    private boolean classicAttempted;
    private int berrySerial;
    private String berryStep = "idle";
    private int berryStepAttempts;
    private Runnable berryStepTimeout;

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
        StringBuilder report = new StringBuilder();
        report.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("Watch  ").append(lastConnectAddress == null ? "none" : lastConnectAddress)
                .append(" · bond ").append(bondStateLabel(lastConnectAddress)).append('\n');
        report.append("Profile  ").append(watchProfile)
                .append(connectionReady ? "  ·  connected" : "  ·  not connected").append('\n');
        report.append("Pairing  ").append(seState)
                .append("  ·  MTU ").append(seMtu)
                .append("  ·  step ").append(seStep).append("\n\n");
        report.append("CONNECTION LOG\n");
        if (connectionLog.isEmpty()) {
            report.append("No connection events yet.\n");
        } else {
            for (String line : connectionLog) report.append(line).append('\n');
        }
        report.append("\nGATT SERVICE REPORT\n");
        List<BluetoothGattService> services = gatt == null ? null : gatt.getServices();
        if (services == null || services.isEmpty()) {
            report.append("No services discovered yet. Connect the watch and check again.\n");
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
            report.append("UUIDs and properties only; pairing traffic is in the log below.\n");
        }
        report.append("\nPAIRING LOG\n");
        if (pairingLog.isEmpty()) {
            report.append("No pairing frames yet.\n");
        } else {
            for (String line : pairingLog) report.append(line).append('\n');
        }
        String lastCrash = OpenFitApp.readLastCrash(this);
        report.append("\n\nLAST APP CRASH\n");
        report.append(lastCrash == null
                ? "None recorded. A crash report is captured on the next failure.\n"
                : lastCrash + "\n");
        final String reportText = report.toString();
        TextView body = text(reportText, 12, C_TEXT, false);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(dp(18), dp(14), dp(18), dp(14));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Watch diagnostics")
                .setView(scroll)
                .setNeutralButton("Copy", (dialog, which) -> copyDiagnostics(reportText))
                .setPositiveButton("Done", null);
        if (lastCrash != null) {
            builder.setNegativeButton("Clear crash log", (dialog, which) -> OpenFitApp.clearLastCrash(this));
        }
        builder.show();
    }

    private void copyDiagnostics(String text) {
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OpenFit diagnostics", text));
        toast("Diagnostics copied to the clipboard.");
    }

    @SuppressWarnings("MissingPermission")
    private String bondStateLabel(String address) {
        if (address == null || bluetoothAdapter == null) return "unknown";
        try {
            int state = bluetoothAdapter.getRemoteDevice(address).getBondState();
            if (state == BluetoothDevice.BOND_BONDED) return "bonded";
            if (state == BluetoothDevice.BOND_BONDING) return "bonding";
            if (state == BluetoothDevice.BOND_NONE) return "not bonded";
            return "state " + state;
        } catch (SecurityException | IllegalArgumentException e) {
            return "unknown";
        }
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
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            toast("This phone reports no camera, so a QR code can’t be scanned. Use Bluetooth search instead.");
            return;
        }
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
            OpenFitApp.recordCaught(this, "QR scanner launch", e);
            toast("Camera access was denied. Allow camera permission to scan a QR code.");
        } catch (RuntimeException | LinkageError e) {
            // LinkageError covers a missing scanner dependency as well as a missing activity, so
            // a packaging problem degrades to the Bluetooth search instead of killing the app.
            OpenFitApp.recordCaught(this, "QR scanner launch", e);
            toast("Could not open the QR scanner. Use Bluetooth search instead.");
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

    /**
     * Starts a connection attempt. Bonding is deliberately not a precondition: many BLE watches
     * connect first and only ask for security when a protected characteristic is used. If the
     * watch does require pairing, {@link #handleGattSecurityFailure} starts it on demand.
     */
    @SuppressWarnings("MissingPermission")
    private void connect(BluetoothDevice device) {
        if (device == null) return;
        stopScan();
        connectAttempt = 0;
        connectionReady = false;
        bondFallbackTried = false;
        resetPairingSession();
        lastConnectAddress = device.getAddress();
        logBle("Connect requested for " + lastConnectAddress);
        String name = watchNames.get(device.getAddress());
        if (name == null || name.trim().isEmpty()) {
            try { name = device.getName(); } catch (SecurityException e) { name = null; }
        }
        connectedDeviceName = name == null || name.trim().isEmpty() ? "BLE watch" : name;
        startConnectionAttempt(device);
    }

    @SuppressWarnings("MissingPermission")
    private void startConnectionAttempt(BluetoothDevice device) {
        cancelRetryConnection();
        closeGatt();
        // A retry starts a fresh GATT link, so any half-finished bind must run again. The account
        // token itself is persistent and is deliberately left untouched.
        preparePairingAttempt();
        connectAttempt++;
        boolean retry = connectAttempt > 1;
        connectionStatus.setText(retry
                ? "Retrying Bluetooth LE connection (" + connectAttempt + "/" + MAX_CONNECT_ATTEMPTS + ")…"
                : "Connecting over Bluetooth LE…");
        scanStatus.setText(retry ? "●  RETRYING / " + connectAttempt : "●  CONNECTING");
        logBle("GATT attempt " + connectAttempt + "/" + MAX_CONNECT_ATTEMPTS);
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            connectionStatus.setText("Bluetooth is off · turn it on to connect");
            scanStatus.setText("●  BLUETOOTH OFF");
            logBle("Aborted: Bluetooth is off");
            return;
        }
        try {
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) {
            gatt = null;
            connectionStatus.setText("Bluetooth permission was not granted.");
            scanStatus.setText("Allow Nearby devices access, then retry");
            logBle("connectGatt SecurityException");
            return;
        } catch (IllegalArgumentException | IllegalStateException e) {
            gatt = null;
            connectionStatus.setText("Could not start the Bluetooth connection");
            scanStatus.setText("Bluetooth connection unavailable");
            logBle("connectGatt " + e.getClass().getSimpleName());
            return;
        }
        if (gatt == null) {
            handleConnectionFailure(device, "connectGatt returned null");
            return;
        }
        BluetoothGatt attempt = gatt;
        connectionTimeout = () -> {
            if (gatt != attempt) return;
            connectionTimeout = null;
            handleConnectionFailure(device, "timed out");
        };
        handler.postDelayed(connectionTimeout, CONNECT_TIMEOUT_MS);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt source, int status, int newState) {
            runOnUiThread(() -> handleGattConnectionState(source, status, newState));
        }

        @Override public void onServicesDiscovered(BluetoothGatt source, int status) {
            runOnUiThread(() -> handleGattServices(source, status));
        }

        @Override public void onDescriptorWrite(BluetoothGatt source, BluetoothGattDescriptor descriptor, int status) {
            runOnUiThread(() -> handleDescriptorWrite(source, descriptor, status));
        }

        @Override public void onMtuChanged(BluetoothGatt source, int mtu, int status) {
            runOnUiThread(() -> handleMtuChanged(source, mtu, status));
        }

        @Override public void onCharacteristicWrite(BluetoothGatt source, BluetoothGattCharacteristic characteristic, int status) {
            runOnUiThread(() -> handleCharacteristicWrite(source, characteristic, status));
        }

        @Override public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic) {
            byte[] value = characteristic == null ? null : characteristic.getValue();
            runOnUiThread(() -> handleNotification(characteristic, value));
        }

        @Override public void onCharacteristicChanged(BluetoothGatt source, BluetoothGattCharacteristic characteristic, byte[] value) {
            runOnUiThread(() -> handleNotification(characteristic, value));
        }
    };

    @SuppressWarnings("MissingPermission")
    private void handleGattConnectionState(BluetoothGatt source, int status, int newState) {
        if (source != gatt) {
            // Callback from a connection attempt that has already been superseded.
            try { source.close(); } catch (RuntimeException ignored) { }
            return;
        }
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logBle("Connected with non-success status " + status);
                handleConnectionFailure(source.getDevice(), "GATT " + status);
                return;
            }
            cancelConnectionTimeout();
            onGattConnected(source);
            return;
        }
        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            cancelConnectionTimeout();
            logBle("Disconnected · GATT status " + status);
            if (berryPhase) {
                // Both Berry transports are separate from the SE command channel, so a GATT drop
                // during the Berry bind must not tear down the pairing UI. The classic socket is
                // unaffected; on the BLE channel the link is gone, so the attempt ends there.
                logBle("GATT link closed · Berry pairing continues");
                if ("ble".equals(berryTransport)) {
                    abandonBleBerry("the watch link closed");
                }
                closeGatt();
                return;
            }
            if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
                    || status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
                if (handleGattSecurityFailure(source.getDevice(), status)) return;
            }
            if (connectionReady) {
                connectionReady = false;
                deviceStatus.setText("No watch connected");
                homeWatchSummary.setText("No watch connected");
                connectionStatus.setText(status == BluetoothGatt.GATT_SUCCESS
                        ? "Disconnected · reconnect when you’re ready"
                        : "Watch disconnected · GATT status " + status);
                scanStatus.setText(status == BluetoothGatt.GATT_SUCCESS
                        ? "●  READY / RECONNECT"
                        : "Reconnect when the watch is awake and nearby");
                closeGatt();
            } else {
                handleConnectionFailure(source.getDevice(),
                        status == BluetoothGatt.GATT_SUCCESS ? "connection closed" : "GATT " + status);
            }
        }
    }

    @SuppressWarnings("MissingPermission")
    private void onGattConnected(BluetoothGatt source) {
        logBle("GATT connected");
        try {
            String reported = source.getDevice().getName();
            if (reported != null && !reported.trim().isEmpty()
                    && ("BLE watch".equals(connectedDeviceName) || connectedDeviceName == null)) {
                connectedDeviceName = reported;
            }
        } catch (SecurityException ignored) { }
        deviceStatus.setText(connectedDeviceName);
        homeWatchSummary.setText(connectedDeviceName);
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(KEY_DEVICE_ADDRESS, source.getDevice().getAddress())
                    .putString(KEY_DEVICE_NAME, connectedDeviceName)
                    .apply();
        } catch (SecurityException ignored) { }
        reconnectButton.setText("Reconnect to " + connectedDeviceName);
        reconnectButton.setVisibility(View.VISIBLE);
        connectionStatus.setText("Connected · discovering watch services…");
        scanStatus.setText("●  CONNECTED / DISCOVERING");
        boolean discovering;
        try {
            discovering = source.discoverServices();
        } catch (SecurityException e) {
            discovering = false;
        }
        if (!discovering) {
            logBle("discoverServices() did not start");
            handleConnectionFailure(source.getDevice(), "service discovery did not start");
            return;
        }
        connectionTimeout = () -> {
            if (gatt != source) return;
            connectionTimeout = null;
            logBle("Service discovery timed out");
            handleConnectionFailure(source.getDevice(), "service discovery timed out");
        };
        handler.postDelayed(connectionTimeout, DISCOVERY_TIMEOUT_MS);
    }

    @SuppressWarnings("MissingPermission")
    private void handleGattServices(BluetoothGatt source, int status) {
        if (source != gatt) return;
        cancelConnectionTimeout();
        if (status != BluetoothGatt.GATT_SUCCESS) {
            logBle("Service discovery failed · GATT status " + status);
            if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
                    || status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) {
                if (handleGattSecurityFailure(source.getDevice(), status)) return;
            }
            handleConnectionFailure(source.getDevice(), "service discovery GATT " + status);
            return;
        }
        notificationQueue.clear();
        notificationIndex = 0;
        notificationSetupFailed = false;
        BluetoothGattService berry = source.getService(BerryProfile.SERVICE);
        if (berry != null) {
            watchProfile = "Noise";
            seWriteChar = berry.getCharacteristic(BerryProfile.CHAR_WRITE);
            for (BluetoothGattCharacteristic characteristic : berry.getCharacteristics()) {
                if (isNotifiable(characteristic)
                        && characteristic.getDescriptor(CLIENT_CONFIGURATION) != null) {
                    notificationQueue.add(characteristic);
                }
            }
        } else {
            BluetoothGattService heart = source.getService(HEART_RATE_SERVICE);
            BluetoothGattCharacteristic measurement =
                    heart == null ? null : heart.getCharacteristic(HEART_RATE_MEASUREMENT);
            if (measurement != null && isNotifiable(measurement)) {
                watchProfile = "Heart rate";
                notificationQueue.add(measurement);
            } else if (source.getService(BerryProfile.RYEEX_SERVICE) != null) {
                watchProfile = "Ryeex";
            } else {
                watchProfile = "Unknown";
            }
        }
        // The Berry-over-BLE channel is independent of the SE command service, so it is set up
        // whenever the watch exposes it: aa02 takes the frames, aa03 (and aa05 where present)
        // delivers the replies.
        berryWriteChar = null;
        berryNotifyChars.clear();
        BluetoothGattService berryBle = source.getService(BerryProfile.AA01_SERVICE);
        if (berryBle != null) {
            berryWriteChar = berryBle.getCharacteristic(BerryProfile.AA01_WRITE);
            for (BluetoothGattCharacteristic characteristic : berryBle.getCharacteristics()) {
                if (!isNotifiable(characteristic)) continue;
                berryNotifyChars.add(characteristic);
                if (!notificationQueue.contains(characteristic)) {
                    notificationQueue.add(characteristic);
                }
            }
        }
        logBle("Berry-over-BLE channel: "
                + (berryWriteChar == null ? "no aa02 write characteristic"
                : "aa02 write, " + berryNotifyChars.size() + " notify characteristic(s)"));
        logBle("Services: profile=" + watchProfile
                + ", " + source.getServices().size() + " service(s), "
                + notificationQueue.size() + " notifiable");
        connectionStatus.setText("Connected · " + watchProfile.toLowerCase(Locale.ROOT)
                + " watch · enabling notifications");
        scanStatus.setText("●  CONNECTED / SETTING UP");
        if (notificationQueue.isEmpty()) {
            onConnectionReady(source);
            return;
        }
        notificationTimeout = () -> {
            notificationTimeout = null;
            if (!connectionReady) {
                // Pairing must not start while a descriptor write is still outstanding: Android
                // allows one GATT operation at a time, so the first command write would be
                // rejected and the bind would fail before it began.
                logBle("Notification setup timed out · "
                        + (notificationIndex + 1) + "/" + notificationQueue.size() + " attempted");
                onConnectionReady(source);
            }
        };
        handler.postDelayed(notificationTimeout, NOTIFICATION_TIMEOUT_MS);
        enableNextNotification(source);
    }

    private static boolean isNotifiable(BluetoothGattCharacteristic characteristic) {
        int properties = characteristic.getProperties();
        return (properties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY
                | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0;
    }

    @SuppressWarnings("MissingPermission")
    private void enableNextNotification(BluetoothGatt source) {
        if (source != gatt) return;
        while (notificationIndex < notificationQueue.size()) {
            BluetoothGattCharacteristic characteristic = notificationQueue.get(notificationIndex);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CONFIGURATION);
            if (descriptor == null) {
                notificationIndex++;
                continue;
            }
            try {
                if (!source.setCharacteristicNotification(characteristic, true)) {
                    notificationSetupFailed = true;
                    logBle("setCharacteristicNotification failed for " + shortUuid(characteristic.getUuid()));
                    notificationIndex++;
                    continue;
                }
                byte[] configuration =
                        (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
                                ? ENABLE_NOTIFICATION : ENABLE_INDICATION;
                logBle("notifications: enabling " + shortUuid(characteristic.getUuid()));
                int result;
                if (Build.VERSION.SDK_INT >= 33) {
                    result = source.writeDescriptor(descriptor, configuration);
                } else {
                    descriptor.setValue(configuration);
                    result = source.writeDescriptor(descriptor)
                            ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_FAILURE;
                }
                if (result != BluetoothGatt.GATT_SUCCESS) {
                    notificationSetupFailed = true;
                    logBle("writeDescriptor failed (" + result + ") for " + shortUuid(characteristic.getUuid()));
                    notificationIndex++;
                    continue;
                }
                return; // Completion arrives in onDescriptorWrite.
            } catch (SecurityException e) {
                logBle("SecurityException while enabling notifications");
                notificationIndex++;
            }
        }
        onConnectionReady(source);
    }

    @SuppressWarnings("MissingPermission")
    private void handleDescriptorWrite(BluetoothGatt source, BluetoothGattDescriptor descriptor, int status) {
        if (source != gatt) return;
        if (!CLIENT_CONFIGURATION.equals(descriptor.getUuid())) return;
        if (status != BluetoothGatt.GATT_SUCCESS) {
            notificationSetupFailed = true;
            logBle("Descriptor write failed for "
                    + shortUuid(descriptor.getCharacteristic() == null
                            ? null : descriptor.getCharacteristic().getUuid())
                    + " · GATT status " + status);
            if ((status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
                    || status == BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION)
                    && handleGattSecurityFailure(source.getDevice(), status)) return;
        }
        if (status == BluetoothGatt.GATT_SUCCESS) {
            logBle("notifications: enabled "
                    + shortUuid(descriptor.getCharacteristic() == null
                            ? null : descriptor.getCharacteristic().getUuid()));
        }
        notificationIndex++;
        if (notificationIndex < notificationQueue.size()) {
            enableNextNotification(source);
        } else {
            onConnectionReady(source);
        }
    }

    private void onConnectionReady(BluetoothGatt source) {
        if (source != gatt) return;
        if (connectionReady) return;
        cancelNotificationTimeout();
        connectionReady = true;
        logBle("Connection ready · profile=" + watchProfile
                + (notificationQueue.isEmpty() ? " · no notifications"
                : notificationSetupFailed ? " · notifications incomplete" : " · notifications enabled"));
        String label = "Unknown".equals(watchProfile)
                ? "watch services discovered"
                : watchProfile + " profile";
        String notifications = notificationQueue.isEmpty() ? ""
                : notificationSetupFailed ? " · some notifications unavailable" : " · notifications enabled";
        deviceStatus.setText(connectedDeviceName);
        homeWatchSummary.setText(connectedDeviceName);
        connectionStatus.setText("Connected · " + label + notifications);
        scanStatus.setText("●  CONNECTED / TAP FOR DIAGNOSTICS");
        reconnectButton.setText("Reconnect to " + connectedDeviceName);
        reconnectButton.setVisibility(View.VISIBLE);
        if ("Noise".equals(watchProfile)) {
            // The vendor app performs its account bind immediately after connecting. Do the same
            // over the GATT link we already have, instead of leaving the watch merely connected.
            connectionStatus.setText("Connected · noise watch · pairing…");
            scanStatus.setText("●  CONNECTED / PAIRING");
            beginVendorPairing(source);
        }
    }

    /**
     * The vendor app starts pairing as soon as the transport is up. Negotiate a larger MTU first
     * so commands and replies fit in fewer chunks; the bind still starts if the watch refuses,
     * because the chunk size adapts to whatever MTU we end up with.
     */
    @SuppressWarnings("MissingPermission")
    private void beginVendorPairing(BluetoothGatt source) {
        if (seSetupStarted || sePairStarted || sePairFinished) return;
        seSetupStarted = true;
        seMtu = 23;
        if (seWriteChar == null) {
            finishPairing(false, "command characteristic 16186f02 is missing");
            return;
        }
        seState = "negotiating MTU";
        seStep = "mtu";
        boolean requested = false;
        try {
            requested = source.requestMtu(PAIRING_MTU_REQUEST);
        } catch (SecurityException ignored) {
        }
        logPairing("MTU request  " + (requested ? String.valueOf(PAIRING_MTU_REQUEST) : "failed"));
        if (!requested) {
            startSePairing();
            return;
        }
        seMtuTimeout = () -> {
            seMtuTimeout = null;
            if (sePairStarted || sePairFinished) return;
            logPairing("MTU timeout · continuing with " + seMtu + " bytes");
            startSePairing();
        };
        handler.postDelayed(seMtuTimeout, PAIRING_MTU_TIMEOUT_MS);
    }

    private void handleMtuChanged(BluetoothGatt source, int mtu, int status) {
        if (source != gatt) return;
        seMtu = Math.max(23, Math.min(mtu, 517));
        cancelSeMtuTimeout();
        logPairing("MTU " + mtu + " (status " + status + ") · chunk "
                + SeGatt.chunkSize(seMtu) + " data bytes");
        logBle("SE transport MTU negotiated · " + seMtu + " bytes");
        if (seSetupStarted && !sePairStarted) startSePairing();
    }

    /**
     * Opens the account exchange the way the vendor SDK does on connect: the id-16 "are you
     * already bound?" probe first. The id-0 {@code ble_connect_parmeter_config} greeting is
     * deliberately not sent: the watch answers it with {@code error_code=NOT_SUPPORT}
     * ({@code 08 00 10 00 A0 06 01}), so this firmware does not implement MTU change over
     * protobuf. Each command still travels through the announce/ready/chunk transport in
     * {@link SeGatt}.
     */
    private void startSePairing() {
        if (sePairStarted || sePairFinished) return;
        if (gatt == null || seWriteChar == null) {
            finishPairing(false, "connection lost before pairing");
            return;
        }
        cancelSeMtuTimeout();
        sePairStarted = true;
        seStepAttempts = 1;
        seStep = "binding-status";
        seState = "asking the watch for its bind state";
        connectionStatus.setText("Pairing · opening " + connectedDeviceName + " command channel…");
        scanStatus.setText("●  PAIRING / BIND STATE");
        // The vendor stack's first command is the binding-status probe, and it tolerates the
        // short settling time between notification setup and the first announce.
        handler.postDelayed(() -> {
            if (sePairFinished || !sePairStarted) return;
            sendSe(SePairing.bindingStatusRequest(), "binding-status");
            scheduleSeStepTimeout();
        }, SE_FIRST_COMMAND_DELAY_MS);
    }

    private void sendSe(byte[] payload, String label) {
        if (payload == null || payload.length == 0) return;
        logPairing("TX " + label + " · " + hex(payload));
        seWriteQueue.add(new SeWrite(payload, label));
        pumpSeWrites();
    }

    private void pumpSeWrites() {
        if (seCurrentWrite != null || seWriteQueue.isEmpty()) return;
        if (gatt == null || seWriteChar == null) {
            seWriteQueue.clear();
            return;
        }
        SeWrite write = seWriteQueue.remove(0);
        seCurrentWrite = write;
        seLastPayload = write.data;
        seLastLabel = write.label;
        seAwaitingReady = true;
        seAnnounceRetries = 0;
        sendSeAnnounce(write);
    }

    private void sendSeAnnounce(SeWrite write) {
        if (write == null || sePairFinished) return;
        int count = SeGatt.chunkCount(write.data.length, seMtu);
        logPairing("announce " + write.label + " · " + count + " chunk(s)");
        if (!writeSe(SeGatt.announce(count))) {
            logPairing("announce " + write.label + " rejected by the Bluetooth stack");
            if (++seAnnounceRetries > 4) {
                seAwaitingReady = false;
                seCurrentWrite = null;
                finishPairing(false, "the watch rejected the command announcement");
                return;
            }
            // A GATT operation from connection setup may still be in flight; Android rejects
            // writes until it completes, so wait longer than the descriptor interval.
            handler.postDelayed(() -> sendSeAnnounce(write), 350L);
            return;
        }
        scheduleSeAckTimeout();
    }

    private void scheduleSeAckTimeout() {
        cancelSeAckTimeout();
        seAckTimeout = () -> {
            seAckTimeout = null;
            if (!seAwaitingReady || seCurrentWrite == null || sePairFinished) return;
            SeWrite write = seCurrentWrite;
            if (++seAnnounceRetries > 3) {
                seAwaitingReady = false;
                seCurrentWrite = null;
                logPairing("no READY control frame · giving up on " + write.label);
                pumpSeWrites();
                return;
            }
            logPairing("no READY control frame · retrying " + write.label
                    + " (" + seAnnounceRetries + "/3)");
            sendSeAnnounce(write);
        };
        handler.postDelayed(seAckTimeout, SE_ACK_TIMEOUT_MS);
    }

    private void onSeReady() {
        if (sePairFinished || !seAwaitingReady || seCurrentWrite == null) return;
        cancelSeAckTimeout();
        seAwaitingReady = false;
        logPairing("watch ready · sending " + seCurrentWrite.label);
        sendSeChunk();
    }

    private void sendSeChunk() {
        SeWrite write = seCurrentWrite;
        if (write == null || sePairFinished) return;
        int size = SeGatt.chunkSize(seMtu);
        if (write.offset >= write.data.length) {
            int chunks = (write.data.length + size - 1) / size;
            logPairing("sent " + chunks + " chunk(s) · " + write.label);
            seCurrentWrite = null;
            handler.postDelayed(this::pumpSeWrites, SE_CHUNK_GAP_MS);
            return;
        }
        int length = Math.min(size, write.data.length - write.offset);
        int index = write.offset / size + 1;
        if (!writeSe(SeGatt.packet(index, write.data, write.offset, length))) {
            // A no-response write can be refused while the controller queue is full.
            handler.postDelayed(this::sendSeChunk, 40L);
            return;
        }
        write.offset += length;
        handler.postDelayed(this::sendSeChunk, SE_CHUNK_GAP_MS);
    }

    private void onSeBusy() {
        if (seCurrentWrite == null || sePairFinished) return;
        logPairing("watch busy · retrying " + seCurrentWrite.label + " after a pause");
        cancelSeAckTimeout();
        seAwaitingReady = true;
        SeWrite write = seCurrentWrite;
        handler.postDelayed(() -> {
            if (seCurrentWrite == write && seAwaitingReady) sendSeAnnounce(write);
        }, SE_BUSY_DELAY_MS);
    }

    private void resendSeChunk(int index) {
        byte[] data = seLastPayload;
        if (data == null || index < 1) return;
        int size = SeGatt.chunkSize(seMtu);
        int offset = (index - 1) * size;
        if (offset >= data.length) return;
        int length = Math.min(size, data.length - offset);
        logPairing("resend chunk " + index + " · " + seLastLabel);
        writeSe(SeGatt.packet(index, data, offset, length));
    }

    private void onSeMessage(byte[] message) {
        int id = SeWear.readId(message);
        logPairing("SEWear · " + SePairing.describe(message) + " · " + hex(message));
        int error = SeWear.readErrorCode(message);
        if (error != 0) {
            logPairing("watch error for command " + id + " · " + SeWear.errorLabel(error));
            logBle("Watch reported " + SeWear.errorLabel(error) + " for command " + id);
            if (onSeRefusal(id, error)) return;
        }
        if (id == SePairing.FUNCTION_MTU) return; // id-0 greeting reply, not part of the bind
        if (id == SePairing.FUNCTION_INQUIRY_BINDING_STATUS) {
            onSeBindingStatus(message);
        } else if (id == SePairing.FUNCTION_BINDING_CHECK) {
            onSeBindCheck(message);
        } else if (id == SePairing.FUNCTION_BINDING_RESULT) {
            onSeBindResult(message);
        } else if (id == SePairing.FUNCTION_VERIFY_USER_NUMBER) {
            onSeVerifyUser(message);
        } else {
            logPairing("SE reply id " + id + " · " + SeWear.errorLabel(error) + " · " + hex(message));
        }
    }

    private void onSeBindingStatus(byte[] message) {
        if (sePairFinished) return;
        cancelSeStepTimeout();
        Boolean bound = SePairing.bindingStatus(message);
        logBle("Watch bound state · " + (bound == null ? "unreadable" : bound ? "yes" : "no"));
        if (bound == null || !bound) {
            requestSeBindCheck();
            return;
        }
        String token = storedToken(lastConnectAddress);
        if (token == null) {
            finishPairing(false,
                    "watch is already bound to another account · unbind it in the watch settings");
            return;
        }
        seStep = "verify-user";
        seStepAttempts = 1;
        seState = "verifying saved account";
        connectionStatus.setText("Pairing · verifying " + connectedDeviceName + "…");
        scanStatus.setText("●  PAIRING / VERIFY");
        sendSe(SePairing.verifyUserNumber(token), "verify-user");
        scheduleSeStepTimeout();
    }

    private void requestSeBindCheck() {
        seStep = "bind-check";
        seStepAttempts = 1;
        seState = "requesting bind";
        connectionStatus.setText("Pairing · asking the watch to bind…");
        scanStatus.setText("●  PAIRING / BIND REQUEST");
        sendSe(SePairing.bindCheckRequest(), "bind-check");
        scheduleSeStepTimeout();
    }

    private void onSeBindCheck(byte[] message) {
        if (sePairFinished) return;
        cancelSeStepTimeout();
        SePairing.BindCheck check = SePairing.bindCheck(message);
        if (check == null) {
            finishPairing(false, "watch sent an unreadable bind check reply");
            return;
        }
        logPairing("bind check · " + check);
        logBle("Watch bind check · " + SePairing.resultLabel(check.result)
                + (check.deviceVerify ? " · device verify" : " · no device verify"));
        if (check.result != SePairing.BIND_RESULT_SUCCESS) {
            finishPairing(false, SePairing.resultLabel(check.result));
            return;
        }
        if (!check.deviceVerify) {
            // The vendor SDK aborts the bind when the watch does not confirm the device check.
            finishPairing(false, "the watch did not confirm this phone");
            return;
        }
        String token = openFitToken(lastConnectAddress);
        seStep = "bind-result";
        seStepAttempts = 1;
        seState = "confirming bind";
        connectionStatus.setText("Pairing · confirming with " + connectedDeviceName + "…");
        scanStatus.setText("●  PAIRING / CONFIRM");
        sendSe(SePairing.bindResult(token), "bind-result");
        scheduleSeStepTimeout();
    }

    private void onSeBindResult(byte[] message) {
        if (sePairFinished) return;
        cancelSeStepTimeout();
        int type = SePairing.bindResultType(message);
        int error = SeWear.readErrorCode(message);
        if (type < 0) {
            // Some firmware answers id 18 with only an errorCode; NO_ERROR means accepted.
            if (error == 0) finishPairing(true, "vendor bind confirmed");
            else finishPairing(false, "watch rejected the bind (error " + error + ")");
            return;
        }
        if (type == SePairing.BIND_RESULT_SUCCESS) {
            finishPairing(true, "vendor bind confirmed");
        } else {
            clearStoredToken(lastConnectAddress);
            finishPairing(false, SePairing.resultLabel(type));
        }
    }

    private void onSeVerifyUser(byte[] message) {
        if (sePairFinished) return;
        cancelSeStepTimeout();
        Boolean verified = SePairing.verifyResult(message);
        if (verified == null) {
            finishPairing(false, "watch sent an unreadable verify reply");
        } else if (verified) {
            finishPairing(true, "saved account verified");
        } else {
            finishPairing(false, "watch rejected the saved account");
        }
    }

    /**
     * An {@code error_code} is the watch's authoritative answer, so it is handled here instead of
     * falling through to the payload parsers (which would report a refusal as "unreadable").
     * Returns true when the reply was fully handled.
     */
    private boolean onSeRefusal(int id, int error) {
        if (sePairFinished) return true;
        String label = SeWear.errorLabel(error);
        if (id == SePairing.FUNCTION_INQUIRY_BINDING_STATUS && "binding-status".equals(seStep)) {
            // Escalate now rather than waiting out the step timeout.
            advanceBindingStatusProbe();
            return true;
        }
        if (id == SePairing.FUNCTION_BINDING_CHECK && "bind-check".equals(seStep)) {
            finishPairing(false, "the watch refused the bind request · " + label);
            return true;
        }
        if (id == SePairing.FUNCTION_BINDING_RESULT && "bind-result".equals(seStep)) {
            clearStoredToken(lastConnectAddress);
            finishPairing(false, "the watch refused the bind result · " + label);
            return true;
        }
        if (id == SePairing.FUNCTION_VERIFY_USER_NUMBER && "verify-user".equals(seStep)) {
            finishPairing(false, "the watch refused the saved account · " + label);
            return true;
        }
        return false;
    }

    /**
     * Moves the id-16 probe through its variants. The watch answers each one with an error code
     * on this firmware, so both the refusal path and the step timeout land here; after the last
     * variant the bind check is sent directly.
     */
    private void advanceBindingStatusProbe() {
        if (sePairFinished) return;
        cancelSeStepTimeout();
        if (seStepAttempts < 2) {
            // Some firmware needs the account submessage to be present before it answers; the
            // vendor client sends the bare id.
            seStepAttempts = 2;
            logPairing("probing the explicit binding-status form");
            sendSe(SePairing.bindingStatusRequestExplicit(), "binding-status (explicit)");
            scheduleSeStepTimeout();
            return;
        }
        if (seStepAttempts < 3) {
            // The empty id-0 config was rejected; try the populated form the vendor app sends
            // once it knows the MTU, then repeat the bind probe.
            seStepAttempts = 3;
            logPairing("sending the MTU config, then binding-status again");
            sendSe(SePairing.connectParameterConfig(seMtu), "connect-params");
            sendSe(SePairing.bindingStatusRequest(), "binding-status");
            scheduleSeStepTimeout();
            return;
        }
        // The watch never reported its bind state. Try the bind check directly: this is what the
        // vendor's "bind this phone" action sends, and on many firmwares it is the command that
        // makes the watch display its confirmation prompt.
        logPairing("binding-status unanswered · trying the bind-check directly");
        requestSeBindCheck();
    }

    private void scheduleSeStepTimeout() {
        cancelSeStepTimeout();
        seStepTimeout = () -> {
            seStepTimeout = null;
            if (sePairFinished) return;
            if ("binding-status".equals(seStep)) {
                logPairing("no reply for binding-status (" + seStepAttempts + "/3)");
                advanceBindingStatusProbe();
                return;
            }
            if (seStepAttempts < 2) {
                seStepAttempts++;
                if ("bind-check".equals(seStep)) {
                    logPairing("no reply for bind-check · retrying");
                    sendSe(SePairing.bindCheckRequest(), "bind-check");
                } else if ("bind-result".equals(seStep)) {
                    logPairing("no reply for bind-result · retrying");
                    sendSe(SePairing.bindResult(openFitToken(lastConnectAddress)), "bind-result");
                } else if ("verify-user".equals(seStep)) {
                    logPairing("no reply for verify-user · retrying");
                    String token = storedToken(lastConnectAddress);
                    if (token != null) sendSe(SePairing.verifyUserNumber(token), "verify-user");
                }
                scheduleSeStepTimeout();
                return;
            }
            finishPairing(false, "no reply from the watch for " + seStep);
        };
        handler.postDelayed(seStepTimeout, SE_STEP_TIMEOUT_MS);
    }

    private void finishPairing(boolean success, String detail) {
        if (sePairFinished) return;
        sePairFinished = true;
        cancelSeTimeouts();
        seWriteQueue.clear();
        seCurrentWrite = null;
        seAwaitingReady = false;
        seTransfer.reset();
        // A watch that answers the SE account commands with NOT_SUPPORT, or not at all, is
        // almost certainly a Berry model: the vendor binds those on a different stack. Try that
        // once before reporting a failure.
        if (!success && startBerryFallback(detail)) return;
        reportPairing(success, detail);
    }

    private void reportPairing(boolean success, String detail) {
        seState = success ? "paired" : "failed";
        logPairing((success ? "PAIRED  " : "FAILED  ") + detail);
        logBle("Vendor pairing " + (success ? "succeeded" : "failed") + " · " + detail);
        if (success) {
            connectionStatus.setText("Paired · " + connectedDeviceName);
            scanStatus.setText("●  PAIRED / READY");
        } else {
            connectionStatus.setText("Connected · pairing failed · " + detail);
            scanStatus.setText("●  CONNECTED / PAIRING FAILED · TAP FOR DIAGNOSTICS");
        }
    }

    /**
     * Starts the Berry account exchange, which is the vendor's other pairing stack. Returns false
     * when it cannot run, so the caller reports the SE failure instead.
     */
    private boolean startBerryFallback(String reason) {
        if (berryStarted || bluetoothAdapter == null) return false;
        String address = lastConnectAddress;
        if (address == null) return false;
        berryStarted = true;
        berryPhase = true;
        berryReader.reset();
        logPairing("SE account exchange failed · " + reason);
        logDeviceCapabilities();
        if (startBleBerry()) return true;
        logPairing("this watch has no aa01 write channel · using the classic link");
        startClassicBerry();
        return true;
    }

    /**
     * Records what the phone itself can see about the watch. {@code getType()} and the SDP record
     * list decide whether the classic (RFCOMM) path is physically possible at all: an LE-only
     * watch has no SPP service, no matter how it is bonded.
     */
    @SuppressWarnings("MissingPermission")
    private void logDeviceCapabilities() {
        BluetoothDevice device;
        try {
            device = bluetoothAdapter.getRemoteDevice(lastConnectAddress);
        } catch (IllegalArgumentException | SecurityException e) {
            return;
        }
        String type;
        int typeCode;
        String uuids;
        try {
            typeCode = device.getType();
            switch (typeCode) {
                case BluetoothDevice.DEVICE_TYPE_CLASSIC: type = "classic"; break;
                case BluetoothDevice.DEVICE_TYPE_LE: type = "LE only"; break;
                case BluetoothDevice.DEVICE_TYPE_DUAL: type = "dual"; break;
                case BluetoothDevice.DEVICE_TYPE_UNKNOWN: type = "unknown"; break;
                default: type = "type " + typeCode; break;
            }
        } catch (SecurityException e) {
            typeCode = BluetoothDevice.DEVICE_TYPE_UNKNOWN;
            type = "unavailable";
        }
        try {
            android.os.ParcelUuid[] records = device.getUuids();
            if (records == null || records.length == 0) {
                uuids = "none";
            } else {
                StringBuilder out = new StringBuilder();
                for (android.os.ParcelUuid record : records) {
                    if (out.length() > 0) out.append(", ");
                    out.append(record.getUuid());
                }
                uuids = out.toString();
            }
        } catch (SecurityException e) {
            uuids = "unavailable";
        }
        logPairing("device type " + type + " (" + typeCode + ")");
        logPairing("SDP records " + uuids);
    }

    /**
     * Berry over BLE: the vendor's {@code Berry-Test} stack writes Berry frames to aa02 and
     * receives them on a notify characteristic of the same service. It needs no system bond, so
     * it is tried before the classic socket.
     */
    private boolean startBleBerry() {
        if (berryWriteChar == null) return false;
        berryTransport = "ble";
        berrySerial = 0;
        berryStep = "user-id";
        berryStepAttempts = 1;
        scanStatus.setText("●  PAIRING / WATCH LINK");
        connectionStatus.setText("Pairing · opening the watch link to " + connectedDeviceName + "…");
        logPairing("trying the BLE Berry channel · write aa02, notify "
                + (berryNotifyChars.isEmpty() ? "none" : berryNotifyChars.size() + " char(s)"));
        // Device-side frames on this channel use the same private 2/3/4 exchange as SPP.
        sendBerryBindUserId();
        scheduleBerryStepTimeout("account identification");
        return true;
    }

    /**
     * Drops the BLE Berry channel. If nothing was ever heard on it, the classic socket is the last
     * transport left; the diagnostic lines above then say whether that is even possible.
     */
    private void abandonBleBerry(String detail) {
        if (berryFinished) return;
        if (!classicAttempted) {
            logPairing("BLE Berry channel silent · " + detail);
            berryReader.reset();
            startClassicBerry();
            return;
        }
        finishBerry(false, detail);
    }

    /** Classic Berry: Android bonding first (the vendor refuses the RFCOMM socket before it). */
    private void startClassicBerry() {
        if (classicAttempted) return;
        classicAttempted = true;
        cancelBerryStepTimeout();
        berryTransport = "spp";
        berryReader.reset();
        berryWriteChunks.clear();
        berryStep = "classic-link";
        berryStepAttempts = 1;
        logPairing("falling back to the classic (SPP) Berry pairing");
        scanStatus.setText("●  PAIRING / CLASSIC LINK");
        connectionStatus.setText("Pairing · opening the Bluetooth link to " + connectedDeviceName + "…");
        BluetoothDevice device;
        try {
            device = bluetoothAdapter.getRemoteDevice(lastConnectAddress);
        } catch (IllegalArgumentException e) {
            finishBerry(false, "the saved watch address is not valid");
            return;
        }
        int bondState;
        try {
            bondState = device.getBondState();
        } catch (SecurityException e) {
            finishBerry(false, "Bluetooth permission is needed to pair");
            return;
        }
        logPairing("bond state " + bondLabel(bondState));
        if (bondState == BluetoothDevice.BOND_BONDED) {
            openBerrySpp(device);
        } else if (bondState == BluetoothDevice.BOND_BONDING) {
            // Something else already started pairing; just wait for it to finish.
            pendingBondDevice = device;
            bondInProgress = true;
            bondRequestedByApp = false;
            scheduleClassicBondTimeout();
        } else {
            requestClassicBond(device);
        }
    }

    private static String bondLabel(int state) {
        switch (state) {
            case BluetoothDevice.BOND_NONE: return "not bonded";
            case BluetoothDevice.BOND_BONDING: return "bonding";
            case BluetoothDevice.BOND_BONDED: return "bonded";
            default: return "state " + state;
        }
    }

    @SuppressWarnings("MissingPermission")
    private void requestClassicBond(BluetoothDevice device) {
        pendingBondDevice = device;
        bondInProgress = true;
        bondRequestedByApp = false;
        try {
            boolean accepted = device.createBond();
            logPairing("createBond accepted=" + accepted);
            if (!accepted) {
                finishBerry(false, "Android refused to start Bluetooth pairing");
                return;
            }
        } catch (SecurityException e) {
            finishBerry(false, "Bluetooth permission is needed to pair");
            return;
        }
        scheduleClassicBondTimeout();
    }

    private void scheduleClassicBondTimeout() {
        cancelPairingTimeout();
        pairingTimeout = () -> {
            pairingTimeout = null;
            if (!bondInProgress) return;
            clearBondState();
            finishBerry(false, "Bluetooth pairing timed out · accept the prompt on both devices");
        };
        handler.postDelayed(pairingTimeout, CLASSIC_BOND_TIMEOUT_MS);
    }

    @SuppressWarnings("MissingPermission")
    private void openBerrySpp(BluetoothDevice device) {
        if (berryFinished) return;
        berryReader.reset();
        berryStep = "spp-connect";
        berryStepAttempts = 1;
        logPairing("opening the classic SPP socket");
        scanStatus.setText("●  PAIRING / CLASSIC LINK");
        connectionStatus.setText("Pairing · connecting over Bluetooth to " + connectedDeviceName + "…");
        scheduleBerryStepTimeout("classic link setup");
        BerrySpp.Listener listener = new BerrySpp.Listener() {
            @Override public void onLog(String message) {
                runOnUiThread(() -> logPairing(message));
            }

            @Override public void onConnected() {
                runOnUiThread(() -> {
                    cancelBerryStepTimeout();
                    berryStep = "user-id";
                    berryStepAttempts = 1;
                    connectionStatus.setText("Pairing · identifying this phone to " + connectedDeviceName + "…");
                    scanStatus.setText("●  PAIRING / ACCOUNT");
                    sendBerryBindUserId();
                    scheduleBerryStepTimeout("account identification");
                });
            }

            @Override public void onData(byte[] chunk) {
                runOnUiThread(() -> handleBerryChunk("classic", chunk));
            }

            @Override public void onFailed(String reason) {
                runOnUiThread(() -> finishBerry(false, reason));
            }
        };
        berrySpp = new BerrySpp(bluetoothAdapter, device, listener);
        berrySpp.connect();
    }

    /** Private cmd 2, the account identification the vendor sends on both Berry transports. */
    private void sendBerryBindUserId() {
        if (berryFinished) return;
        String token = openFitToken(lastConnectAddress);
        logPairing("account token " + token);
        sendBerry(BerryBinding.userIdFrame(berrySerial,
                token, phoneName(), Build.VERSION.RELEASE), "user-id");
    }

    /** The vendor sends {@code Build.MODEL} with its whitespace removed, then the OS release. */
    private static String phoneName() {
        String model = Build.MODEL == null ? "" : Build.MODEL;
        return model.replaceAll("\\s+", "");
    }

    private void handleBerryChunk(String transport, byte[] chunk) {
        if (chunk == null || chunk.length == 0) return;
        logPairing("RX " + transport + " · " + BerryBinding.hex(chunk));
        for (BerryFrame frame : berryReader.append(chunk)) handleBerryFrame(frame);
    }

    private void handleBerryFrame(BerryFrame frame) {
        logPairing("RX frame · " + BerryBinding.hex(frame.toBytes()));
        // The exchange is stop-and-wait: the vendor answers every command frame with a bare
        // 8-byte ack before it even parses the payload. Without it the watch stops sending.
        sendBerryAck(frame.serialNo());
        if (!frame.isPrivate()) {
            if (frame.isProtobuf()) {
                int type = BerrySewear.readType(frame.payload());
                int id = BerrySewear.readId(frame.payload());
                logPairing("SEWear (Berry) · type " + type + " id " + id
                        + " · " + BerryBinding.hex(frame.payload()));
                if (type == BerrySewear.TYPE_ACCOUNT) {
                    logPairing("watch speaks the Berry account schema · type=ACCOUNT id=" + id);
                }
            } else {
                logPairing("ignoring Berry event " + frame.event() + " type " + frame.type());
            }
            return;
        }
        int cmdId = frame.privateCmdId();
        byte[] data = frame.privateData();
        if (cmdId == BerrySewear.PRIVATE_CMD_USER_ID) {
            onBerryDeviceInfo(data);
        } else if (cmdId == BerrySewear.PRIVATE_CMD_BIND) {
            onBerryBindStatus(data);
        } else if (cmdId == BerrySewear.PRIVATE_CMD_BIND_RESULT) {
            onBerryBindResult(data);
        } else {
            logPairing("private cmd " + cmdId + " · " + BerryBinding.hex(data));
        }
    }

    private void onBerryDeviceInfo(byte[] data) {
        if (berryFinished || !"user-id".equals(berryStep)) return;
        cancelBerryStepTimeout();
        BerryBinding.DeviceInfo info = BerryBinding.DeviceInfo.parse(data);
        logPairing("watch info · " + info);
        if (info == null) {
            finishBerry(false, "the watch sent an unreadable account reply");
            return;
        }
        if (info.isBind && !info.isSameUser) {
            finishBerry(false, "the watch is bound to another account · unbind it in the watch settings");
            return;
        }
        if (info.isBind) {
            finishBerry(true, "the watch is already bound to this account");
            return;
        }
        berryStep = "bind";
        berryStepAttempts = 1;
        connectionStatus.setText("Pairing · asking " + connectedDeviceName + " to confirm…");
        scanStatus.setText("●  PAIRING / BIND REQUEST");
        sendBerry(BerryBinding.bindFrame(berrySerial), "bind-request");
        scheduleBerryStepTimeout("bind request");
    }

    private void onBerryBindStatus(byte[] data) {
        if (berryFinished || !"bind".equals(berryStep)) return;
        cancelBerryStepTimeout();
        int status = data.length == 0 ? -1 : data[0] & 0xff;
        logPairing("bind status · " + BerryBinding.bindStatusLabel(status));
        if (status != BerryBinding.BIND_OK) {
            finishBerry(false, BerryBinding.bindStatusLabel(status));
            return;
        }
        berryStep = "bind-result";
        berryStepAttempts = 1;
        connectionStatus.setText("Pairing · confirming with " + connectedDeviceName + "…");
        scanStatus.setText("●  PAIRING / CONFIRM");
        sendBerry(BerryBinding.bindResultFrame(berrySerial, true), "bind-confirm");
        scheduleBerryStepTimeout("bind confirmation");
    }

    private void onBerryBindResult(byte[] data) {
        if (berryFinished || !"bind-result".equals(berryStep)) return;
        cancelBerryStepTimeout();
        int status = data.length == 0 ? -1 : data[0] & 0xff;
        logPairing("bind confirmation · " + BerryBinding.bindResultLabel(status));
        if (status == 0) {
            finishBerry(true, "Berry bind confirmed");
        } else {
            clearStoredToken(lastConnectAddress);
            finishBerry(false, BerryBinding.bindResultLabel(status));
        }
    }

    /** Bare 8-byte answer to one watch frame; it reuses the request's serial number. */
    private void sendBerryAck(int serialNo) {
        if (berryFinished) return;
        byte[] ack = BerryFrame.ackBytes(serialNo, 0);
        logPairing("TX ack · " + BerryBinding.hex(ack));
        if ("ble".equals(berryTransport)) {
            queueBerryBleWrite(ack);
        } else if (berrySpp != null) {
            berrySpp.send(ack);
        }
    }

    /** Sends one Berry frame on whichever Berry transport is active. */
    private void sendBerry(byte[] frame, String label) {
        if (frame == null || frame.length == 0) return;
        logPairing("TX " + label + " · " + BerryBinding.hex(frame));
        if ("ble".equals(berryTransport)) {
            queueBerryBleWrite(frame);
        } else if (berrySpp != null) {
            berrySpp.send(frame);
        } else {
            finishBerry(false, "the " + berryTransport + " link is gone");
            return;
        }
        berrySerial = BerryFrame.nextSerial(berrySerial);
    }

    /**
     * GATT allows one outstanding write, so frames are queued and split to fit the negotiated
     * MTU. Everything the bind sends fits in one 247-byte write, but the split keeps this correct
     * on a watch that only negotiates the default MTU.
     */
    private void queueBerryBleWrite(byte[] frame) {
        int max = Math.max(20, seMtu - 3);
        for (int offset = 0; offset < frame.length; offset += max) {
            int length = Math.min(max, frame.length - offset);
            byte[] part = new byte[length];
            System.arraycopy(frame, offset, part, 0, length);
            berryWriteChunks.add(part);
        }
        pumpBerryBleWrites();
    }

    @SuppressWarnings("MissingPermission")
    private void pumpBerryBleWrites() {
        if (berryWriteBusy || berryWriteChunks.isEmpty() || berryFinished) return;
        if (!"ble".equals(berryTransport) || berryWriteChar == null) {
            berryWriteChunks.clear();
            return;
        }
        byte[] part = berryWriteChunks.get(0);
        berryWriteBusy = true;
        if (writeGattValue(berryWriteChar, part)) {
            berryWriteRetries = 0;
            handler.removeCallbacks(berryWriteTimeout);
            handler.postDelayed(berryWriteTimeout, BERRY_WRITE_TIMEOUT_MS);
            return;
        }
        berryWriteBusy = false;
        if (berryWriteRetries++ < 2) {
            // The stack can refuse a write while the previous one is still settling.
            handler.postDelayed(this::pumpBerryBleWrites, 120L);
            return;
        }
        berryWriteRetries = 0;
        berryWriteChunks.remove(0);
        logPairing("write to aa02 was rejected");
        if (berryWriteChunks.isEmpty()) finishBerry(false, "the watch rejected the write on aa02");
    }

    /** Releases a chunk whose onCharacteristicWrite callback never came. */
    private void onBerryWriteTimeout() {
        if (!berryWriteBusy) return;
        berryWriteBusy = false;
        if (!berryWriteChunks.isEmpty()) berryWriteChunks.remove(0);
        logPairing("aa02 write callback never arrived · continuing");
        pumpBerryBleWrites();
    }

    private void scheduleBerryStepTimeout(String what) {
        cancelBerryStepTimeout();
        berryStepTimeout = () -> {
            berryStepTimeout = null;
            if (berryFinished) return;
            if ("user-id".equals(berryStep) && berryStepAttempts < 2) {
                berryStepAttempts++;
                logPairing("no reply for " + what + " · retrying");
                sendBerryBindUserId();
                scheduleBerryStepTimeout(what);
                return;
            }
            if ("user-id".equals(berryStep) && "ble".equals(berryTransport) && berryStepAttempts < 3) {
                // Last probe on this channel: the Berry schema's own account query, which is a
                // SEWear{type=ACCOUNT, id=INQUIRY_BINDING_STATUS}. A reply proves this firmware
                // speaks the Berry account schema even though the private bind did not answer.
                berryStepAttempts++;
                logPairing("no private reply · probing the Berry account query");
                sendBerry(BerryFrame.protobuf(berrySerial, BerrySewear.bindStateRequest()).toBytes(),
                        "account-query");
                scheduleBerryStepTimeout("account query");
                return;
            }
            if ("user-id".equals(berryStep) && "ble".equals(berryTransport)) {
                abandonBleBerry("no reply to the account commands");
                return;
            }
            if ("bind".equals(berryStep) && berryStepAttempts < 2) {
                berryStepAttempts++;
                logPairing("no reply for " + what + " · retrying");
                sendBerry(BerryBinding.bindFrame(berrySerial), "bind-request");
                scheduleBerryStepTimeout(what);
                return;
            }
            if ("bind-result".equals(berryStep) && berryStepAttempts < 2) {
                berryStepAttempts++;
                logPairing("no reply for " + what + " · retrying");
                sendBerry(BerryBinding.bindResultFrame(berrySerial, true), "bind-confirm");
                scheduleBerryStepTimeout(what);
                return;
            }
            finishBerry(false, "no reply from the watch for " + what);
        };
        handler.postDelayed(berryStepTimeout, BERRY_STEP_TIMEOUT_MS);
    }

    private void cancelBerryStepTimeout() {
        if (berryStepTimeout != null) {
            handler.removeCallbacks(berryStepTimeout);
            berryStepTimeout = null;
        }
    }

    private void finishBerry(boolean success, String detail) {
        if (berryFinished) return;
        berryFinished = true;
        berryPhase = false;
        handler.removeCallbacks(berryWriteTimeout);
        berryWriteChunks.clear();
        berryWriteBusy = false;
        cancelBerryStepTimeout();
        cancelPairingTimeout();
        if (bondInProgress) clearBondState();
        if (!success && berrySpp != null) {
            berrySpp.close();
            berrySpp = null;
        }
        reportPairing(success, detail);
    }

    /**
     * The vendor client derives its account token from a random UUID plus a small number. The
     * watch stores it, so it must survive reconnects; it is kept per watch address.
     */
    private String openFitToken(String address) {
        String key = KEY_WATCH_TOKEN_PREFIX + (address == null ? "unknown" : address);
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String token = prefs.getString(key, null);
        if (token == null || token.isEmpty()) {
            token = (UUID.randomUUID().toString()
                    + (10 + new Random().nextInt(9990)) + "openfit").substring(30);
            prefs.edit().putString(key, token).apply();
        }
        return token;
    }

    private String storedToken(String address) {
        if (address == null) return null;
        String token = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_WATCH_TOKEN_PREFIX + address, null);
        return token == null || token.isEmpty() ? null : token;
    }

    private void clearStoredToken(String address) {
        if (address == null) return;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove(KEY_WATCH_TOKEN_PREFIX + address).apply();
    }

    @SuppressWarnings("MissingPermission")
    private boolean writeSe(byte[] value) {
        return writeGattValue(seWriteChar, value);
    }

    @SuppressWarnings("MissingPermission")
    private boolean writeGattValue(BluetoothGattCharacteristic characteristic, byte[] value) {
        BluetoothGatt source = gatt;
        if (source == null || characteristic == null || value == null) return false;
        int writeType = (characteristic.getProperties()
                & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                ? BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                : BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                return source.writeCharacteristic(characteristic, value, writeType)
                        == BluetoothGatt.GATT_SUCCESS;
            }
            characteristic.setWriteType(writeType);
            characteristic.setValue(value);
            return source.writeCharacteristic(characteristic);
        } catch (SecurityException e) {
            return false;
        }
    }

    /** Answers an inbound announce/chunk on the characteristic that delivered it. */
    private void writeSeAck(int code) {
        BluetoothGattCharacteristic characteristic = seInboundChar;
        if (characteristic == null) return;
        byte[] frame = SeGatt.control(code);
        logPairing("TX " + SeGatt.controlLabel(code) + " · " + hex(frame));
        if (!writeGattValue(characteristic, frame)) {
            logPairing("ack write failed on " + shortUuid(characteristic.getUuid()));
        }
    }

    private void handleCharacteristicWrite(BluetoothGatt source,
                                           BluetoothGattCharacteristic characteristic, int status) {
        if (source != gatt || characteristic == null) return;
        if (berryWriteChar != null && berryWriteChar.getUuid().equals(characteristic.getUuid())) {
            // One GATT write at a time: release the next queued chunk, whatever the result.
            // A stale callback (the watchdog already released this chunk) must not eat the next.
            if (!berryWriteBusy) return;
            handler.removeCallbacks(berryWriteTimeout);
            if (!berryWriteChunks.isEmpty()) berryWriteChunks.remove(0);
            berryWriteBusy = false;
            berryWriteRetries = 0;
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logPairing("aa02 write failed · GATT " + status);
            }
            pumpBerryBleWrites();
            return;
        }
        if (status == BluetoothGatt.GATT_SUCCESS) return;
        if (seWriteChar != null && seWriteChar.getUuid().equals(characteristic.getUuid())) {
            logPairing("TX callback failed · GATT " + status);
        }
    }

    private void handleNotification(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (characteristic == null || value == null) return;
        BluetoothGattService service = characteristic.getService();
        if (service != null && BerryProfile.SERVICE.equals(service.getUuid())) {
            handleSeNotification(characteristic, value);
        } else if (service != null && BerryProfile.AA01_SERVICE.equals(service.getUuid())) {
            // Only while the BLE Berry attempt owns the shared frame reader. The classic socket
            // has its own stream, and mixing the two would corrupt reassembly.
            if ("ble".equals(berryTransport)) handleBleBerryNotification(characteristic, value);
        } else {
            updateHeartRate(value);
        }
    }

    /**
     * One notification from the aa01 service. Berry frames can be split across notifications, so
     * everything goes through the shared reassembler.
     */
    private void handleBleBerryNotification(BluetoothGattCharacteristic characteristic,
                                            byte[] value) {
        if (value.length == 0) return;
        logPairing("RX " + shortUuid(characteristic.getUuid()) + " · " + hex(value));
        for (BerryFrame frame : berryReader.append(value)) handleBerryFrame(frame);
    }

    /**
     * Dispatches one notification from the {@code 16186f00} service. The watch announces every
     * reply, expects the ready control frame, then sends indexed chunks. Control frames from the
     * watch arrive on the write characteristic (16186f02).
     */
    private void handleSeNotification(BluetoothGattCharacteristic characteristic, byte[] value) {
        if (value.length == 0) return;
        logPairing("RX " + shortUuid(characteristic.getUuid()) + " · " + hex(value));
        if (SeGatt.isAnnounce(value)) {
            int count = SeGatt.announceCount(value);
            seInboundChar = characteristic;
            if (!seTransfer.start(count)) {
                logPairing("inbound announce with implausible chunk count " + count);
                return;
            }
            logPairing("inbound transfer · " + count + " chunk(s)");
            writeSeAck(SeGatt.CODE_READY);
            return;
        }
        if (SeGatt.isControl(value)) {
            int code = SeGatt.controlCode(value);
            int payload = SeGatt.controlPayload(value);
            logPairing("control " + SeGatt.controlLabel(code)
                    + (code == SeGatt.CODE_RESEND ? " " + payload : ""));
            if (code == SeGatt.CODE_READY) onSeReady();
            else if (code == SeGatt.CODE_RESEND) resendSeChunk(payload);
            else if (code == SeGatt.CODE_BUSY) onSeBusy();
            // CODE_DONE (01 03) arrives after the last chunk. The app-level SEWear reply is
            // what advances the bind, so the done frame needs no action here.
            return;
        }
        if (seTransfer.isActive()) {
            byte[] full = seTransfer.accept(SeGatt.packetIndex(value), SeGatt.packetData(value));
            if (full != null) {
                writeSeAck(SeGatt.CODE_RECEIVED);
                onSeMessage(full);
            }
            return;
        }
        logPairing("unframed notification · " + hex(value));
    }

    private void logPairing(String message) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        pairingLog.add(time + "  " + message);
        while (pairingLog.size() > 60) pairingLog.remove(0);
    }

    private static String hex(byte[] data) {
        if (data == null) return "null";
        StringBuilder out = new StringBuilder(data.length * 2);
        for (byte b : data) out.append(String.format(Locale.US, "%02X", b & 0xff));
        return out.toString();
    }

    private void resetPairingSession() {
        preparePairingAttempt();
        pairingLog.clear();
    }

    /** Clears per-link pairing state while keeping the persistent token and diagnostic log. */
    private void preparePairingAttempt() {
        cancelSeTimeouts();
        seTransfer.reset();
        seWriteQueue.clear();
        seCurrentWrite = null;
        seAwaitingReady = false;
        seAnnounceRetries = 0;
        seWriteChar = null;
        seInboundChar = null;
        seLastPayload = null;
        seLastLabel = null;
        seMtu = 23;
        seSetupStarted = false;
        sePairStarted = false;
        sePairFinished = false;
        seStepAttempts = 0;
        seStep = "idle";
        seState = "not started";
        cancelBerryStepTimeout();
        berryReader.reset();
        berryStep = "idle";
        berryStepAttempts = 0;
        berryTransport = "";
        berryStarted = false;
        berryPhase = false;
        berryFinished = false;
        handler.removeCallbacks(berryWriteTimeout);
        berryWriteChunks.clear();
        berryWriteBusy = false;
        classicAttempted = false;
        if (berrySpp != null) {
            berrySpp.close();
            berrySpp = null;
        }
    }

    private void cancelSeMtuTimeout() {
        if (seMtuTimeout != null) {
            handler.removeCallbacks(seMtuTimeout);
            seMtuTimeout = null;
        }
    }

    private void cancelSeStepTimeout() {
        if (seStepTimeout != null) {
            handler.removeCallbacks(seStepTimeout);
            seStepTimeout = null;
        }
    }

    private void cancelSeAckTimeout() {
        if (seAckTimeout != null) {
            handler.removeCallbacks(seAckTimeout);
            seAckTimeout = null;
        }
    }

    private void cancelSeTimeouts() {
        cancelSeMtuTimeout();
        cancelSeStepTimeout();
        cancelSeAckTimeout();
    }

    private static final class SeWrite {
        final byte[] data;
        final String label;
        int offset;

        SeWrite(byte[] data, String label) {
            this.data = data;
            this.label = label;
        }
    }

    /** Retries a failed attempt a bounded number of times, then reports the real reason. */
    @SuppressWarnings("MissingPermission")
    private void handleConnectionFailure(BluetoothDevice device, String reason) {
        cancelConnectionTimeout();
        closeGatt();
        logBle("Attempt failed: " + reason);
        if (bondInProgress) {
            connectionStatus.setText("Waiting for Android Bluetooth pairing to finish…");
            scanStatus.setText("Accept the system pairing prompt if shown");
            return;
        }
        if (device != null && connectAttempt < MAX_CONNECT_ATTEMPTS
                && lastConnectAddress != null
                && lastConnectAddress.equals(device.getAddress())) {
            scanStatus.setText("●  RETRY " + (connectAttempt + 1) + " / " + MAX_CONNECT_ATTEMPTS);
            connectionStatus.setText("Connection failed · " + reason + " · retrying");
            cancelRetryConnection();
            retryConnection = () -> {
                retryConnection = null;
                if (lastConnectAddress != null && lastConnectAddress.equals(device.getAddress())) {
                    startConnectionAttempt(device);
                }
            };
            handler.postDelayed(retryConnection, RETRY_DELAY_MS);
            return;
        }
        int bondState = BluetoothDevice.BOND_NONE;
        try {
            if (device != null) bondState = device.getBondState();
        } catch (SecurityException ignored) { }
        if (device != null && !bondFallbackTried && !bondInProgress
                && bondState != BluetoothDevice.BOND_BONDED) {
            // Last resort: some watches only accept an encrypted link. Match NoiseFit's
            // bond-first behaviour here instead of leaving the user with a failed connect.
            bondFallbackTried = true;
            logBle("Direct attempts exhausted · falling back to Android pairing");
            connectionStatus.setText("Direct connection failed · opening Android Bluetooth pairing");
            scanStatus.setText("●  PAIRING / ACCEPT PROMPT");
            startBonding(device);
            return;
        }
        connectionStatus.setText(bondState == BluetoothDevice.BOND_BONDED
                ? "Could not connect · " + reason
                        + " · remove the watch in Android Bluetooth settings, then pair again"
                : "Could not connect · " + reason + " · keep the watch awake and retry");
        scanStatus.setText("●  NOT CONNECTED / TAP FOR DIAGNOSTICS");
    }

    private void registerBondReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            // ACTION_BOND_STATE_CHANGED is a protected broadcast, so only the system/Bluetooth
            // stack can send it. RECEIVER_EXPORTED is required here: the Bluetooth stack runs
            // under its own privileged UID, and NOT_EXPORTED receivers can miss its broadcasts.
            registerReceiver(bondReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(bondReceiver, filter);
        }
        bondReceiverRegistered = true;
    }

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) return;
            BluetoothDevice device;
            if (Build.VERSION.SDK_INT >= 33) {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            } else {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            }
            if (device == null || pendingBondDevice == null || !bondInProgress) return;
            try {
                if (!pendingBondDevice.getAddress().equals(device.getAddress())) return;
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                int previousState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);
                logBle("Bond state " + previousState + " -> " + state);
                if (state == BluetoothDevice.BOND_BONDED) {
                    if (berryPhase) {
                        clearBondState();
                        logPairing("bonded · opening the classic link");
                        openBerrySpp(device);
                        return;
                    }
                    boolean shouldReconnect = bondRequestedByApp;
                    clearBondState();
                    if (shouldReconnect) {
                        connectionStatus.setText("Bluetooth pairing accepted · reconnecting…");
                        scanStatus.setText("●  PAIRED / RECONNECTING");
                        connectAttempt = 0;
                        connectionReady = false;
                        lastConnectAddress = device.getAddress();
                        handler.postDelayed(() -> {
                            if (lastConnectAddress != null
                                    && lastConnectAddress.equals(device.getAddress())) {
                                startConnectionAttempt(device);
                            }
                        }, 500L);
                    }
                } else if (state == BluetoothDevice.BOND_NONE && previousState == BluetoothDevice.BOND_BONDING) {
                    if (berryPhase) {
                        clearBondState();
                        finishBerry(false, "the watch rejected the Bluetooth pairing");
                        return;
                    }
                    boolean retryPairing = bondRequestedByApp;
                    clearBondState();
                    if (retryPairing) {
                        connectionStatus.setText("Bluetooth pairing was cancelled or rejected");
                        scanStatus.setText("Keep the watch awake and accept its pairing prompt, then retry");
                        logBle("Bond failed");
                    }
                }
            } catch (SecurityException e) {
                clearBondState();
                connectionStatus.setText("Bluetooth permission is needed to finish pairing");
                logBle("Bond receiver SecurityException");
            }
        }
    };

    private void clearBondState() {
        cancelPairingTimeout();
        bondInProgress = false;
        bondRequestedByApp = false;
        pendingBondDevice = null;
    }

    /**
     * Called only when the watch itself reports that the link needs authentication/encryption.
     * That is the safe moment to start Android bonding; connecting first avoids blocking the
     * whole flow on watches that never bond.
     */
    @SuppressWarnings("MissingPermission")
    private boolean handleGattSecurityFailure(BluetoothDevice device, int status) {
        if (status != BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION
                && status != BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION) return false;
        logBle("Watch requested Bluetooth security · GATT " + status);
        cancelConnectionTimeout();
        closeGatt();
        connectionReady = false;
        int bondState;
        try {
            bondState = device.getBondState();
        } catch (SecurityException e) {
            connectionStatus.setText("Bluetooth permission is needed to pair the watch");
            scanStatus.setText("Allow Nearby devices access, then retry");
            return true;
        }
        if (bondState == BluetoothDevice.BOND_BONDED) {
            connectionStatus.setText("The watch rejected the paired connection (GATT " + status
                    + ") · remove it in Android Bluetooth settings, then pair again");
            scanStatus.setText("Saved Bluetooth pairing may be out of date");
            logBle("Security rejected while already bonded");
            return true;
        }
        if (bondInProgress) return true;
        connectionStatus.setText("Watch requests Bluetooth pairing · accept the system prompt");
        scanStatus.setText("●  PAIRING / ACCEPT PROMPT");
        startBonding(device);
        return true;
    }

    @SuppressWarnings("MissingPermission")
    private void startBonding(BluetoothDevice device) {
        pendingBondDevice = device;
        bondInProgress = true;
        bondRequestedByApp = true;
        cancelPairingTimeout();
        try {
            if (!device.createBond()) {
                clearBondState();
                connectionStatus.setText("Android could not start Bluetooth pairing");
                scanStatus.setText("Retry while the watch is awake and nearby");
                logBle("createBond returned false");
                return;
            }
        } catch (SecurityException e) {
            clearBondState();
            connectionStatus.setText("Bluetooth permission is needed to pair the watch");
            scanStatus.setText("Allow Nearby devices access, then retry");
            logBle("createBond SecurityException");
            return;
        } catch (IllegalStateException e) {
            clearBondState();
            connectionStatus.setText("Could not start system Bluetooth pairing");
            scanStatus.setText("Retry while the watch is awake and nearby");
            logBle("createBond IllegalStateException");
            return;
        }
        pairingTimeout = () -> {
            pairingTimeout = null;
            if (!bondInProgress) return;
            clearBondState();
            connectionStatus.setText("Bluetooth pairing timed out · keep the watch awake, then retry");
            scanStatus.setText("●  PAIRING TIMED OUT");
            logBle("Pairing timed out");
        };
        handler.postDelayed(pairingTimeout, PAIRING_TIMEOUT_MS);
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
            lastConnectAddress = address;
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

    private void cancelNotificationTimeout() {
        if (notificationTimeout != null) {
            handler.removeCallbacks(notificationTimeout);
            notificationTimeout = null;
        }
    }

    private void cancelPairingTimeout() {
        if (pairingTimeout != null) {
            handler.removeCallbacks(pairingTimeout);
            pairingTimeout = null;
        }
    }

    private void cancelRetryConnection() {
        if (retryConnection != null) {
            handler.removeCallbacks(retryConnection);
            retryConnection = null;
        }
    }

    @SuppressWarnings("MissingPermission")
    private void closeGatt() {
        cancelConnectionTimeout();
        cancelNotificationTimeout();
        cancelSeTimeouts();
        seWriteQueue.clear();
        seCurrentWrite = null;
        seAwaitingReady = false;
        if (sePairStarted && !sePairFinished) {
            sePairFinished = true;
            seState = "disconnected";
            logPairing("disconnected during pairing");
        }
        if (gatt != null) {
            try { gatt.disconnect(); gatt.close(); } catch (SecurityException ignored) { }
            gatt = null;
        }
    }

    /** Short on-device connection log so a failed test can be reported without adb. */
    private void logBle(String message) {
        String time = new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        connectionLog.add(time + "  " + message);
        while (connectionLog.size() > 60) connectionLog.remove(0);
    }

    private static String shortUuid(UUID uuid) {
        if (uuid == null) return "unknown";
        String value = uuid.toString();
        return value.length() > 8 ? value.substring(0, 8) : value;
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        scanTimeout = null;
        retryConnection = null;
        stopScan();
        closeGatt();
        if (berrySpp != null) {
            berrySpp.close();
            berrySpp = null;
        }
        classicAttempted = false;
        berryPhase = false;
        berryWriteChunks.clear();
        berryWriteBusy = false;
        clearBondState();
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
