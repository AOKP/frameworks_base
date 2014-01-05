/*
 * Copyright (C) 2012 The Android Open Source Project
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
package android.annotation;

import static java.lang.annotation.ElementType.FIELD;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.android.internal.R;

/** Helper annotation for AOKP Settings Backup */
@Target({ FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedSetting {

    /**
     * Human readable short description of the setting
     * Should point to a R.string in frameworks-res
     *
     * default if not supplied "Unknown Setting"
     */
    int title() default R.string.unknown_title;

    /**
     * Name of category Setting is a member of
     *
     * default if not supplied "Uncategorized"
     */
    int category() default R.string.uncategorized_setting;

    /**
     * Indicates if changing the feature's status will
     * require a reboot to take effect
     *
     * default if not supplied false
     */
    boolean requiresReboot() default false;

    /**
     * Indicates the names of dependant settings; for
     * example if a feature has 3 settings then one should
     * make a String[] containing the 3 settings then
     * annoint that setting with isMaster=true. This avoids
     * making multiple dependants annointed with similar
     * nomenclature. 
     *
     * default is false
     */
    boolean isMaster() default false;
}
