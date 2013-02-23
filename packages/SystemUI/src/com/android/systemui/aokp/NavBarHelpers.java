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

package com.android.systemui.aokp;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.media.AudioManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import static com.android.internal.util.aokp.AwesomeConstants.*;
import com.android.systemui.R;
import java.io.File;
import java.net.URISyntaxException;

public class NavBarHelpers {

    private NavBarHelpers() {
    }

    public static Drawable getIconImage(Context mContext, String uri) {
        if (TextUtils.isEmpty(uri))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_null);
        if (uri.equals(ACTION_HOME))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_home);
        if (uri.equals(ACTION_BACK))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_back);
        if (uri.equals(ACTION_RECENTS))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_recent);
        if (uri.equals(ACTION_RECENTS_GB))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_recent_gb);
        if (uri.equals(ACTION_SCREENSHOT))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_screenshot);
        if (uri.equals(ACTION_MENU))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_menu_big);
        if (uri.equals(ACTION_IME))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_ime_switcher);
        if (uri.equals(ACTION_KILL))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_killtask);
        if (uri.equals(ACTION_POWER))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_power);
        if (uri.equals(ACTION_SEARCH))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_search);
        if (uri.equals(ACTION_NOTIFICATIONS))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_notifications);
        if (uri.equals(ACTION_LAST_APP))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_lastapp);
        try {
            return mContext.getPackageManager().getActivityIcon(Intent.parseUri(uri, 0));
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return mContext.getResources().getDrawable(R.drawable.ic_sysbar_null);
    }

    public static String getProperSummary(Context mContext, String uri) {
        if (uri.equals(ACTION_HOME))
            return mContext.getResources().getString(R.string.action_home);
        if (uri.equals(ACTION_BACK))
            return mContext.getResources().getString(R.string.action_back);
        if (uri.equals(ACTION_RECENTS))
            return mContext.getResources().getString(R.string.action_recents);
        if (uri.equals(ACTION_SCREENSHOT))
            return mContext.getResources().getString(R.string.action_screenshot);
        if (uri.equals(ACTION_MENU))
            return mContext.getResources().getString(R.string.action_menu);
        if (uri.equals(ACTION_IME))
            return mContext.getResources().getString(R.string.action_ime);
        if (uri.equals(ACTION_KILL))
            return mContext.getResources().getString(R.string.action_kill);
        if (uri.equals(ACTION_POWER))
            return mContext.getResources().getString(R.string.action_power);
        if (uri.equals(ACTION_SEARCH))
            return mContext.getResources().getString(R.string.action_search);
        if (uri.equals(ACTION_NOTIFICATIONS))
            return mContext.getResources().getString(R.string.action_notifications);
        if (uri.equals(ACTION_LAST_APP))
            return mContext.getResources().getString(R.string.action_lastapp);
        if (uri.equals(ACTION_NULL))
            return mContext.getResources().getString(R.string.action_none);
        try {
            Intent intent = Intent.parseUri(uri, 0);
            if (Intent.ACTION_MAIN.equals(intent.getAction())) {
                return getFriendlyActivityName(mContext, intent);
            }
            return getFriendlyShortcutName(mContext, intent);
        } catch (URISyntaxException e) {
        }
        return mContext.getResources().getString(R.string.action_none);
    }

    private static String getFriendlyActivityName(Context mContext, Intent intent) {
        PackageManager pm = mContext.getPackageManager();
        ActivityInfo ai = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
        String friendlyName = null;

        if (ai != null) {
            friendlyName = ai.loadLabel(pm).toString();
            if (friendlyName == null) {
                friendlyName = ai.name;
            }
        }

        return (friendlyName != null) ? friendlyName : intent.toUri(0);
    }

    private static String getFriendlyShortcutName(Context mContext, Intent intent) {
        String activityName = getFriendlyActivityName(mContext, intent);
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (activityName != null && name != null) {
            return activityName + ": " + name;
        }
        return name != null ? name : intent.toUri(0);
    }
}
