
package com.android.systemui.statusbar.toggles;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BrightnessController;
import com.android.systemui.statusbar.policy.BrightnessController.BrightnessStateChangeCallback;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Yo dog, I heard you like toggles.
 */
public class XzibitToggler {

    private static final String TOGGLE_PIPE = "|";

    public static final String USER_TOGGLE = "USER";
    public static final String BRIGHTNESS_TOGGLE = "BRIGHTNESS";
    public static final String SETTINGS_TOGGLE = "SETTINGS";
    public static final String WIFI_TOGGLE = "WIFI";
    public static final String SIGNAL_TOGGLE = "SIGNAL";
    public static final String ROTATE_TOGGLE = "ROTATE";
    public static final String CLOCK_TOGGLE = "CLOCK";
    public static final String GPS_TOGGLE = "GPS";
    public static final String IME_TOGGLE = "IME";
    public static final String BATTERY_TOGGLE = "BATTERY";
    public static final String AIRPLANE_TOGGLE = "AIRPLANE_MODE";
    public static final String BLUETOOTH_TOGGLE = "BLUETOOTH";
    public static final String SWAGGER_TOGGLE = "SWAGGER";
    public static final String VIBRATE_TOGGLE = "VIBRATE";
    public static final String SILENT_TOGGLE = "SILENT";
    public static final String FCHARGE_TOGGLE = "FCHARGE";
    public static final String SYNC_TOGGLE = "SYNC";
    public static final String NFC_TOGGLE = "NFC";
    public static final String TORCH_TOGGLE = "TORCH";
    public static final String WIFI_TETHER_TOGGLE = "WIFITETHER";
    // public static final String BT_TETHER_TOGGLE = "BTTETHER";
    public static final String USB_TETHER_TOGGLE = "USBTETHER";
    public static final String TWOG_TOGGLE = "2G";
    public static final String LTE_TOGGLE = "LTE";
    public static final String FAV_CONTACT_TOGGLE = "FAVCONTACT";
    public static final String SOUND_STATE_TOGGLE = "SOUNDSTATE";
    public static final String NAVBAR_HIDE_TOGGLE = "NAVBARHIDE";
    public static final String QUICKRECORD_TOGGLE = "QUICKRECORD";

    private static final String TAG = XzibitToggler.class.getSimpleName();

    private int mStyle;

    public static final int STYLE_TILE = 0;
    public static final int STYLE_SWITCH = 1;
    public static final int STYLE_TRADITIONAL = 2;

    private ViewGroup[] mContainers = new ViewGroup[3];

    Context mContext;
    private String userToggles = null;
    ArrayList<BaseToggle> mToggles = new ArrayList<BaseToggle>();

    private View mFlipSettingsView;

    private HashMap<String, Class<? extends BaseToggle>> toggleMap;

    private HashMap<String, Class<? extends BaseToggle>> getToggleMap() {
        if (toggleMap == null) {
            toggleMap = new HashMap<String, Class<? extends BaseToggle>>();
            toggleMap.put(USER_TOGGLE, UserToggle.class);
            toggleMap.put(BRIGHTNESS_TOGGLE, BrightnessToggle.class);
            toggleMap.put(SETTINGS_TOGGLE, SettingsToggle.class);
            toggleMap.put(WIFI_TOGGLE, WifiToggle.class);
            toggleMap.put(SIGNAL_TOGGLE, SignalToggle.class);
            toggleMap.put(ROTATE_TOGGLE, RotateToggle.class);
            toggleMap.put(CLOCK_TOGGLE, ClockToggle.class);
            toggleMap.put(GPS_TOGGLE, GpsToggle.class);
            toggleMap.put(IME_TOGGLE, ImeToggle.class);
            toggleMap.put(BATTERY_TOGGLE, BatteryToggle.class);
            toggleMap.put(AIRPLANE_TOGGLE, AirplaneModeToggle.class);
            toggleMap.put(BLUETOOTH_TOGGLE, BluetoothToggle.class);
            toggleMap.put(SWAGGER_TOGGLE, SwaggerToggle.class);
            toggleMap.put(VIBRATE_TOGGLE, VibrateToggle.class);
            toggleMap.put(SILENT_TOGGLE, SilentToggle.class);
            toggleMap.put(FCHARGE_TOGGLE, FastChargeToggle.class);
            toggleMap.put(SYNC_TOGGLE, SyncToggle.class);
            toggleMap.put(NFC_TOGGLE, NfcToggle.class);
            toggleMap.put(TORCH_TOGGLE, TorchToggle.class);
            toggleMap.put(WIFI_TETHER_TOGGLE, WifiApToggle.class);
            toggleMap.put(USB_TETHER_TOGGLE, UsbTetherToggle.class);
            toggleMap.put(TWOG_TOGGLE, TwoGToggle.class);
            toggleMap.put(LTE_TOGGLE, LteToggle.class);
            toggleMap.put(FAV_CONTACT_TOGGLE, FavoriteUserToggle.class);
            toggleMap.put(SOUND_STATE_TOGGLE, SoundStateToggle.class);
            toggleMap.put(NAVBAR_HIDE_TOGGLE, NavbarHideToggle.class);
            toggleMap.put(QUICKRECORD_TOGGLE, QuickRecordToggle.class);
            // toggleMap.put(BT_TETHER_TOGGLE, null);
        }
        return toggleMap;
    }

