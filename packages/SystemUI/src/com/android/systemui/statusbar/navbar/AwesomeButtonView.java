package com.android.systemui.statusbar.navbar;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import com.android.internal.util.aokp.AwesomeConstants;
import com.android.systemui.aokp.AwesomeAction;
import com.android.systemui.aokp.NavBarHelpers;
import com.android.systemui.statusbar.navbar.AOKPNavBar.AwesomeButtonInfo;

import java.io.File;

public class AwesomeButtonView extends AwesomeButtonBase {

    String mSingleTapAction;
    String mDoubleTapAction;
    String mLongPressAction;

    GestureDetector mGestureDetector;

    public AwesomeButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mSupportsLongpress = false;
        mGestureDetector = new GestureDetector(context, new AwesomeGestureDetector());
        mGestureDetector.setOnDoubleTapListener(new AwesomeGestureDetector());
    }

    public AwesomeButtonView(Context context, AwesomeButtonInfo info) {
        this(context, (AttributeSet) null);

        mSingleTapAction = info.singleAction;
        mDoubleTapAction = info.doubleTapAction;
        mLongPressAction = info.longPressAction;

        if (mSingleTapAction != null) {
            setTag(AwesomeConstants.fromString(mSingleTapAction).value());
        }

        String iconUri = info.iconUri;
        if (iconUri != null && iconUri.length() > 0) {
            // custom icon from the URI here
            File f = new File(Uri.parse(iconUri).getPath());
            if (f.exists()) {
                setImageDrawable(new BitmapDrawable(getResources(), f.getAbsolutePath()));
            }
        } else if (mSingleTapAction != null) {
            setImageDrawable(NavBarHelpers.getIconImage(mContext, mSingleTapAction));
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        int x, y;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mDownTime = SystemClock.uptimeMillis();
                setPressed(true);
                if (mCode != 0) {
                    sendEvent(KeyEvent.ACTION_DOWN, 0, mDownTime);
                } else {
                    // Provide the same haptic feedback that the system offers for virtual keys.
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                x = (int) ev.getX();
                y = (int) ev.getY();
                setPressed(x >= -mTouchSlop
                        && x < getWidth() + mTouchSlop
                        && y >= -mTouchSlop
                        && y < getHeight() + mTouchSlop);
                break;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if (mCode != 0) {
                    sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
                }
                break;
            case MotionEvent.ACTION_UP:
                final boolean doIt = isPressed();
                setPressed(false);
                if (mCode != 0) {
                    if (doIt) {
                        sendEvent(KeyEvent.ACTION_UP, 0);
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                        playSoundEffect(SoundEffectConstants.CLICK);
                    } else {
                        sendEvent(KeyEvent.ACTION_UP, KeyEvent.FLAG_CANCELED);
                    }
                }
                break;
        }
        mGestureDetector.onTouchEvent(ev);

        return super.onTouchEvent(ev);
    }


    class AwesomeGestureDetector extends SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            // handle the single tap a little differently
            // if it's a key event, so the system can repeat them
            boolean consumed = false;
            if (mCode == 0 && mSingleTapAction != null) {
                consumed = AwesomeAction.launchAction(getContext(), mSingleTapAction);
                // we don't vibrate again
            }
            setPressed(false);
            // otherwise this was already handled in MotionEvent.ACTION_DOWN
            return consumed;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            boolean consumed = false;
            if (mDoubleTapAction != null) {
                consumed = AwesomeAction.launchAction(getContext(), mDoubleTapAction);
            }
            setPressed(false);
            return consumed;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (mLongPressAction != null) {
                AwesomeAction.launchAction(getContext(), mLongPressAction);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                playSoundEffect(SoundEffectConstants.CLICK);
            }
            setPressed(false);
        }
    }

    public String getSingleTapAction() {
        return mSingleTapAction;
    }

    public void setSingleTapAction(String singleTapAction) {
        this.mSingleTapAction = singleTapAction;
    }

    public String getDoubleTapAction() {
        return mDoubleTapAction;
    }

    public void setDoubleTapAction(String doubleTapAction) {
        this.mDoubleTapAction = doubleTapAction;
    }

    public String getLongPressAction() {
        return mLongPressAction;
    }

    public void setLongPressAction(String longPressAction) {
        this.mLongPressAction = longPressAction;
        mGestureDetector.setIsLongpressEnabled(mDoubleTapAction != null);
    }

    public static LinearLayout.LayoutParams getLayoutParams(boolean landscape, float px, float weight) {
        return landscape ?
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) px, weight) :
                new LinearLayout.LayoutParams((int) px, LinearLayout.LayoutParams.MATCH_PARENT, weight);
    }

    public static LinearLayout.LayoutParams getLayoutParams(boolean landscape, float px) {
        return landscape ?
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) px) :
                new LinearLayout.LayoutParams((int) px, LinearLayout.LayoutParams.MATCH_PARENT);
    }
}
