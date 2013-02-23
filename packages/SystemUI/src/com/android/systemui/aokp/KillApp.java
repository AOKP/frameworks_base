/*
 * Copyright 2011 Colin McDonough
 *
 * Modified for AOKP by Mike Wilson - Zaphod-Beeblebrox
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

import com.android.systemui.R;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.IActivityManager;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.widget.Toast;

import java.util.List;

public class KillApp extends Activity {
  public KillApp() {
    super();
  }

    public ActivityManager mActivityManager;

    private void killProcess() {
            mActivityManager = (ActivityManager) getSystemService(Activity.ACTIVITY_SERVICE);
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            String packageName = mActivityManager.getRunningTasks(2).get(1).topActivity.getPackageName();
            if (!defaultHomePackage.equals(packageName)) {
                    mActivityManager.forceStopPackage(packageName);
                    Toast.makeText(this, R.string.app_killed_message, Toast.LENGTH_SHORT).show();
            }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
       super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
            killProcess();
            this.finish();
    }
}