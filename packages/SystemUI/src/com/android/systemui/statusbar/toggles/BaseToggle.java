
package com.android.systemui.statusbar.toggles;

import android.app.ActivityManagerNative;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public abstract class BaseToggle
        implements OnClickListener, OnLongClickListener {

    public static final int STYLE_TILE = 0;
    public static final int STYLE_SWITCH = 1;
    public static final int STYLE_TRADITIONAL = 2;

    public static final String TAG = "Toggle";

    protected Context mContext;

    protected int mStyle;

    private CharSequence mLabelText = null;
    private int mIconRes = 0;
    private int mTextSize = 12;

    protected CompoundButton mToggleButton = null;
    protected TextView mLabel = null;
    protected ImageView mIcon = null;

    static protected XzibitToggler mToggleController;

    protected Handler mHandler = new Handler();
    private Runnable mUpdateViewRunnable = new Runnable() {
        @Override
        public void run() {
            updateView();
        }
    };

    public BaseToggle() {
        // init(c, style, stateless);
    }

    protected void init(Context c, int style) {
        mContext = c;
        this.mStyle = style;
    }

    protected final void setInfo(final String label, final int resId) {
        setLabel(label);
        setIcon(resId);
    }

    protected final void setLabel(final String label) {
        mLabelText = label;
        scheduleViewUpdate();
    }

    protected final void setLabel(final int labelRes) {
        mLabelText = mContext.getText(labelRes);
        scheduleViewUpdate();
    }

    protected final void setIcon(int resId) {
        mIconRes = resId;
        scheduleViewUpdate();
    }

    protected final void setIcon(Drawable d) {
        if (mIcon != null) {
            mIcon.setImageDrawable(d);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        collapseStatusBar();
        return true;
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
                View.inflate(mContext, R.layout.quick_settings_tile, null);
        quick.setOnClickListener(this);
        quick.setOnLongClickListener(this);
        mLabel = (TextView) quick.findViewById(R.id.label);
        return quick;
    }

    public View createTraditionalView() {
        View view = View.inflate(mContext, R.layout.toggle_traditional, null);
        mLabel = (TextView) view.findViewById(R.id.label);
        mIcon = (ImageView) view.findViewById(R.id.icon);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        // mToggleButton = (ToggleButton) view.findViewById(R.id.toggle);
        // mToggleButton.setOnClickListener(this);
        // mToggleButton.setOnLongClickListener(this);
        return view;

    }

    public View createView() {
        switch (mStyle) {
            case STYLE_SWITCH:
                return null;
            case STYLE_TRADITIONAL:
                return createTraditionalView();
            case STYLE_TILE:
                return createTileView();
        }
        return null;
    }

    protected final void scheduleViewUpdate() {
        mHandler.removeCallbacks(mUpdateViewRunnable);
        mHandler.postDelayed(mUpdateViewRunnable, 100);
    }

    protected final void startActivity(Intent i) {
        dismissKeyguard();
        mContext.startActivityAsUser(i, new UserHandle(UserHandle.USER_CURRENT));
    }

    protected final void registerBroadcastReceiver(BroadcastReceiver r, IntentFilter f) {
        // TODO cleanup when destroying
        mContext.registerReceiver(r, f);
    }

    protected void updateView() {
        if (mStyle == STYLE_SWITCH) {

        } else if (mStyle == STYLE_TILE) {
            if (mLabel != null) {
                mLabel.setText(mLabelText);
                mLabel.setCompoundDrawablesWithIntrinsicBounds(0, mIconRes, 0, 0);
                mLabel.setTextSize(1, mTextSize);
            }
        } else if (mStyle == STYLE_TRADITIONAL) {

            if (mLabel != null) {
                mLabel.setText(mLabelText);
                mLabel.setTextSize(1, mTextSize);
                mLabel.setVisibility(View.GONE);
            }
            if (mIcon != null) {
                mIcon.setImageResource(mIconRes);
            }
            if (mToggleButton != null) {
            }

        }
    }

    public void setTextSize(int size) {
        mTextSize = size;
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
}
