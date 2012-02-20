
package com.android.systemui.statusbar.policy;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.widget.TextView;
import com.android.internal.telephony.PhoneStateIntentReceiver;

public class SignalText extends TextView {

    private Context mContext;
    private int dBm = 0;
    private int ASU = 0;

    private boolean mAttached;

    public static final int STYLE_HIDE = 0;
    public static final int STYLE_SHOW = 1;
    public static final int STYLE_SHOW_DBM = 2;

    private static final int EVENT_SIGNAL_STRENGTH_CHANGED = 200;

    private int mTextStyle;
    private int mSignalColor;

    private PhoneStateIntentReceiver mPhoneStateReceiver;
    private SettingsObserver mSettingsObserver;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == EVENT_SIGNAL_STRENGTH_CHANGED) {
                updateSignalStrength();
            }
        }
    };

    public SignalText(Context context) {
        this(context, null);
    }

    public SignalText(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SignalText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mAttached) {
            mAttached = true;
            mSettingsObserver = new SettingsObserver(mHandler);
            mSettingsObserver.observe();
            mPhoneStateReceiver = new PhoneStateIntentReceiver(mContext, mHandler);
            mPhoneStateReceiver.notifySignalStrength(EVENT_SIGNAL_STRENGTH_CHANGED);
            mPhoneStateReceiver.registerIntent();
            updateSettings();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mAttached) {
            mAttached = false;
            mContext.getContentResolver().unregisterContentObserver(mSettingsObserver);
            mSettingsObserver = null;
            mPhoneStateReceiver.unregisterIntent();
            mPhoneStateReceiver = null;
            mHandler = null;
        }
        super.onDetachedFromWindow();
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.AOKP.getUriFor(Settings.AOKP.STATUSBAR_SIGNAL_TEXT), false,
                    this);
            resolver.registerContentObserver(
                    Settings.AOKP.getUriFor(Settings.AOKP.STATUSBAR_SIGNAL_TEXT_COLOR), false,
                    this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            updateSettings();
        }
    }

    private void updateSettings() {
        ContentResolver resolver = getContext().getContentResolver();
        mSignalColor = Settings.AOKP.getInt(resolver,
                Settings.AOKP.STATUSBAR_SIGNAL_TEXT_COLOR,
                Color.WHITE);
        if (mSignalColor == Integer.MIN_VALUE) {
            // flag to reset the color
            mSignalColor = Color.WHITE;
        }
        setTextColor(mSignalColor);
        updateSignalText();
    }

    public void updateSignalStrength() {
        dBm = mPhoneStateReceiver.getSignalStrengthDbm();

        if (-1 == dBm) dBm = 0;

        ASU = mPhoneStateReceiver.getSignalStrengthLevelAsu();

        if (-1 == ASU) ASU = 0;
        updateSignalText();
    }

    private void updateSignalText() {
        mTextStyle = Settings.AOKP.getInt(getContext().getContentResolver(),
                Settings.AOKP.STATUSBAR_SIGNAL_TEXT, STYLE_HIDE);

        if (mTextStyle == STYLE_SHOW) {
            String result = Integer.toString(dBm);

            setText(result + " ");
        } else if (mTextStyle == STYLE_SHOW_DBM) {
            String result = Integer.toString(dBm) + " dBm ";

            SpannableStringBuilder formatted = new SpannableStringBuilder(result);
            int start = result.indexOf("d");

            CharacterStyle style = new RelativeSizeSpan(0.7f);
            formatted.setSpan(style, start, start + 3, Spannable.SPAN_EXCLUSIVE_INCLUSIVE);

            setText(formatted);
        } else {
            setText(null);
        }
    }
}
