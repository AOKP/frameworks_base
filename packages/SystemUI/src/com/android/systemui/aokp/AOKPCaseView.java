/*
 * Copyright (C) 2013-2014 The CyanogenMod Project
 * Copyright (C) 2014 The Android Open Kang Project
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

import com.android.systemui.DessertCaseView;
import com.android.systemui.R;

import android.content.Context;
import android.util.AttributeSet;

public class AOKPCaseView extends DessertCaseView {
    private static final int[] PASTRIES = {
            R.drawable.unicorn,             // AOKP
            R.drawable.unicorn,             // added 2 times so it appears more often
            R.drawable.dessert_kitkat,      // used with permission
            R.drawable.dessert_android,     // thx irina
    };

    private static final int[] RARE_PASTRIES = {
            R.drawable.dessert_cupcake,     // 2009
            R.drawable.dessert_donut,       // 2009
            R.drawable.dessert_eclair,      // 2009
            R.drawable.dessert_froyo,       // 2010
            R.drawable.dessert_gingerbread, // 2010
            R.drawable.dessert_honeycomb,   // 2011
            R.drawable.dessert_ics,         // 2011
            R.drawable.dessert_jellybean,   // 2012
    };

    private static final int[] XRARE_PASTRIES = {
            R.drawable.dessert_petitfour,   // the original and still delicious

            R.drawable.dessert_donutburger, // remember kids, this was long before cronuts

            R.drawable.dessert_flan,        //     sholes final approach
                                            //     landing gear punted to flan
                                            //     runway foam glistens
                                            //         -- mcleron

            R.drawable.dessert_keylimepie,  // from an alternative timeline
    };
    private static final int[] XXRARE_PASTRIES = {
            R.drawable.dessert_zombiegingerbread, // thx hackbod
            R.drawable.dessert_dandroid,    // thx morrildl
            R.drawable.dessert_jandycane,   // thx nes
    };

    private static final int NUM_PASTRIES = PASTRIES.length + RARE_PASTRIES.length
            + XRARE_PASTRIES.length + XXRARE_PASTRIES.length;

    public AOKPCaseView(Context context) {
        super(context, null);
    }

    public AOKPCaseView(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
    }

    public AOKPCaseView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected int[] getPastries() {
        return PASTRIES;
    }

    @Override
    protected int[] getRarePastries() {
        return RARE_PASTRIES;
    };

    @Override
    protected int[] getXRarePastries() {
        return XRARE_PASTRIES;
    }

    @Override
    protected int[] getXXRarePastries() {
        return XXRARE_PASTRIES;
    }

    @Override
    protected int getNumPastries() {
        return NUM_PASTRIES;
    }
}
