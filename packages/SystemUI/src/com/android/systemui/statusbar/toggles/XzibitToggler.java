
package com.android.systemui.statusbar.toggles;

import android.app.ActivityManagerNative;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.UserInfo;
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
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Profile;
import android.provider.Settings;
import android.util.Log;
import android.util.Pair;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.BrightnessController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.statusbar.policy.BrightnessController.BrightnessStateChangeCallback;
import com.android.systemui.statusbar.policy.LocationController.LocationGpsStateChangeCallback;
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

    Context mContext;
    QuickSettingsContainerView mContainerView;
    private String userToggles = null;
    ArrayList<BaseToggle> mToggles = new ArrayList<BaseToggle>();

    private HashMap<String, Class<? extends BaseToggle>> toggleMap;

    private HashMap<String, Class<? extends BaseToggle>> getToggleMap() {
        if (toggleMap == null) {
            toggleMap = new HashMap<String, Class<? extends BaseToggle>>();
            toggleMap.put(USER_TOGGLE, null);
            toggleMap.put(BRIGHTNESS_TOGGLE, null);
            toggleMap.put(SETTINGS_TOGGLE, SettingsToggle.class);
            toggleMap.put(WIFI_TOGGLE, WifiToggle.class);
            toggleMap.put(SIGNAL_TOGGLE, null);
            toggleMap.put(ROTATE_TOGGLE, null);
            toggleMap.put(CLOCK_TOGGLE, null);
            toggleMap.put(GPS_TOGGLE, null);
            toggleMap.put(IME_TOGGLE, null);
            toggleMap.put(BATTERY_TOGGLE, null);
            toggleMap.put(AIRPLANE_TOGGLE, null);
            toggleMap.put(BLUETOOTH_TOGGLE, null);
            toggleMap.put(SWAGGER_TOGGLE, null);
            toggleMap.put(VIBRATE_TOGGLE, null);
            toggleMap.put(SILENT_TOGGLE, null);
            toggleMap.put(FCHARGE_TOGGLE, null);
            toggleMap.put(SYNC_TOGGLE, null);
            toggleMap.put(NFC_TOGGLE, null);
            toggleMap.put(TORCH_TOGGLE, null);
            toggleMap.put(WIFI_TETHER_TOGGLE, null);
            toggleMap.put(USB_TETHER_TOGGLE, null);
            toggleMap.put(TWOG_TOGGLE, null);
            toggleMap.put(LTE_TOGGLE, null);
            toggleMap.put(FAV_CONTACT_TOGGLE, null);
            toggleMap.put(SOUND_STATE_TOGGLE, null);
            toggleMap.put(NAVBAR_HIDE_TOGGLE, null);
            // toggleMap.put(BT_TETHER_TOGGLE, null);
        }
        return toggleMap;
    }

    public XzibitToggler(QuickSettingsContainerView container) {
        mContainerView = container;
        mContext = container.getContext();

        new SettingsObserver(new Handler()).observe();
        new SoundObserver(new Handler()).observe();

        setupContainerView();
    }

    public void setControllers(BluetoothController bt, NetworkController net,
            BatteryController batt, LocationController loc, BrightnessController screen) {
        for (BaseToggle toggle : mToggles) {
            if (toggle instanceof NetworkSignalChangedCallback) {
                net.addNetworkSignalChangedCallback((NetworkSignalChangedCallback) toggle);
            }

            if (toggle instanceof BluetoothStateChangeCallback) {
                bt.addStateChangedCallback((BluetoothStateChangeCallback) toggle);
            }

            if (toggle instanceof BatteryStateChangeCallback) {
                batt.addStateChangedCallback((BatteryStateChangeCallback) toggle);
            }

            if (toggle instanceof LocationGpsStateChangeCallback) {
                loc.addStateChangedCallback((LocationGpsStateChangeCallback) loc);
            }

//            if (toggle instanceof BrightnessStateChangeCallback) {
//                screen.addStateChangedCallback((BrightnessStateChangeCallback) toggle);
//            }
        }
    }

    public void setupContainerView() {
        mContainerView.removeAllViews();
        HashMap<String, Class<? extends BaseToggle>> map = getToggleMap();
        ArrayList<String> toots = getToggles();
        for (String toggle : toots) {
            try {
                Class<? extends BaseToggle> theclass = map.get(toggle);
                BaseToggle bt = theclass.newInstance();
                bt.init(mContext, new Integer(BaseToggle.STYLE_TILE));
                mContainerView.addView(bt.createView());
                mToggles.add(bt);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // get these out of here
    private Dialog mBrightnessDialog;
    private AsyncTask<Void, Void, Pair<String, Drawable>> mUserInfoTask;
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
        mContainerView.setColumnCount(columnCount);
        updateTileSize(columnCount);
        setupContainerView();
    }

    private void updateTileSize(int colnum) {
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

    private void queryForUserInformation() {
        Context currentUserContext = null;
        UserInfo userInfo = null;
        try {
            userInfo = ActivityManagerNative.getDefault().getCurrentUser();
            currentUserContext = mContext.createPackageContextAsUser("android", 0,
                    new UserHandle(userInfo.id));
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Couldn't create user context", e);
            throw new RuntimeException(e);
        } catch (RemoteException e) {
            Log.e(TAG, "Couldn't get user info", e);
        }
        final int userId = userInfo.id;
        final String userName = userInfo.name;

        final Context context = currentUserContext;
        mUserInfoTask = new AsyncTask<Void, Void, Pair<String, Drawable>>() {
            @Override
            protected Pair<String, Drawable> doInBackground(Void... params) {
                final UserManager um =
                        (UserManager) mContext.getSystemService(Context.USER_SERVICE);

                // Fall back to the UserManager nickname if we can't read the
                // name from the local
                // profile below.
                String name = userName;
                Drawable avatar = null;
                Bitmap rawAvatar = um.getUserIcon(userId);
                if (rawAvatar != null) {
                    avatar = new BitmapDrawable(mContext.getResources(), rawAvatar);
                } else {
                    avatar = mContext.getResources().getDrawable(R.drawable.ic_qs_default_user);
                }

                // If it's a single-user device, get the profile name, since the
                // nickname is not
                // usually valid
                if (um.getUsers().size() <= 1) {
                    // Try and read the display name from the local profile
                    final Cursor cursor = context.getContentResolver().query(
                            Profile.CONTENT_URI, new String[] {
                                    Phone._ID, Phone.DISPLAY_NAME
                            },
                            null, null, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                name = cursor.getString(cursor.getColumnIndex(Phone.DISPLAY_NAME));
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
                return new Pair<String, Drawable>(name, avatar);
            }

            @Override
            protected void onPostExecute(Pair<String, Drawable> result) {
                super.onPostExecute(result);
                // mModel.setUserTileInfo(result.first, result.second);
                mUserInfoTask = null;
            }
        };
        mUserInfoTask.execute();
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

    void reloadUserInfo() {
        if (mUserInfoTask != null) {
            mUserInfoTask.cancel(false);
            mUserInfoTask = null;
        }
        queryForUserInformation();
    }

    void reloadFavContactInfo() {
        if (mFavContactInfoTask != null) {
            mFavContactInfoTask.cancel(false);
            mFavContactInfoTask = null;
        }
        queryForFavContactInformation();
    }

    private void showBrightnessDialog() {
        if (mBrightnessDialog == null) {
            mBrightnessDialog = new Dialog(mContext);
            mBrightnessDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mBrightnessDialog.setContentView(R.layout.quick_settings_brightness_dialog);
            mBrightnessDialog.setCanceledOnTouchOutside(true);

            // mBrightnessController = new BrightnessController(mContext,
            // (ImageView) mBrightnessDialog.findViewById(R.id.brightness_icon),
            // (ToggleSlider)
            // mBrightnessDialog.findViewById(R.id.brightness_slider));
            // mBrightnessController.addStateChangedCallback(mModel);
            mBrightnessDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    // mBrightnessController = null;
                }
            });

            mBrightnessDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            mBrightnessDialog.getWindow().getAttributes().privateFlags |=
                    WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS;
            mBrightnessDialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        if (!mBrightnessDialog.isShowing()) {
            try {
                WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
            } catch (RemoteException e) {
            }
            mBrightnessDialog.show();
            // dismissBrightnessDialog(mBrightnessDialogLongTimeout);
        }
    }

    private void dismissBrightnessDialog(int timeout) {
        // removeAllBrightnessDialogCallbacks();
        // if (mBrightnessDialog != null) {
        // mHandler.postDelayed(mDismissBrightnessDialogRunnable, timeout);
        // }
        if (mBrightnessDialog != null)
            mBrightnessDialog.dismiss();
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
            // mModel.refreshVibrateTile();
            // mModel.refreshSilentTile();
            // mModel.refreshSoundStateTile();
            // mModel.refreshNavBarHideTile();
            // mModel.refreshTorchTile();
        }

        @Override
        public void onChange(boolean selfChange) {
            // mModel.refreshVibrateTile();
            // mModel.refreshSilentTile();
            // mModel.refreshSoundStateTile();
            // mModel.refreshNavBarHideTile();
            // mModel.refreshTorchTile();
        }
    }

    public void cleanUp() {
        // TODO Auto-generated method stub

    }
}