    public XzibitToggler(Context c) {
        mContext = c;
        new SettingsObserver(new Handler()).observe();
        new SoundObserver(new Handler()).observe();
    }

    private BluetoothController bluetoothController;
    private NetworkController networkController;
    private BatteryController batteryController;
    private LocationController locationController;
    private BrightnessController brightnessController;

    public void setControllers(BluetoothController bt, NetworkController net,
            BatteryController batt, LocationController loc, BrightnessController screen) {
        bluetoothController = bt;
        networkController = net;
        batteryController = batt;
        locationController = loc;
        brightnessController = screen;
    }

    public void setFlipButton(View button) {
        mFlipSettingsView = button;
    }

    private void setupTiles() {
        if (mContainers[STYLE_TILE] != null) {
            updateToggleList();
            FrameLayout.LayoutParams params = getTileParams(mContext);
            mContainers[STYLE_TILE].removeAllViews();
            for (BaseToggle toggle : mToggles) {
                QuickSettingsTileView tile = toggle.createTileView();
                mContainers[STYLE_TILE].addView(tile, params);
            }
            mContainers[STYLE_TILE].setVisibility(View.VISIBLE);
        }
    }

    public void setContainer(ViewGroup container, int style) {
        Log.d(TAG, "set container for style: " + style);
        if (container == null) {
            Log.d(TAG, "container was null for style: " + style);
            return;
        }
        mContainers[style] = container;
    }

    private void setupTraditional() {
        int widgetsPerRow = 6;
        View toggleSpacer;

        if (mContainers[STYLE_TRADITIONAL] != null) {
            updateToggleList();

            mContainers[STYLE_TRADITIONAL].removeAllViews();
            ArrayList<LinearLayout> rows = new ArrayList<LinearLayout>();
            rows.add(new LinearLayout(mContext)); // add first row

            LinearLayout.LayoutParams params = getTraditionalToggleParams(mContext);

            for (int i = 0; i < mToggles.size(); i++) {
                if (widgetsPerRow > 0 && i % widgetsPerRow == 0) {
                    // new row
                    rows.add(new LinearLayout(mContext));
                }
                rows.get(rows.size() - 1)
                        .addView(mToggles.get(i).createTraditionalView(),
                                params);
            }

            for (LinearLayout row : rows)
                mContainers[STYLE_TRADITIONAL].addView(row);

            mContainers[STYLE_TRADITIONAL].setVisibility(View.VISIBLE);
        }
    }

