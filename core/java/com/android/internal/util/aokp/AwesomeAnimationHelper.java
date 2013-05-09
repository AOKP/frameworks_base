/*
 * Copyright (C) 2013 AOKP by Steve Spear - Stevespear426
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

import android.content.Context;
import android.content.res.Resources;
//import android.view.animation.Animation;
//import android.view.animation.AnimationUtils;

public class AwesomeAnimationHelper {

    public final static int ANIMATION_DEFAULT = 0;
    public final static int ANIMATION_FADE = 1;
    public final static int ANIMATION_SLIDE_RIGHT = 2;
    public final static int ANIMATION_SLIDE_LEFT = 3;
    public final static int ANIMATION_SLIDE_UP = 4;

    public static int[] getAnimationsList() {
        int[] anim = new int[5];
        anim[0] = ANIMATION_DEFAULT;
        anim[1] = ANIMATION_FADE;
        anim[2] = ANIMATION_SLIDE_RIGHT;
        anim[3] = ANIMATION_SLIDE_LEFT;
        anim[4] = ANIMATION_SLIDE_UP;
        return anim;
    }

    public static int[] getAnimations(int mAnim) {
        int[] anim = new int[2];
        switch (mAnim) {
            case ANIMATION_FADE:
                anim[0] = com.android.internal.R.anim.fade_out;
                anim[1] = com.android.internal.R.anim.fade_in;
                break;
            case ANIMATION_SLIDE_RIGHT:
                anim[0] = com.android.internal.R.anim.slide_out_right;
                anim[1] = com.android.internal.R.anim.slide_in_right;
                break;
            case ANIMATION_SLIDE_LEFT:
                anim[0] = com.android.internal.R.anim.slide_in_left;
                anim[1] = com.android.internal.R.anim.slide_out_left;
                break;
            case ANIMATION_SLIDE_UP:
                anim[0] = com.android.internal.R.anim.slide_out_down;
                anim[1] = com.android.internal.R.anim.slide_in_up;
                break;
        }
        return anim;
    }

    public static String getProperName(Context context, int mAnim) {
        Resources res = context.getResources();
        String value = "";
        switch (mAnim) {
            case ANIMATION_DEFAULT:
                value = res.getString(com.android.internal.R.string.animation_default);
                break;
            case ANIMATION_FADE:
                value = res.getString(com.android.internal.R.string.animation_fade);
                break;
            case ANIMATION_SLIDE_RIGHT:
                value = res.getString(com.android.internal.R.string.animation_slide_right);
                break;
            case ANIMATION_SLIDE_LEFT:
                value = res.getString(com.android.internal.R.string.animation_slide_left);
                break;
            case ANIMATION_SLIDE_UP:
                value = res.getString(com.android.internal.R.string.animation_slide_up);
                break;
            default:
                value = res.getString(com.android.internal.R.string.action_null);
                break;

        }
        return value;
    }
}
