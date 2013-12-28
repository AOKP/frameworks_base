package com.android.systemui.statusbar.navbar;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.android.internal.util.aokp.AwesomeConstants.AwesomeConstant;
import com.android.systemui.R;

import java.util.ArrayList;

/**
 * Class which extends the original Android NavigationBar, attempting to keep all of it's original functionality intact.
 * <p/>
 * All custom button logic will be handled here.
 */
public class AOKPNavBar extends NavigationBarBase {

    public static final String NAVIGATION_BAR_BUTTONS = "navigation_bar_buttons";
    private ArrayList<AwesomeButtonInfo> mNavButtons = new ArrayList<AwesomeButtonInfo>();

    static class AwesomeButtonInfo {
        String singleAction, doubleTapAction, longPressAction, iconUri;

        public AwesomeButtonInfo(String singleTap, String doubleTap, String longPress, String uri) {
            this.singleAction = singleTap;
            this.doubleTapAction = doubleTap;
            this.longPressAction = longPress;
            this.iconUri = uri;
        }
    }

    View mCameraButton, mSearchLight;
    View mLeftSeparator, mRightSeparator;
    private float mButtonWidth, mMenuButtonWidth;
    FrameLayout rot0, rot90;

    public AOKPNavBar(Context context, AttributeSet attrs) {
        super(context, attrs);

        Resources res = context.getResources();
        mButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_key_width);
        mMenuButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_menu_key_width);

        refreshNavButtons();
    }

    private void refreshNavButtons() {
        mNavButtons.clear();
        String buttons = Settings.AOKP.getString(getContext().getContentResolver(), /*Settings.AOKP.NAVIGATION_BAR_BUTTONS*/  NAVIGATION_BAR_BUTTONS);
        if (buttons == null || buttons.isEmpty()) {
            // back
            mNavButtons.add(new AwesomeButtonInfo(AwesomeConstant.ACTION_BACK.value(), null, null, null));
            // home
            mNavButtons.add(new AwesomeButtonInfo(AwesomeConstant.ACTION_HOME.value(), null, null, null));
            // recents
            mNavButtons.add(new AwesomeButtonInfo(AwesomeConstant.ACTION_RECENTS.value(), null, null, null));
        } else {
            /**
             * Format:
             *
             * singleTapAction,doubleTapAction,longPressAction,iconUri|singleTap...
             */
            String[] userButtons = buttons.split("\\|");
            if (userButtons != null) {
                for (String button : userButtons) {
                    String[] actions = button.split(",", 4);
                    mNavButtons.add(new AwesomeButtonInfo(actions[0], actions[1], actions[2], actions[3]));
                }
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        ContentResolver res = mContext.getContentResolver();

        res.registerContentObserver(Settings.AOKP.getUriFor(NAVIGATION_BAR_BUTTONS), false, mSettingsObserver);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        ContentResolver res = mContext.getContentResolver();
        res.unregisterContentObserver(mSettingsObserver);
    }

    @Override
    public View getRecentsButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_RECENTS.value());
    }

    @Override
    public View getMenuButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_MENU.value());
    }

    @Override
    public View getBackButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_BACK.value());
    }

    @Override
    public View getHomeButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_HOME.value());
    }

    @Override
    public View getSearchLight() {
        return mSearchLight;
    }

    @Override
    public View getCameraButton() {
        return mCameraButton;
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        rot0 = (FrameLayout) findViewById(R.id.rot0);
        rot90 = (FrameLayout) findViewById(R.id.rot90);
        mSearchLight = findViewById(R.id.search_light);
        mCameraButton = findViewById(R.id.camera_button);

        createViews();
    }

    @Override
    protected void createViews() {

        for (int i = 0; i <= 1; i++) {
            boolean landscape = (i == 1);

            LinearLayout navButtons = (LinearLayout) (landscape ? rot90
                    .findViewById(R.id.nav_buttons) : rot0
                    .findViewById(R.id.nav_buttons));
            LinearLayout lightsOut = (LinearLayout) (landscape ? rot90
                    .findViewById(R.id.lights_out) : rot0
                    .findViewById(R.id.lights_out));

            navButtons.removeAllViews();
            lightsOut.removeAllViews();

            mLeftSeparator = new View(mContext);
            mLeftSeparator.setLayoutParams(AwesomeButtonView.getLayoutParams(landscape, mMenuButtonWidth, 1f));

            navButtons.addView(mLeftSeparator);
            addLightsOutButton(lightsOut, mLeftSeparator, landscape, true);

            for (int j = 0; j < mNavButtons.size(); j++) {
                AwesomeButtonInfo info = mNavButtons.get(j);
                AwesomeButtonView button = new AwesomeButtonView(getContext(), info);
                button.setLayoutParams(AwesomeButtonView.getLayoutParams(landscape, mButtonWidth));
                button.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                        : R.drawable.ic_sysbar_highlight);
                navButtons.addView(button);

                Object tag = button.getTag();
                addLightsOutButton(lightsOut, button, landscape, false);
            }

            mRightSeparator = new View(mContext);
            mRightSeparator.setLayoutParams(AwesomeButtonView.getLayoutParams(landscape, mMenuButtonWidth, 1f));
            navButtons.addView(mRightSeparator);
            addLightsOutButton(lightsOut, mRightSeparator, landscape, true);
        }
    }

    private void addLightsOutButton(LinearLayout root, View v, boolean landscape, boolean empty) {

        ImageView addMe = new ImageView(mContext);
        addMe.setLayoutParams(v.getLayoutParams());
        addMe.setImageResource(empty ? R.drawable.ic_sysbar_lights_out_dot_large
                : R.drawable.ic_sysbar_lights_out_dot_small);
        addMe.setScaleType(ImageView.ScaleType.CENTER);
        addMe.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);

        if (landscape)
            root.addView(addMe, 0);
        else
            root.addView(addMe);
    }

    @Override
    protected AwesomeButtonBase[] getNavigationBarButtonViews() {
        return new AwesomeButtonBase[0];
    }

    private ContentObserver mSettingsObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);

            if (uri.equals(Settings.AOKP.getUriFor(NAVIGATION_BAR_BUTTONS))) {
                refreshNavButtons();
            }

            recreateNavigationBar();
        }
    };

    private void recreateNavigationBar() {
        createViews();
        reorient();
    }
}
