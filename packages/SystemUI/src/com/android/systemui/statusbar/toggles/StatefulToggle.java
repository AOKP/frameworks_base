
package com.android.systemui.statusbar.toggles;

import android.view.View;

public abstract class StatefulToggle extends BaseToggle {

    public enum State {
        ENABLED,
        DISABLED,
        ENABLING,
        DISABLING;
    }

    private State mState = State.DISABLED;

    protected final void updateCurrentState(final State state) {
        mState = state;
        scheduleViewUpdate();
    }

    @Override
    public final void onClick(View v) {
        State newState = null;

        switch (mState) {
            case DISABLING:
            case ENABLING:
                return;
            case DISABLED:
                newState = State.ENABLING;
                doEnable();
                break;
            case ENABLED:
                newState = State.DISABLING;
                doDisable();
                break;
        }
        updateCurrentState(newState);
    }

    public State getState() {
        return mState;
    }

    protected abstract void doEnable();

    protected abstract void doDisable();

    @Override
    protected void updateView() {
        super.updateView();

        boolean disable = mState == State.DISABLING || mState == State.ENABLING;
        mLabel.setEnabled(!disable);
    }

}
