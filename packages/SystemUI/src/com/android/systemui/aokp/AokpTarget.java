/*
 * Copyright 2011 AOKP by Mike Wilson - Zaphod-Beeblebrox
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

import java.net.URISyntaxException;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManagerNative;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.widget.Toast;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;

/*
 * Helper classes for managing AOKP custom actions
 */

public class AokpTarget {

    final String TAG = "AOKPTarget";

    public final static String ACTION_HOME = "**home**";
    public final static String ACTION_BACK = "**back**";
    public final static String ACTION_SCREENSHOT = "**screenshot**";
    public final static String ACTION_MENU = "**menu**";
    public final static String ACTION_POWER = "**power**";
    public final static String ACTION_NOTIFICATIONS = "**notifications**";
    public final static String ACTION_RECENTS = "**recents**";
    public final static String ACTION_IME = "**ime**";
    public final static String ACTION_KILL = "**kill**";
    public final static String ACTION_ASSIST = "**assist**";
    public final static String ACTION_CUSTOM = "**custom**";
    public final static String ACTION_SILENT = "**ring_silent**";
    public final static String ACTION_VIB = "**ring_vib**";
    public final static String ACTION_SILENT_VIB = "**ring_vib_silent**";
    public final static String ACTION_EVENT = "**event**";
    public final static String ACTION_ALARM = "**alarm**";
    public final static String ACTION_TODAY = "**today**";
    public final static String ACTION_CLOCKOPTIONS = "**clockoptions**";
	public final static String ACTION_VOICEASSIST = "**voiceassist**";
	public final static String ACTION_TORCH = "**torch**";
	public final static String ACTION_SEARCH = "**search**";
    public final static String ACTION_NULL = "**null**";

    private int mInjectKeyCode;
    private Context mContext;
    private Handler mHandler;

    final Object mScreenshotLock = new Object();
    ServiceConnection mScreenshotConnection = null;

    public AokpTarget (Context context){
        mContext = context;
        mHandler = new Handler();
    }

    public boolean launchAction (String action){

        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }

        if (action == null || action.equals(ACTION_NULL)) {
            return false;
        }
        if (action.equals(ACTION_HOME)) {
            injectKeyDelayed(KeyEvent.KEYCODE_HOME);
            return true;
        }
        if (action.equals(ACTION_BACK)) {
            injectKeyDelayed(KeyEvent.KEYCODE_BACK);
            return true;
        }
        if (action.equals(ACTION_MENU)) {
            injectKeyDelayed(KeyEvent.KEYCODE_MENU);
            return true;
        }
        if (action.equals(ACTION_POWER)) {
            injectKeyDelayed(KeyEvent.KEYCODE_POWER);
            return true;
        }
        if (action.equals(ACTION_IME)) {
            mContext.sendBroadcast(new Intent("android.settings.SHOW_INPUT_METHOD_PICKER"));
            return true;
        }
        if (action.equals(ACTION_SCREENSHOT)) {
            takeScreenshot();
            return true;
        }
        if (action.equals(ACTION_TORCH)) {
            Intent intent = new Intent("android.intent.action.MAIN");
            intent.setComponent(ComponentName.unflattenFromString("com.aokp.Torch/.TorchActivity"));
            intent.addCategory("android.intent.category.LAUNCHER");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
        }
        if (action.equals(ACTION_TODAY)) {
            long startMillis = System.currentTimeMillis();
            Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendPath("time");
            ContentUris.appendId(builder, startMillis);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                      .setData(builder.build());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
        }
        if (action.equals(ACTION_CLOCKOPTIONS)) {
            Intent intent = new Intent(Intent.ACTION_QUICK_CLOCK);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
        }
        if (action.equals(ACTION_EVENT)) {
            Intent intent = new Intent(Intent.ACTION_INSERT)
                      .setData(Events.CONTENT_URI);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
        }
        if (action.equals(ACTION_VOICEASSIST)) {
            Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
        }
        if (action.equals(ACTION_ALARM)) {
			Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
			return true;
		}
        if (action.equals(ACTION_ASSIST)) {
            Intent intent = new Intent(Intent.ACTION_ASSIST);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
        }
        if (action.equals(ACTION_KILL)) {
            mHandler.post(mKillTask);
            return true;
        }

        if (action.equals(ACTION_VIB)) {
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if(am != null){
                if(am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                    am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    Vibrator vib = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                    if(vib != null){
                        vib.vibrate(50);
                    }
                }else{
                    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                    if(tg != null){
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                    }
                }
            }
            return true;
        }
        if (action.equals(ACTION_SILENT)) {
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if(am != null){
                if(am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                    am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                }else{
                    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                    if(tg != null){
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                    }
                }
            }
            return true;
        }
        if (action.equals(ACTION_SILENT_VIB)) {
            AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
            if(am != null){
                if(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                    am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    Vibrator vib = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                    if(vib != null){
                        vib.vibrate(50);
                    }
                } else if(am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                    am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                } else {
                    am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                    if(tg != null){
                        tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                    }
                }
            }
            return true;
        }

