/*
 * Copyright (C) 2014 The Android Open Source Project
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

import cyanogenmod.providers.WeatherContract;

import java.text.DecimalFormat;
import java.util.Locale;

public class AOKPUtils {

    public static boolean isNavBarDefault(Context context) {
        return context.getResources().getBoolean(
                com.android.internal.R.bool.config_showNavigationBar);
    }

    public static boolean isChineseLanguage() {
       return Resources.getSystem().getConfiguration().locale.getLanguage().startsWith(
               Locale.CHINESE.getLanguage());
    }

    public static String formatWind(double windSpeed, int windSpeedUnit) {
        if (!isValidWindSpeedUnit(windSpeedUnit)) return null;
        if (Double.isNaN(windSpeed)) return "-";

        DecimalFormat oneDigitFormat = new DecimalFormat("0.0");
        String oneDigitSpeed = oneDigitFormat.format(windSpeed);
        if (oneDigitSpeed.equals("-0.0")) {
            oneDigitSpeed = "0.0";
        }

        StringBuilder formatted = new StringBuilder()
                .append(oneDigitSpeed).append(" ");
        if (windSpeedUnit == WeatherContract.WeatherColumns.WindSpeedUnit.KPH) {
            formatted.append("KPH"); //TODO use getString() 
        } else if (windSpeedUnit == WeatherContract.WeatherColumns.WindSpeedUnit.MPH) {
            formatted.append("MPH"); //TODO use getString() 
        }
        return formatted.toString();
    }

    private static boolean isValidWindSpeedUnit(int unit) {
        switch (unit) {
            case WeatherContract.WeatherColumns.WindSpeedUnit.KPH:
            case WeatherContract.WeatherColumns.WindSpeedUnit.MPH:
                return true;
            default:
                return false;
        }
    }
}
