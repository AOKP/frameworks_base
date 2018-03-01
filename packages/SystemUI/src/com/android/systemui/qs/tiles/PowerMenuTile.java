/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.qs.GlobalSetting;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.tileimpl.QSTileImpl;

import com.android.systemui.R;

/** Quick settings tile: PowerMenu **/
public class PowerMenuTile extends QSTileImpl<BooleanState> {

    private boolean mListening;

    public PowerMenuTile(QSHost host) {
        super(host);
    }

    @Override
    public BooleanState newTileState() {
        return new BooleanState();
    }

    public void handleSetListening(boolean listening) {
        if (mListening == listening) return;
        mListening = listening;
    }

    @Override
    public void handleClick() {
        mHost.collapsePanels();
        Intent intent = new Intent(Intent.ACTION_POWERMENU);
        mContext.sendBroadcast(intent);
    }

    @Override
    public void handleLongClick() {
        mHost.collapsePanels();
        Intent intent = new Intent(Intent.ACTION_POWERMENU_REBOOT);
        mContext.sendBroadcast(intent);
    }

    @Override
    public Intent getLongClickIntent() {
        return null;
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_powermenu_label);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = mContext.getString(R.string.quick_settings_powermenu_label);
        state.icon = ResourceIcon.get(R.drawable.ic_qs_powermenu);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QUICK_SETTINGS;
    }
}