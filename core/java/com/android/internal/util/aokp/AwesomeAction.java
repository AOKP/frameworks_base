/*
 * Copyright (C) 2013 Android Open Kang Project
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

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import com.android.internal.statusbar.IStatusBarService;

import java.net.URISyntaxException;
import java.util.List;

import static com.android.internal.util.aokp.AwesomeConstants.AwesomeConstant;
import static com.android.internal.util.aokp.AwesomeConstants.fromString;

public class AwesomeAction {

    public final static String TAG = "AwesomeAction";
    private final static String SysUIPackage = "com.android.systemui";

    private AwesomeAction() {
    }

    public static boolean launchAction(final Context mContext, final String action) {
        if (TextUtils.isEmpty(action) || action.equals(AwesomeConstant.ACTION_NULL.value())) {
            return false;
        }
        final IStatusBarService barService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        new Thread(new Runnable() {
            @Override
            public void run() {
                AwesomeConstant AwesomeEnum = fromString(action);
                AudioManager am;
                switch (AwesomeEnum) {
                    case ACTION_RECENTS:
                        try {
                            barService.toggleRecentApps();
                        } catch (RemoteException e) {
                            // let it go.
                        }
                        break;
                    case ACTION_ASSIST:
                        Intent intent = new Intent(Intent.ACTION_ASSIST);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        if (isIntentAvailable(mContext, intent))
                            mContext.startActivity(intent);
                        break;
                    case ACTION_HOME:
                        triggerVirtualKeypress(KeyEvent.KEYCODE_HOME);
                        break;
                    case ACTION_BACK:
                        triggerVirtualKeypress(KeyEvent.KEYCODE_BACK);
                        break;
                    case ACTION_MENU:
                        triggerVirtualKeypress(KeyEvent.KEYCODE_MENU);
                        break;
                    case ACTION_SEARCH:
                        triggerVirtualKeypress(KeyEvent.KEYCODE_SEARCH);
                        break;
                    case ACTION_RECENTS_GB:
                        triggerVirtualKeypress(KeyEvent.KEYCODE_APP_SWITCH);
                        break;
                    case ACTION_KILL:
                        try {
                            barService.toggleKillApp();
                        } catch (RemoteException e) {
                        }
                        break;
                    case ACTION_APP_WINDOW:
                        Intent appWindow = new Intent();
                        appWindow.setAction("com.android.systemui.ACTION_SHOW_APP_WINDOW");
                        mContext.sendBroadcast(appWindow);
                        break;
                    case ACTION_VIB:
                        am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) {
                            if (am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                                am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                                Vibrator vib = (Vibrator) mContext
                                        .getSystemService(Context.VIBRATOR_SERVICE);
                                if (vib != null) {
                                    vib.vibrate(50);
                                }
                            } else {
                                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                                ToneGenerator tg = new ToneGenerator(
                                        AudioManager.STREAM_NOTIFICATION,
                                        (int) (ToneGenerator.MAX_VOLUME * 0.85));
                                if (tg != null) {
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                                }
                            }
                        }
                        break;
                    case ACTION_SILENT:
                        am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) {
                            if (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                                am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                            } else {
                                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                                ToneGenerator tg = new ToneGenerator(
                                        AudioManager.STREAM_NOTIFICATION,
                                        (int) (ToneGenerator.MAX_VOLUME * 0.85));
                                if (tg != null) {
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                                }
                            }
                        }
                        break;
                    case ACTION_SILENT_VIB:
                        am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                        if (am != null) {
                            if (am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                                am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                                Vibrator vib = (Vibrator) mContext
                                        .getSystemService(Context.VIBRATOR_SERVICE);
                                if (vib != null) {
                                    vib.vibrate(50);
                                }
                            } else if (am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                                am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                            } else {
                                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                                ToneGenerator tg = new ToneGenerator(
                                        AudioManager.STREAM_NOTIFICATION,
                                        (int) (ToneGenerator.MAX_VOLUME * 0.85));
                                if (tg != null) {
                                    tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                                }
                            }
                        }
                        break;
                    case ACTION_POWER:
                        triggerVirtualKeypress(KeyEvent.KEYCODE_POWER);
                        break;
                    case ACTION_IME:
                        mContext.sendBroadcast(new Intent(
                                "android.settings.SHOW_INPUT_METHOD_PICKER"));
                        break;
                    case ACTION_TORCH:
                        Intent intentTorch = new Intent("android.intent.action.MAIN");
                        intentTorch.setComponent(ComponentName
                                .unflattenFromString("com.aokp.Torch/.TorchActivity"));
                        intentTorch.addCategory("android.intent.category.LAUNCHER");
                        intentTorch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intentTorch);
                        break;
                    case ACTION_TODAY:
                        long startMillis = System.currentTimeMillis();
                        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                        builder.appendPath("time");
                        ContentUris.appendId(builder, startMillis);
                        Intent intentToday = new Intent(Intent.ACTION_VIEW)
                                .setData(builder.build());
                        intentToday.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intentToday);
                        break;
                    case ACTION_CLOCKOPTIONS:
                        Intent intentClock = new Intent(Intent.ACTION_QUICK_CLOCK);
                        intentClock.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intentClock);
                        break;
                    case ACTION_EVENT:
                        Intent intentEvent = new Intent(Intent.ACTION_INSERT)
                                .setData(Events.CONTENT_URI);
                        intentEvent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intentEvent);
                        break;
                    case ACTION_VOICEASSIST:
                        Intent intentVoice = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
                        intentVoice.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intentVoice);
                        break;
                    case ACTION_ALARM:
                        Intent intentAlarm = new Intent(AlarmClock.ACTION_SET_ALARM);
                        intentAlarm.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intentAlarm);
                        break;
                    case ACTION_LAST_APP:
                        try {
                            barService.toggleLastApp();
                        } catch (RemoteException e) {
                        }
                        break;
                    case ACTION_NOTIFICATIONS:
                        try {
                            barService.expandNotificationsPanel();
                        } catch (RemoteException e) {
                            // A RemoteException is like a cold
                            // Let's hope we don't catch one!
                        }
                        break;
                    case ACTION_APP:
                        try {
                            Intent intentapp = Intent.parseUri(action, 0);
                            intentapp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            mContext.startActivity(intentapp);
                        } catch (URISyntaxException e) {
                            Log.e(TAG, "URISyntaxException: [" + action + "]");
                        } catch (ActivityNotFoundException e) {
                            Log.e(TAG, "ActivityNotFound: [" + action + "]");
                        }
                        break;
                    case ACTION_CAMERA:
                        Intent camera = new Intent("android.media.action.IMAGE_CAPTURE");
                        camera.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(camera);
                        break;
                    case ACTION_DPAD_LEFT:
                        triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_LEFT);
                        break;
                    case ACTION_DPAD_RIGHT:
                        triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_RIGHT);
                        break;
                }

            }
        }).start();

        return true;
    }

    public static boolean isIntentAvailable(Context context, Intent intent) {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    public static void triggerVirtualKeypress(final int keyCode) {
        InputManager im = InputManager.getInstance();
        long now = SystemClock.uptimeMillis();
        int downflags = 0;
        int upflags = 0;
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
            || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
            || keyCode == KeyEvent.KEYCODE_DPAD_UP
            || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            downflags = upflags = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;
        } else {
            downflags = upflags = KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY;
        }

        final KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                downflags,
                InputDevice.SOURCE_KEYBOARD);
        im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);

        final KeyEvent upEvent = new KeyEvent(now, now, KeyEvent.ACTION_UP,
                keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                upflags,
                InputDevice.SOURCE_KEYBOARD);
        im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }
}