    private void updateToggleList() {
        for (BaseToggle t : mToggles) {
            t.cleanup();
        }
        mToggles.clear();
        HashMap<String, Class<? extends BaseToggle>> map = getToggleMap();
        ArrayList<String> toots = getToggles();
        for (String toggleIdent : toots) {
            try {
                Class<? extends BaseToggle> theclass = map.get(toggleIdent);
                BaseToggle toggle = theclass.newInstance();
                toggle.init(mContext, mStyle);
                mToggles.add(toggle);

                if (networkController != null && toggle instanceof NetworkSignalChangedCallback) {
                    networkController
                            .addNetworkSignalChangedCallback((NetworkSignalChangedCallback) toggle);
                }

                if (bluetoothController != null && toggle instanceof BluetoothStateChangeCallback) {
                    bluetoothController
                            .addStateChangedCallback((BluetoothStateChangeCallback) toggle);
                }

                if (batteryController != null && toggle instanceof BatteryStateChangeCallback) {
                    batteryController.addStateChangedCallback((BatteryStateChangeCallback) toggle);
                }

                if (locationController != null && toggle instanceof LocationGpsStateChangeCallback) {
                    locationController
                            .addStateChangedCallback((LocationGpsStateChangeCallback) toggle);
                }

                if (brightnessController != null && toggle instanceof BrightnessStateChangeCallback) {
                    brightnessController.addStateChangedCallback((BrightnessStateChangeCallback)
                            toggle);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private ArrayList<String> getToggles() {
        if (userToggles == null)
            return getDefaultTiles();

        ArrayList<String> tiles = new ArrayList<String>();
        String[] splitter = userToggles.split("\\" + TOGGLE_PIPE);
        for (String toggle : splitter) {
            tiles.add(toggle);
        }

        return tiles;
    }

    private ArrayList<String> getDefaultTiles() {
        ArrayList<String> tiles = new ArrayList<String>();
        tiles.add(USER_TOGGLE);
        tiles.add(BRIGHTNESS_TOGGLE);
        tiles.add(SETTINGS_TOGGLE);
        tiles.add(WIFI_TOGGLE);
        if (deviceSupportsTelephony()) {
            tiles.add(SIGNAL_TOGGLE);
        }
        if (mContext.getResources().getBoolean(R.bool.quick_settings_show_rotation_lock)) {
            tiles.add(ROTATE_TOGGLE);
        }
        tiles.add(BATTERY_TOGGLE);
        tiles.add(AIRPLANE_TOGGLE);
        if (deviceSupportsBluetooth()) {
            tiles.add(BLUETOOTH_TOGGLE);
        }
        return tiles;
    }

    public void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        userToggles = Settings.System.getString(resolver, Settings.System.QUICK_TOGGLES);
        int columnCount = Settings.System.getInt(resolver, Settings.System.QUICK_TOGGLES_PER_ROW,
                mContext.getResources().getInteger(R.integer.quick_settings_num_columns));

        mStyle = Settings.System.getInt(resolver, Settings.System.TOGGLES_STYLE,
                XzibitToggler.STYLE_TILE);

        for (int i = 0; i < mContainers.length; i++) {
            if (mContainers[i] != null) {
                mContainers[i].removeAllViews();
            }
        }

        if (mContainers[STYLE_TILE] != null) {
            ((QuickSettingsContainerView) mContainers[STYLE_TILE]).setColumnCount(columnCount);
        }

        if (mFlipSettingsView != null) {
            if (mStyle == STYLE_TILE) {
                mFlipSettingsView.setVisibility(View.VISIBLE);
            } else {
                mFlipSettingsView.setVisibility(View.GONE);
            }
        }

        if (mContainers[mStyle] != null) {
            switch (mStyle) {
                case STYLE_SWITCH:
                    break;
                case STYLE_TILE:
                    setupTiles();
                    break;
                case STYLE_TRADITIONAL:
                    setupTraditional();
                    break;
            }
        }
    }

    private boolean deviceSupportsTelephony() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    private boolean deviceSupportsBluetooth() {
        return (BluetoothAdapter.getDefaultAdapter() != null);
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QUICK_TOGGLES),
                    false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QUICK_TOGGLES_PER_ROW),
                    false, this);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    class SoundObserver extends ContentObserver {
        SoundObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global
                    .getUriFor(Settings.Global.MODE_RINGER),
                    false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            boolean vibt = false;
            boolean silt = false;
            boolean sst = false;
            if (userToggles.contains(VIBRATE_TOGGLE)) {
                vibt = true;
            }
            if (userToggles.contains(SILENT_TOGGLE)) {
                silt = true;
            }
            if (userToggles.contains(SOUND_STATE_TOGGLE)) {
                sst = true;
            }
            for (BaseToggle t : mToggles) {
                if (t instanceof VibrateToggle && vibt) {
                    t.scheduleViewUpdate();
                }
                if (t instanceof SilentToggle && silt) {
                    t.scheduleViewUpdate();
                }
                if (t instanceof SoundStateToggle && sst) {
                    t.scheduleViewUpdate();
                }
            }
        }
    }

    private void log(String s) {
        if (true) {
            Log.d(TAG, s);
        }
    }

    public static int getTextSize(Context c) {
        int columnCount = Settings.System.getInt(c.getContentResolver(),
                Settings.System.QUICK_TOGGLES_PER_ROW,
                c.getResources().getInteger(R.integer.quick_settings_num_columns));
        // adjust Tile Text Size based on column count
        switch (columnCount) {
            case 5:
                return 8;
            case 4:
                return 10;
            case 3:
            default:
                return 12;
        }
    }

    private static LinearLayout.LayoutParams getTraditionalToggleParams(Context c) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, c.getResources().getDimensionPixelSize(
                        R.dimen.toggle_row_height), 1f);
    }

    private static FrameLayout.LayoutParams getTileParams(Context c) {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, c.getResources().getDimensionPixelSize(
                        R.dimen.quick_settings_cell_height));
    }
}
