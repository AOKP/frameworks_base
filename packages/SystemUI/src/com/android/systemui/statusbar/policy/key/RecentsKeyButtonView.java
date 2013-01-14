package com.android.systemui.statusbar.policy.key;

import android.content.Context;
import android.util.AttributeSet;

import com.android.systemui.R;
import com.android.systemui.recent.RecentTasksLoader;
import com.android.systemui.statusbar.policy.ExtensibleKeyButtonView;

public class RecentsKeyButtonView extends ExtensibleKeyButtonView {

    public RecentsKeyButtonView(Context context, AttributeSet attrs, String ClickAction,
            String Longpress) {
        super(context, attrs, ClickAction, Longpress);
    }

    @Override
    public void setActions(String ClickAction, String Longpress) {
        setLongPress(true);
        setId(R.id.recent_apps);
        setOnClickListener(mClickListener);
        setOnTouchListener(RecentTasksLoader.getInstance(mContext));
    }
}
