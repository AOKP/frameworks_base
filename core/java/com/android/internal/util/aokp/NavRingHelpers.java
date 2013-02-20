/*
 * Copyright (C) 2013 The CyanogenMod Project
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

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.TargetDrawable;

import java.net.URISyntaxException;

public class NavRingHelpers {

    private static final String ASSIST_ICON_METADATA_NAME = "com.android.systemui.action_assist_icon";

    private NavRingHelpers() {
    }

    public static TargetDrawable getTargetDrawable(Context context, String action) {
        int resourceId = -1;
        final Resources res = context.getResources();

        if (TextUtils.isEmpty(action) || action.equals(ACTION_NULL)) {
            resourceId = com.android.internal.R.drawable.ic_action_empty;
        } else if (action.equals(ACTION_ASSIST)) {
            return new TargetDrawable(res, com.android.internal.R.drawable.ic_action_assist_generic);
        } else if (action.equals(ACTION_SCREENSHOT)) {
            resourceId = com.android.internal.R.drawable.ic_action_screenshot;
        } else if (action.equals(ACTION_IME)) {
            resourceId = com.android.internal.R.drawable.ic_action_ime_switcher;
        } else if (action.equals(ACTION_VIB)) {
            resourceId = com.android.internal.R.drawable.ic_action_vib;
        } else if (action.equals(ACTION_SILENT)) {
            resourceId = com.android.internal.R.drawable.ic_action_silent;
        } else if (action.equals(ACTION_SILENT_VIB)) {
            resourceId = com.android.internal.R.drawable.ic_action_ring_vib_silent;
        } else if (action.equals(ACTION_LAST_APP)) {
            resourceId = com.android.internal.R.drawable.ic_action_lastapp;
        } else if (action.equals(ACTION_KILL)) {
            resourceId = com.android.internal.R.drawable.ic_action_killtask;
        } else if (action.equals(ACTION_POWER)) {
            resourceId = com.android.internal.R.drawable.ic_action_power;
        }

        if (resourceId < 0) {
            // no pre-defined action, try to resolve URI
            try {
                Intent intent = Intent.parseUri(action, 0);
                PackageManager pm = context.getPackageManager();
                ActivityInfo info = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);

                Drawable activityIcon = info.loadIcon(pm);
                Drawable iconBg = res.getDrawable(
                        com.android.internal.R.drawable.ic_navbar_blank);
                Drawable iconBgActivated = res.getDrawable(
                        com.android.internal.R.drawable.ic_navbar_blank_activated);

                int margin = (int)(iconBg.getIntrinsicHeight() / 3);
                LayerDrawable icon = new LayerDrawable (new Drawable[] { iconBg, activityIcon });
                LayerDrawable iconActivated = new LayerDrawable (new Drawable[] { iconBgActivated, activityIcon });

                icon.setLayerInset(1, margin, margin, margin, margin);
                iconActivated.setLayerInset(1, margin, margin, margin, margin);

                StateListDrawable selector = new StateListDrawable();
                selector.addState(new int[] {
                        android.R.attr.state_enabled,
                        -android.R.attr.state_active,
                        -android.R.attr.state_focused
                    }, icon);
                selector.addState(new int[] {
                        android.R.attr.state_enabled,
                        android.R.attr.state_active,
                        -android.R.attr.state_focused
                    }, iconActivated);
                selector.addState(new int[] {
                        android.R.attr.state_enabled,
                        -android.R.attr.state_active,
                        android.R.attr.state_focused
                    }, iconActivated);
                return new TargetDrawable(res, selector);
            } catch (URISyntaxException e) {
                resourceId = com.android.internal.R.drawable.ic_action_empty;
            }
        }

        TargetDrawable drawable = new TargetDrawable(res, resourceId);
        if (resourceId == com.android.internal.R.drawable.ic_action_empty) {
            drawable.setEnabled(false);
        }
        return drawable;
    }
}
