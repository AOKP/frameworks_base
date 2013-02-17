
package com.android.systemui.statusbar.toggles;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BrightnessController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;

import java.io.InputStream;
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

    private static final String TAG = XzibitToggler.class.getSimpleName();

    private int mStyle;

    public static final int STYLE_TILE = 0;
    public static final int STYLE_SWITCH = 1;
    public static final int STYLE_TRADITIONAL = 2;

    private ViewGroup[] mContainers = new ViewGroup[3];

    Context mContext;
    private String userToggles = null;
    ArrayList<BaseToggle> mToggles = new ArrayList<BaseToggle>();

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
            toggleMap.put(SWAGGER_TOGGLE, null);
            toggleMap.put(VIBRATE_TOGGLE, VibrateToggle.class);
            toggleMap.put(SILENT_TOGGLE, SilentToggle.class);
            toggleMap.put(FCHARGE_TOGGLE, null);
            toggleMap.put(SYNC_TOGGLE, null);
            toggleMap.put(NFC_TOGGLE, null);
            toggleMap.put(TORCH_TOGGLE, TorchToggle.class);
            toggleMap.put(WIFI_TETHER_TOGGLE, WifiApToggle.class);
            toggleMap.put(USB_TETHER_TOGGLE, null);
            toggleMap.put(TWOG_TOGGLE, TwoGToggle.class);
            toggleMap.put(LTE_TOGGLE, null);
            toggleMap.put(FAV_CONTACT_TOGGLE, null);
            toggleMap.put(SOUND_STATE_TOGGLE, SoundStateToggle.class);
            toggleMap.put(NAVBAR_HIDE_TOGGLE, null);
            // toggleMap.put(BT_TETHER_TOGGLE, null);
        }
        return toggleMap;
    }

    private static final LinearLayout.LayoutParams PARAMS_BRIGHTNESS = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 90);

    private static final LinearLayout.LayoutParams PARAMS_TOGGLE = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

    private static final LinearLayout.LayoutParams PARAMS_TOGGLE_SCROLL = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);

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

    public void setupTiles(QuickSettingsContainerView container) {
        mContainers[STYLE_TILE] = container;
        if (mContainers[STYLE_TILE] != null) {
            updateToggleList();

            mContainers[STYLE_TILE].removeAllViews();
            for (BaseToggle toggle : mToggles) {
                mContainers[STYLE_TILE].addView(toggle.createTileView());
            }

        }
    }

    public void setupTraditional(LinearLayout container) {
        int widgetsPerRow = 6;
        View toggleSpacer;

        mContainers[STYLE_TRADITIONAL] = container;
        if (container != null) {
            updateToggleList();

            mContainers[STYLE_TRADITIONAL].removeAllViews();
            ArrayList<LinearLayout> rows = new ArrayList<LinearLayout>();
            rows.add(new LinearLayout(mContext)); // add first row

            for (int i = 0; i < mToggles.size(); i++) {
                if (widgetsPerRow > 0 && i % widgetsPerRow == 0) {
                    // new row
                    rows.add(new LinearLayout(mContext));
                }
                rows.get(rows.size() - 1)
                        .addView(mToggles.get(i).createTraditionalView(),
                                PARAMS_TOGGLE);
            }

            for (LinearLayout row : rows)
                mContainers[STYLE_TRADITIONAL].addView(row);

            mContainers[STYLE_TRADITIONAL].setVisibility(View.VISIBLE);
        }
    }

    private void updateToggleList() {
        // BaseToggle.mToggleController = this;
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

                // if (toggle instanceof BrightnessStateChangeCallback) {
                // screen.addStateChangedCallback((BrightnessStateChangeCallback)
                // toggle);
                // }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private AsyncTask<Void, Void, Pair<String, Drawable>> mFavContactInfoTask;

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

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        userToggles = Settings.System.getString(resolver, Settings.System.QUICK_TOGGLES);
        int columnCount = Settings.System.getInt(resolver, Settings.System.QUICK_TOGGLES_PER_ROW,
                mContext.getResources().getInteger(R.integer.quick_settings_num_columns));
        mStyle = XzibitToggler.STYLE_TRADITIONAL;

        if (mContainers[mStyle] != null
                && mContainers[mStyle] instanceof QuickSettingsContainerView) {
            ((QuickSettingsContainerView) mContainers[mStyle]).setColumnCount(columnCount);
        }

        if (mContainers[mStyle] != null) {
            switch (mStyle) {
                case STYLE_SWITCH:
                    break;
                case STYLE_TILE:
                    setupTiles((QuickSettingsContainerView) mContainers[mStyle]);
                    break;
                case STYLE_TRADITIONAL:
                    setupTraditional((LinearLayout) mContainers[mStyle]);
                    break;
            }
        }

        updateToggleTextSize(columnCount);
    }

    private void updateToggleTextSize(int colnum) {
        int size;
        // adjust Tile Text Size based on column count
        switch (colnum) {
            case 5:
                size = 8;
            case 4:
                size = 10;
            case 3:
            default:
                size = 12;
        }
        for (BaseToggle toggle : mToggles) {
            toggle.setTextSize(size);
            toggle.scheduleViewUpdate();
        }
    }

    private void queryForFavContactInformation() {
        mFavContactInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
            @Override
            protected Pair<String, Drawable> doInBackground(Void... params) {
                String name = "";
                Drawable avatar = mContext.getResources()
                        .getDrawable(R.drawable.ic_qs_default_user);
                Bitmap rawAvatar = null;
                String lookupKey = Settings.System.getString(mContext.getContentResolver(),
                        Settings.System.QUICK_TOGGLE_FAV_CONTACT);
                if (lookupKey != null && lookupKey.length() > 0) {
                    Uri lookupUri = Uri.withAppendedPath(
                            ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
                    Uri res = ContactsContract.Contacts.lookupContact(
                            mContext.getContentResolver(), lookupUri);
                    String[] projection = new String[] {
                            ContactsContract.Contacts.DISPLAY_NAME,
                            ContactsContract.Contacts.PHOTO_URI,
                            ContactsContract.Contacts.LOOKUP_KEY
                    };

                    final Cursor cursor = mContext.getContentResolver().query(res, projection,
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(cursor
                                        .getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                    InputStream input = ContactsContract.Contacts.
                            openContactPhotoInputStream(mContext.getContentResolver(), res, true);
                    if (input != null) {
                        rawAvatar = BitmapFactory.decodeStream(input);
                    }

                    if (rawAvatar != null) {
                        avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                    }
                }
                return new Pair<String, Drawable>(name, avatar);
            }

            @Override
            protected void onPostExecute(Pair<String, Drawable> result) {
                super.onPostExecute(result);
                // mModel.setFavContactTileInfo(result.first, result.second);
                mFavContactInfoTask = null;
            }
        };
        mFavContactInfoTask.execute();
    }

    void reloadFavContactInfo() {
        if (mFavContactInfoTask != null) {
            mFavContactInfoTask.cancel(false);
            mFavContactInfoTask = null;
        }
        queryForFavContactInformation();
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
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.QUICK_TOGGLE_FAV_CONTACT),
                    false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.NAV_HIDE_ENABLE),
                    false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.TORCH_STATE),
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
                // if(t instanceof SilentToggle && silt) {
                // t.scheduleViewUpdate();
                // }
                // if(t instanceof SoundStateToggle && sst) {
                // t.scheduleViewUpdate();
                // }
            }
        }
    }

    public void cleanUp() {

    }
}
