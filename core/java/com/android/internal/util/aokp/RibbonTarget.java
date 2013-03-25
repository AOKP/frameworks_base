/*
 * Copyright (C) 2013 The Android Open Kang Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.util.aokp;

import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.IWindowManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.R;

public class RibbonTarget {

    private static final String TAG = "Ribbon Target";

    private View mView;
    private Context mContext;
    private IWindowManager mWm;
    private ImageButton mIcon;
    private TextView mText;
    private Vibrator vib;


    /*
     * sClick = short click send the uri for the short click action also this will be the icon used
     * lClick = long click send the uri for the long click action
     * text = a boolean for weither to show the app text label
     * size = size used to resize icons 0 is default and will not resize the icons at all.
     */

    public RibbonTarget(Context context, final String sClick, final String lClick, final boolean text, final int size) {
        mContext = context;
        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window"));
        vib = (Vibrator) mContext.getSystemService(mContext.VIBRATOR_SERVICE);
        mView = View.inflate(mContext, R.layout.target_button, null);
        mText = (TextView) mView.findViewById(R.id.label);
        if (!text) {
            mText.setVisibility(View.GONE);
        }
        mText.setText(NavBarHelpers.getMultiLineSummary(mContext, sClick));
        mText.setOnClickListener(new OnClickListener() {
            @Override
            public final void onClick(View v) {
                if(vib != null) {
                    vib.vibrate(10);
                }
                collapseStatusBar();
                maybeSkipKeyguard();
                sendIt(sClick);
            }
        });

        mText.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                collapseStatusBar();
                maybeSkipKeyguard();
                sendIt(lClick);
                return true;
            }
        });

        mIcon = (ImageButton) mView.findViewById(R.id.icon);
        if (size > 0) {
            mIcon.setImageDrawable(resize(NavBarHelpers.getIconImage(mContext, sClick), size));
        } else {
            mIcon.setImageDrawable(NavBarHelpers.getIconImage(mContext, sClick));
        }
        mIcon.setOnClickListener(new OnClickListener() {
            @Override
            public final void onClick(View v) {
                if(vib != null) {
                    vib.vibrate(10);
                }
                collapseStatusBar();
                maybeSkipKeyguard();
                sendIt(sClick);
            }
        });

        mIcon.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                collapseStatusBar();
                maybeSkipKeyguard();
                sendIt(lClick);
                return true;
            }
        });
    }

    private Drawable resize(Drawable image, int size) {
        int px = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, size,
                mContext.getResources().getDisplayMetrics());

        Bitmap d = ((BitmapDrawable) image).getBitmap();
        if (d == null) {
            return AwesomeConstants.getSystemUIDrawable(mContext, "**null**");
        } else {
            Bitmap bitmapOrig = Bitmap.createScaledBitmap(d, px, px, false);
            return new BitmapDrawable(mContext.getResources(), bitmapOrig);
        }
    }

    private void sendIt(String action) {
        Intent i = new Intent();
        i.setAction("com.android.systemui.aokp.LAUNCH_ACTION");
        i.putExtra("action", action);
        mContext.sendBroadcastAsUser(i, UserHandle.ALL);
    }

    private void maybeSkipKeyguard() {
        try {
            if (mWm.isKeyguardLocked() && !mWm.isKeyguardSecure()) {
                ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
            }
        } catch (RemoteException ignored) {
        }
    }

    private void collapseStatusBar() {
        try {
            IStatusBarService sb = IStatusBarService.Stub
                    .asInterface(ServiceManager
                            .getService(Context.STATUS_BAR_SERVICE));
            sb.collapsePanels();
        } catch (RemoteException e) {
        }
    }

    public View getView() {
        return mView;
    }
}
