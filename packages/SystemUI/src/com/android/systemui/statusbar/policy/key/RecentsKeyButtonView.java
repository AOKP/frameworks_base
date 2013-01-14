
package com.android.systemui.statusbar.policy.key;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.AttributeSet;
import android.view.View;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;
import com.android.systemui.recent.RecentTasksLoader;
import com.android.systemui.statusbar.policy.ExtensibleKeyButtonView;

public class RecentsKeyButtonView extends ExtensibleKeyButtonView {

    private boolean mRecentsLocked = false;

    public RecentsKeyButtonView(Context context, AttributeSet attrs, String clickAction,
            String longPress) {
        super(context, attrs, clickAction, longPress);
        setActions(clickAction, longPress);
    }

    @Override
    public void setActions(String clickAction, String longPress) {
        setLongPress(true);
        setId(R.id.recent_apps);
        setOnClickListener(mClickListener);
        setOnTouchListener(RecentTasksLoader.getInstance(mContext));
    }

    protected OnClickListener mClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mRecentsLocked)
                return;

            try {
                IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(Context.STATUS_BAR_SERVICE))
                        .toggleRecentApps();
            } catch (RemoteException e) {
                // nuu
            }
            mRecentsLocked = true;
            postDelayed(mUnlockRecents, 100); // just to prevent spamming, it
                                              // looks ugly
        }
    };

    private Runnable mUnlockRecents = new Runnable() {
        @Override
        public void run() {
            mRecentsLocked = false;
        }
    };
}
