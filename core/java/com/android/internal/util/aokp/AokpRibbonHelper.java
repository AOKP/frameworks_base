package com.android.internal.util.aokp;

import java.util.ArrayList;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

public class AokpRibbonHelper {

    private static final String TAG = "Aokp Ribbon";

    private static final String TARGET_DELIMITER = "|";
    public static final int LOCKSCREEN = 0;
    public static final int NOTIFICATIONS = 1;

    private static final LinearLayout.LayoutParams PARAMS_TOGGLE = new LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 1f);

    private static final LinearLayout.LayoutParams PARAMS_TARGET_SCROLL = new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f);

    public static HorizontalScrollView getRibbon(Context mContext, String shortTargets, String longTargets, boolean text, int size) {
        HorizontalScrollView targetScrollView = new HorizontalScrollView(mContext);
        if (!TextUtils.isEmpty(shortTargets) && !TextUtils.isEmpty(longTargets)) {
            final String[] shortSplit = shortTargets.split("\\" + TARGET_DELIMITER);
            final String[] longSplit = longTargets.split("\\" + TARGET_DELIMITER);
            ArrayList<RibbonTarget> targets = new ArrayList<RibbonTarget>();

            for (int i = 0; i < shortSplit.length; i++) {
                RibbonTarget newTarget = null;
                newTarget = new RibbonTarget(mContext, shortSplit[i], longSplit[i], text, size);
                if (newTarget != null) {
                    targets.add(newTarget);
                }
            }
            LinearLayout targetsLayout = new LinearLayout(mContext);
            targetsLayout.setGravity(Gravity.CENTER_HORIZONTAL);
            targetScrollView.setHorizontalFadingEdgeEnabled(true);
            for (int i = 0; i < targets.size(); i++) {
                targetsLayout.addView(targets.get(i).getView(), PARAMS_TARGET_SCROLL);
            }
            targetScrollView.addView(targetsLayout, PARAMS_TOGGLE);
        }
        return targetScrollView;
    }
}