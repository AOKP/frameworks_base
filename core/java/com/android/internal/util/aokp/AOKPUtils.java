/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.ConnectivityManager;
import android.os.UserHandle;
import android.util.Log;

import java.util.List;

import java.util.Locale;

public class AOKPUtils {

    private static final String TAG = "AOKPUtils";

    public static boolean isChineseLanguage() {
       return Resources.getSystem().getConfiguration().locale.getLanguage().startsWith(
               Locale.CHINESE.getLanguage());
    }

    public static boolean isWifiOnly(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        return (cm.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) == false);
    }

   /**
     * Checks if a specific package is installed.
     *
     * @param context     The context to retrieve the package manager
     * @param packageName The name of the package
     * @return Whether the package is installed or not.
     */
    public static boolean isPackageInstalled(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            if (pm != null) {
                List<ApplicationInfo> packages = pm.getInstalledApplications(0);
                for (ApplicationInfo packageInfo : packages) {
                    if (packageInfo.packageName.equals(packageName)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Checks if a specific service is running.
     *
     * @param context     The context to retrieve the activity manager
     * @param serviceName The name of the service
     * @return Whether the service is running or not
     */
    public static boolean isServiceRunning(Context context, String serviceName) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> services = activityManager
                .getRunningServices(Integer.MAX_VALUE);

        if (services != null) {
            for (ActivityManager.RunningServiceInfo info : services) {
                if (info.service != null) {
                    if (info.service.getClassName() != null && info.service.getClassName()
                            .equalsIgnoreCase(serviceName)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if system has a camera.
     *
     * @param context
     * @return
     */
    public static boolean hasCamera(final Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm != null && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    /**
     * Check if system has a front camera.
     *
     * @param context
     * @return
     */
    public static boolean hasFrontCamera(final Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm != null && pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }

    public static boolean deviceSupportsFlashLight(Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(
                Context.CAMERA_SERVICE);
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer lensFacing = c.get(CameraCharacteristics.LENS_FACING);
                if (flashAvailable != null
                        && flashAvailable
                        && lensFacing != null
                        && lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
            // Ignore
        }
        return false;
    }

    // Omni Switch Constants

    /**
     * Package name of the omnniswitch app
     */
    public static final String APP_PACKAGE_NAME = "org.omnirom.omniswitch";

    /**
     * Intent broadcast action for toogle the omniswitch overlay
     */
    private static final String ACTION_TOGGLE_OVERLAY2 = APP_PACKAGE_NAME + ".ACTION_TOGGLE_OVERLAY2";

    /**
     * Intent broadcast action for telling omniswitch to preload tasks
     */
    private static final String ACTION_PRELOAD_TASKS = APP_PACKAGE_NAME + ".ACTION_PRELOAD_TASKS";

    /**
     * Intent broadcast action for restoring the home stack
     */
    private static final String ACTION_RESTORE_HOME_STACK = APP_PACKAGE_NAME + ".ACTION_RESTORE_HOME_STACK";

    /**
     * Intent for launching the omniswitch settings actvity
     */
    public static Intent INTENT_LAUNCH_APP = new Intent(Intent.ACTION_MAIN)
            .setClassName(APP_PACKAGE_NAME, APP_PACKAGE_NAME + ".SettingsActivity");

    public static boolean isLuckyPatcherInstalled(final Context context) {
        boolean mluckyPatcherInstalled = false;
        try {
            mluckyPatcherInstalled = (context.getPackageManager().getPackageInfo(
                    "com.android.vending.billing.InAppBillingService.LOCK", 0).versionCode > 0);
        } catch (PackageManager.NameNotFoundException e) {
        }
        return mluckyPatcherInstalled;
    }

    /**
     * @hide
     */
    public static void toggleOmniSwitchRecents(Context context, UserHandle user) {
        final Intent intent = new Intent(AOKPUtils.ACTION_TOGGLE_OVERLAY2);
        intent.setPackage(APP_PACKAGE_NAME);
        context.sendBroadcastAsUser(intent, user);
    }

    /**
     * @hide
     */
    public static void restoreHomeStack(Context context, UserHandle user) {
        final Intent intent = new Intent(AOKPUtils.ACTION_RESTORE_HOME_STACK);
        intent.setPackage(APP_PACKAGE_NAME);
        context.sendBroadcastAsUser(intent, user);
    }

    /**
     * @hide
     */
    public static void preloadOmniSwitchRecents(Context context, UserHandle user) {
        final Intent intent = new Intent(AOKPUtils.ACTION_PRELOAD_TASKS);
        intent.setPackage(APP_PACKAGE_NAME);
        context.sendBroadcastAsUser(intent, user);
    }

    public static int getBlendColorForPercent(int fullColor, int emptyColor, boolean reversed,
                                        int percentage) {
        float[] newColor = new float[3];
        float[] empty = new float[3];
        float[] full = new float[3];
        Color.colorToHSV(fullColor, full);
        int fullAlpha = Color.alpha(fullColor);
        Color.colorToHSV(emptyColor, empty);
        int emptyAlpha = Color.alpha(emptyColor);
        float blendFactor = percentage/100f;
        if (reversed) {
            if (empty[0] < full[0]) {
                empty[0] += 360f;
            }
            newColor[0] = empty[0] - (empty[0]-full[0])*blendFactor;
        } else {
            if (empty[0] > full[0]) {
                full[0] += 360f;
            }
            newColor[0] = empty[0] + (full[0]-empty[0])*blendFactor;
        }
        if (newColor[0] > 360f) {
            newColor[0] -= 360f;
        } else if (newColor[0] < 0) {
            newColor[0] += 360f;
        }
        newColor[1] = empty[1] + ((full[1]-empty[1])*blendFactor);
        newColor[2] = empty[2] + ((full[2]-empty[2])*blendFactor);
        int newAlpha = (int) (emptyAlpha + ((fullAlpha-emptyAlpha)*blendFactor));
        return Color.HSVToColor(newAlpha, newColor);
    }
}