        if (action.equals(ACTION_RECENTS)) {
            mHandler.post(mToggleRecents);
            return true;
        }
        if (action.equals(ACTION_NOTIFICATIONS)) {
            try {
                IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).expandNotificationsPanel();
            } catch (RemoteException e) {
                // A RemoteException is like a cold
                // Let's hope we don't catch one!
            }
            return true;
        }
            // we must have a custom uri
        try {
            Intent intent = Intent.parseUri(action, 0);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
            } catch (URISyntaxException e) {
                    Log.e(TAG, "URISyntaxException: [" + action + "]");
            } catch (ActivityNotFoundException e){
                    Log.e(TAG, "ActivityNotFound: [" + action + "]");
            }
        return false; // we didn't handle the action!
    }


    //not using yet and dont want to take time to get drawables... yes lazy dev.
    // Yes Steve, You are a lazy Dev.  I need this :)  - Zaphod 12-01-12
    public Drawable getIconImage(String uri) {

        if (uri == null)
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_null);
        if (uri.equals(ACTION_HOME))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_home);
        if (uri.equals(ACTION_BACK))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_back);
        if (uri.equals(ACTION_RECENTS))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_recent);
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
        try {
            return mContext.getPackageManager().getActivityIcon(Intent.parseUri(uri, 0));
            } catch (NameNotFoundException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        return mContext.getResources().getDrawable(R.drawable.ic_sysbar_null);
    } 

    public String getProperSummary(String uri) {
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
        if (uri.equals(ACTION_NULL))
            return mContext.getResources().getString(R.string.action_none);
        try {
            Intent intent = Intent.parseUri(uri, 0);
            if (Intent.ACTION_MAIN.equals(intent.getAction())) {
                return getFriendlyActivityName(intent);
            }
            return getFriendlyShortcutName(intent);
        } catch (URISyntaxException e) {
        }
        return mContext.getResources().getString(R.string.action_none);
    }

    private String getFriendlyActivityName(Intent intent) {
        PackageManager pm = mContext.getPackageManager();
        ActivityInfo ai = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
        String friendlyName = null;

        if (ai != null) {
            friendlyName = ai.loadLabel(pm).toString();
            if (friendlyName == null) {
                friendlyName = ai.name;
            }
        }

        return (friendlyName != null)  ? friendlyName : intent.toUri(0);
    }

    private String getFriendlyShortcutName(Intent intent) {
        String activityName = getFriendlyActivityName(intent);
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (activityName != null && name != null) {
            return activityName + ": " + name;
        }
        return name != null ? name : intent.toUri(0);
    }

    private void injectKeyDelayed(int keycode){
        mInjectKeyCode = keycode;
        mHandler.removeCallbacks(onInjectKey_Down);
        mHandler.removeCallbacks(onInjectKey_Up);
        mHandler.post(onInjectKey_Down);
        mHandler.postDelayed(onInjectKey_Up,10); // introduce small delay to handle key press
    }

    final Runnable onInjectKey_Down = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev,
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    final Runnable onInjectKey_Up = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev,
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    Runnable mKillTask = new Runnable() {
        public void run() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            final ActivityManager am = (ActivityManager) mContext.getSystemService(Activity.ACTIVITY_SERVICE);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            String packageName = am.getRunningTasks(1).get(0).topActivity.getPackageName();
            if (!defaultHomePackage.equals(packageName)) {
                    am.forceStopPackage(packageName);
                    Toast.makeText(mContext, R.string.app_killed_message, Toast.LENGTH_SHORT).show();
            }
        }
    };

    Runnable mToggleRecents = new Runnable() {
        @Override
        public void run() {
            try {
                IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(Context.STATUS_BAR_SERVICE)).toggleRecentApps();
            } catch (RemoteException e) {
            }
        }
    };

    final Runnable mScreenshotTimeout = new Runnable() {
        @Override
        public void run() {
            synchronized (mScreenshotLock) {
                if (mScreenshotConnection != null) {
                    mContext.unbindService(mScreenshotConnection);
                    mScreenshotConnection = null;
                }
            }
        }
    };

    private void takeScreenshot() {
        synchronized (mScreenshotLock) {
            if (mScreenshotConnection != null) {
                return;
            }
            ComponentName cn = new ComponentName("com.android.systemui",
                    "com.android.systemui.screenshot.TakeScreenshotService");
            Intent intent = new Intent();
            intent.setComponent(cn);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    synchronized (mScreenshotLock) {
                        if (mScreenshotConnection != this) {
                            return;
                        }
                        Messenger messenger = new Messenger(service);
                        Message msg = Message.obtain(null, 1);
                        final ServiceConnection myConn = this;
                        Handler h = new Handler(H.getLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                synchronized (mScreenshotLock) {
                                    if (mScreenshotConnection == myConn) {
                                        mContext.unbindService(mScreenshotConnection);
                                        mScreenshotConnection = null;
                                        H.removeCallbacks(mScreenshotTimeout);
                                    }
                                }
                            }
                        };
                        msg.replyTo = new Messenger(h);
                        msg.arg1 = msg.arg2 = 0;

                        /*
                         * remove for the time being if (mStatusBar != null &&
                         * mStatusBar.isVisibleLw()) msg.arg1 = 1; if
                         * (mNavigationBar != null &&
                         * mNavigationBar.isVisibleLw()) msg.arg2 = 1;
                         */

                        /* wait for the dialog box to close */
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ie) {
                        }

                        /* take the screenshot */
                        try {
                            messenger.send(msg);
                        } catch (RemoteException e) {
                        }
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                }
            };
            if (mContext.bindService(intent, conn, mContext.BIND_AUTO_CREATE)) {
                mScreenshotConnection = conn;
                H.postDelayed(mScreenshotTimeout, 10000);
            }
        }
    }

    private Handler H = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {

            }
        }
    };
}