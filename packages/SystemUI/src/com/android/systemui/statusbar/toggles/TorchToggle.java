
package com.android.systemui.statusbar.toggles;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.provider.Settings;

import com.android.systemui.R;
import com.android.systemui.aokp.AwesomeAction;

public class TorchToggle extends StatefulToggle {

    @Override
    protected void init(Context c, int style) {
        super.init(c, style);
        new TorchObserver(new Handler()).observe();
    }

    @Override
    protected void doEnable() {
        AwesomeAction.getInstance(mContext).launchAction(AwesomeAction.ACTION_TORCH);
    }

    @Override
    protected void doDisable() {
        AwesomeAction.getInstance(mContext).launchAction(AwesomeAction.ACTION_TORCH);
    }

    @Override
    protected void updateView() {
        boolean enabled = Settings.System.getBoolean(mContext.getContentResolver(),
                Settings.System.TORCH_STATE, false);
        updateCurrentState(enabled ? State.ENABLED : State.DISABLED);
        setIcon(enabled
                ? R.drawable.ic_qs_torch_on
                : R.drawable.ic_qs_torch_off);
        setLabel(enabled
                ? mContext.getString(R.string.quick_settings_torch_on_label)
                : mContext.getString(R.string.quick_settings_torch_off_label));
        super.updateView();
    }

    protected class TorchObserver extends ContentObserver {
        TorchObserver(Handler handler) {
            super(handler);
            observe();
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.TORCH_STATE), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleViewUpdate();
        }
    }

}
