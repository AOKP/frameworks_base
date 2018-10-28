/*
 * Copyright (C) 2018 AOKP Project
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

package com.android.systemui.qs.tiles;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.systemui.Dependency;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import com.android.systemui.R;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ROMControlTile extends QSTileImpl<BooleanState> {
    private boolean mListening;
    private final ActivityStarter mActivityStarter;
    private static final Intent ROMCONTROL = new Intent().setComponent(new ComponentName(
            "com.aokp.romcontrol", "com.aokp.romcontrol.MainActivity"));
    private static final Intent ABOUT = new Intent().setComponent(new ComponentName(
            "com.android.settings", "Settings$DeviceInfoSettingsActivity"));

    public ROMControlTile(QSHost host) {
        super(host);
        mActivityStarter = Dependency.get(ActivityStarter.class);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QUICK_SETTINGS;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_romcontrol_label);
    }

    @Override
    protected void handleClick() {
        mHost.collapsePanels();
        mActivityStarter.postStartActivityDismissingKeyguard(ROMCONTROL, 0);
        refreshState();
    }

    @Override
    public void handleLongClick() {
        mHost.collapsePanels();
        mActivityStarter.postStartActivityDismissingKeyguard(ABOUT, 0);
        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }


    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.icon = ResourceIcon.get(R.drawable.ic_qs_romcontrol);
        state.label = mContext.getString(R.string.quick_settings_romcontrol_label);

    }

    @Override
    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }
}