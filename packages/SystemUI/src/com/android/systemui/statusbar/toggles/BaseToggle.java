
package com.android.systemui.statusbar.toggles;

import android.animation.ValueAnimator;
import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;
import com.android.systemui.settings.BrightnessController;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;

import java.util.ArrayList;

public abstract class BaseToggle
        implements OnClickListener, OnLongClickListener {

    public static final String TAG = "Toggle";

    protected Context mContext;

    protected int mStyle;

    private boolean mCollapsePref;
    private boolean mVibratePref;
    private boolean mTactileFeedbackEnabled;
    private Vibrator vib;
    private Drawable mIconDrawable = null;
    private int mIconLevel = -1;
    private CharSequence mLabelText = null;
    private int mTextSize = 12;

    protected CompoundButton mToggleButton = null;
    protected TextView mLabel = null;
    protected ImageView mIcon = null;
    protected ImageView mActivityIn = null;
    protected ImageView mActivityOut = null;
    private int mIconId = -1;

    private SettingsObserver mObserver = null;

    protected ArrayList<BroadcastReceiver> mRegisteredReceivers = new ArrayList<BroadcastReceiver>();

    protected Handler mHandler;
    private Runnable mUpdateViewRunnable = new Runnable() {
        @Override
        public void run() {
            updateView();
        }
    };

    public BaseToggle() {
    }

    public void init(Context c, int style) {
        mContext = c;
        mStyle = style;
        mHandler = new Handler();
        mObserver = new SettingsObserver(mHandler);
        mObserver.observe();
        vib = (Vibrator) mContext.getSystemService(mContext.VIBRATOR_SERVICE);
        setTextSize(ToggleManager.getTextSize(mContext));
        scheduleViewUpdate();
    }

    public abstract int getDefaultIconResId();

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

    protected final LocationController getLocationController() {
        return locationController;
    }

    protected final BrightnessController getBrightnessController() {
        return brightnessController;
    }

    protected final BatteryController getBatteryController() {
        return batteryController;
    }

    protected final NetworkController getNetworkController() {
        return networkController;
    }

    protected final BluetoothController getBluetoothController() {
        return bluetoothController;
    }

    protected final void setTextSize(int s) {
        mTextSize = s;
    }

    protected final void setInfo(final String label, final int resId) {
        setLabel(label);
        setIcon(resId);
    }

    protected final void setLabel(final String label) {
        mLabelText = label;
    }

    protected final void setLabel(final int labelRes) {
        mLabelText = mContext.getText(labelRes);
    }

    protected final void setIcon(int resId) {
        if (resId == mIconId) {
            // a little cache action
            return;
        }
        mIconDrawable = null;
        mIconDrawable = mContext.getResources().getDrawable(resId);
        mIconId = resId;
    }

    protected final void setIcon(Drawable d) {
        mIconDrawable = d;
        mIconId = -1;
    }

    protected final void setIconLevel(int level) {
        mIconLevel = level;
    }

    protected void cleanup() {
        setControllers(null, null, null, null, null);
        mHandler.removeCallbacks(mUpdateViewRunnable);
        for (BroadcastReceiver br : mRegisteredReceivers) {
            mContext.unregisterReceiver(br);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        return true;
    }

    /* Called by StateFullToggle
     * Grant elsewhere if user should have a choice
     */
    protected final void collapseShadePref() {
        if (mCollapsePref) {
            collapseStatusBar();
        }
    }

    protected final void vibrateOnTouch() {
        if (mTactileFeedbackEnabled &&  mVibratePref && vib != null ) {
            vib.vibrate(25);
        }
    }

    protected final void collapseStatusBar() {
        try {
            IStatusBarService sb = IStatusBarService.Stub.asInterface(ServiceManager
                    .getService(Context.STATUS_BAR_SERVICE));
            sb.collapsePanels();
        } catch (RemoteException e) {
        }
    }

    protected final void dismissKeyguard() {
        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
    }

    public QuickSettingsTileView createTileView() {
        QuickSettingsTileView quick = (QuickSettingsTileView)
                View.inflate(mContext, R.layout.toggle_tile, null);
        quick.setVisibility(View.VISIBLE);
        quick.setOnClickListener(this);
        quick.setOnLongClickListener(this);
        mLabel = (TextView) quick.findViewById(R.id.label);
        mIcon = (ImageView) quick.findViewById(R.id.icon);
        return quick;
    }

    public View createTraditionalView() {
        View view = View.inflate(mContext, R.layout.toggle_traditional, null);
        mLabel = (TextView) view.findViewById(R.id.label);
        mIcon = (ImageView) view.findViewById(R.id.icon);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        return view;
    }

    public View createScrollableView() {
        View view = View.inflate(mContext, R.layout.toggle_traditional, null);
        mLabel = (TextView) view.findViewById(R.id.label);
        mIcon = (ImageView) view.findViewById(R.id.icon);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        view.setPadding(0,0,
                mContext.getResources().getDimensionPixelSize(R.dimen.toggle_traditional_padding),
                mContext.getResources().getDimensionPixelSize(R.dimen.quick_settings_cell_gap));
        return view;

    }

    public void networkActivity(boolean acIn, boolean acOut) {
        long defaultDuration = new ValueAnimator().getDuration();
        networkActivitySetVisibility(mActivityIn, acIn, defaultDuration);
        networkActivitySetVisibility(mActivityOut, acOut, defaultDuration);
    }

    private void networkActivitySetVisibility(View view, boolean visible, long defaultDuration) {
        long shortDuration = defaultDuration / 3;

        final float newAlpha = visible ? 1 : 0;
        if (view.getAlpha() != newAlpha) {
            view.animate()
                .setDuration(visible ? shortDuration : defaultDuration)
                .alpha(newAlpha)
                .start();
        }
    }

    protected final void scheduleViewUpdate() {
        // mHandler.removeCallbacks(mUpdateViewRunnable);
        if (!mHandler.hasCallbacks(mUpdateViewRunnable))
            mHandler.postDelayed(mUpdateViewRunnable, 100);
    }

    protected final void startActivity(String a) {
        startActivity(new Intent(a));
    }

    protected final void startActivity(Intent i) {
        collapseStatusBar();
        dismissKeyguard();
        mContext.startActivityAsUser(i, new UserHandle(UserHandle.USER_CURRENT));
    }

    protected final void registerBroadcastReceiver(BroadcastReceiver r, IntentFilter f) {
        if (r == null) {
            return;
        }
        mRegisteredReceivers.add(r);
        mContext.registerReceiver(r, f);
    }

    protected void updateView() {
        if (mStyle == ToggleManager.STYLE_SWITCH) {

        } else if (mStyle == ToggleManager.STYLE_TILE) {

            if (mLabel != null) {
                mLabel.setText(mLabelText);
                mLabel.setVisibility(View.VISIBLE);
                // if (mIconDrawable != null) {
                // mLabel.setCompoundDrawablesWithIntrinsicBounds(null,
                // mIconDrawable, null, null);
                // }
                mLabel.setTextSize(1, mTextSize);
            }
            if (mIcon != null) {
                if (mIconDrawable != null) {
                    mIcon.setImageDrawable(mIconDrawable);
                    if (mIconLevel != -1) {
                        mIcon.setImageLevel(mIconLevel);
                    }
                }
            }

        } else if (mStyle == ToggleManager.STYLE_TRADITIONAL) {
            if (mIcon != null) {
                if (mIconDrawable != null) {
                    mIcon.setImageDrawable(mIconDrawable);
                    if (mIconLevel != -1) {
                        mIcon.setImageLevel(mIconLevel);
                    }
                }
            }
        } else if (mStyle == ToggleManager.STYLE_SCROLLABLE) {
            if (mIcon != null) {
                if (mIconDrawable != null) {
                    mIcon.setImageDrawable(mIconDrawable);
                    if (mIconLevel != -1) {
                        mIcon.setImageLevel(mIconLevel);
                    }
                }
            }
        }
    }

    // Remove the double quotes that the SSID may contain
    public static String removeDoubleQuotes(String string) {
        if (string == null)
            return null;
        final int length = string.length();
        if ((length > 1) && (string.charAt(0) == '"') && (string.charAt(length - 1) == '"')) {
            return string.substring(1, length - 1);
        }
        return string;
    }

    // Remove the period from the network name
    public static String removeTrailingPeriod(String string) {
        if (string == null)
            return null;
        final int length = string.length();
        if (string.endsWith(".")) {
            string.substring(0, length - 1);
        }
        return string;
    }

    protected static void log(String msg) {
        ToggleManager.log(msg);
    }

    protected static void log(String msg, Exception e) {
        ToggleManager.log(msg, e);
    }

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mCollapsePref = Settings.AOKP.getBoolean(resolver,
                Settings.AOKP.SHADE_COLLAPSE_ALL, false);
        mVibratePref = Settings.AOKP.getBoolean(resolver,
                Settings.AOKP.QUICK_TOGGLE_VIBRATE, false);
        mTactileFeedbackEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 1, UserHandle.USER_CURRENT) != 0;
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();

            resolver.registerContentObserver(Settings.AOKP
                    .getUriFor(Settings.AOKP.SHADE_COLLAPSE_ALL),
                    false, this);
            resolver.registerContentObserver(Settings.AOKP
                    .getUriFor(Settings.AOKP.QUICK_TOGGLE_VIBRATE),
                    false, this);
            resolver.registerContentObserver(Settings.System
                    .getUriFor(Settings.System.HAPTIC_FEEDBACK_ENABLED),
                    false, this);

            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
}
