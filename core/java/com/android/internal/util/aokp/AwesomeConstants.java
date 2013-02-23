/*
 * Copyright (C) 2013 AOKP by Mike Wilson - Zaphod-Beeblebrox && Steve Spear - Stevespear426
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

import java.util.HashMap;

public class AwesomeConstants {

    public static final String ASSIST_ICON_METADATA_NAME = "com.android.systemui.action_assist_icon";

    public final static int SWIPE_LEFT = 0;
    public final static int SWIPE_RIGHT = 1;
    public final static int SWIPE_DOWN = 2;
    public final static int SWIPE_UP = 3;
    public final static int TAP_DOUBLE = 4;
    public final static int PRESS_LONG = 5;
    public final static int SPEN_REMOVE = 6;
    public final static int SPEN_INSERT = 7;

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
    public final static String ACTION_LAST_APP = "**lastapp**";
    public final static String ACTION_RECENTS_GB = "**recentsgb**";
    public final static String ACTION_NULL = "**null**";

    public final static int INT_ACTION_HOME = 0;
    public final static int INT_ACTION_BACK = 1;
    public final static int INT_ACTION_SCREENSHOT = 2;
    public final static int INT_ACTION_MENU = 3;
    public final static int INT_ACTION_POWER = 4;
    public final static int INT_ACTION_NOTIFICATIONS = 5;
    public final static int INT_ACTION_RECENTS = 6;
    public final static int INT_ACTION_IME = 7;
    public final static int INT_ACTION_KILL = 8;
    public final static int INT_ACTION_ASSIST = 9;
    public final static int INT_ACTION_CUSTOM = 10;
    public final static int INT_ACTION_SILENT = 11;
    public final static int INT_ACTION_VIB = 12;
    public final static int INT_ACTION_SILENT_VIB = 13;
    public final static int INT_ACTION_EVENT = 14;
    public final static int INT_ACTION_ALARM = 15;
    public final static int INT_ACTION_TODAY = 16;
    public final static int INT_ACTION_CLOCKOPTIONS = 17;
    public final static int INT_ACTION_VOICEASSIST = 18;
    public final static int INT_ACTION_TORCH = 19;
    public final static int INT_ACTION_SEARCH = 20;
    public final static int INT_ACTION_LAST_APP = 21;
    public final static int INT_ACTION_NULL = 22;
    public final static int INT_ACTION_RECENTS_GB = 23;

    public final static HashMap<String, Integer> actionMap = new HashMap<String, Integer>();

    static {
        actionMap.put(ACTION_HOME, INT_ACTION_HOME);
        actionMap.put(ACTION_BACK, INT_ACTION_BACK);
        actionMap.put(ACTION_SCREENSHOT, INT_ACTION_SCREENSHOT);
        actionMap.put(ACTION_MENU, INT_ACTION_MENU);
        actionMap.put(ACTION_POWER, INT_ACTION_POWER);
        actionMap.put(ACTION_NOTIFICATIONS, INT_ACTION_NOTIFICATIONS);
        actionMap.put(ACTION_RECENTS, INT_ACTION_RECENTS);
        actionMap.put(ACTION_IME, INT_ACTION_IME);
        actionMap.put(ACTION_KILL, INT_ACTION_KILL);
        actionMap.put(ACTION_ASSIST, INT_ACTION_ASSIST);
        actionMap.put(ACTION_CUSTOM, INT_ACTION_CUSTOM);
        actionMap.put(ACTION_SILENT, INT_ACTION_SILENT);
        actionMap.put(ACTION_VIB, INT_ACTION_VIB);
        actionMap.put(ACTION_SILENT_VIB, INT_ACTION_SILENT_VIB);
        actionMap.put(ACTION_EVENT, INT_ACTION_EVENT);
        actionMap.put(ACTION_ALARM, INT_ACTION_ALARM);
        actionMap.put(ACTION_TODAY, INT_ACTION_TODAY);
        actionMap.put(ACTION_CLOCKOPTIONS, INT_ACTION_CLOCKOPTIONS);
        actionMap.put(ACTION_VOICEASSIST, INT_ACTION_VOICEASSIST);
        actionMap.put(ACTION_TORCH, INT_ACTION_TORCH);
        actionMap.put(ACTION_SEARCH, INT_ACTION_SEARCH);
        actionMap.put(ACTION_LAST_APP, INT_ACTION_LAST_APP);
        actionMap.put(ACTION_NULL, INT_ACTION_NULL);
        actionMap.put(ACTION_RECENTS_GB, INT_ACTION_RECENTS_GB);
    }
}
