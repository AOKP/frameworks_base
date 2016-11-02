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

package com.android.test.soundtrigger;

import android.annotation.Nullable;
import android.content.Context;
import android.hardware.soundtrigger.SoundTrigger.GenericSoundModel;
import android.media.soundtrigger.SoundTriggerDetector;
import android.media.soundtrigger.SoundTriggerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ParcelUuid;
import android.util.Log;

import com.android.internal.app.ISoundTriggerService;

import java.lang.RuntimeException;
import java.util.UUID;

/**
 * Utility class for the managing sound trigger sound models.
 */
public class SoundTriggerUtil {
    private static final String TAG = "SoundTriggerTestUtil";

    private final ISoundTriggerService mSoundTriggerService;
    private final SoundTriggerManager mSoundTriggerManager;
    private final Context mContext;

    public SoundTriggerUtil(Context context) {
        mSoundTriggerService = ISoundTriggerService.Stub.asInterface(
                ServiceManager.getService(Context.SOUND_TRIGGER_SERVICE));
        mSoundTriggerManager = (SoundTriggerManager) context.getSystemService(
                Context.SOUND_TRIGGER_SERVICE);
        mContext = context;
    }

    /**
     * Adds/Updates a sound model.
     * The sound model must contain a valid UUID.
     *
     * @param soundModel The sound model to add/update.
     */
    public boolean addOrUpdateSoundModel(GenericSoundModel soundModel) {
        try {
            if (soundModel == null) {
                throw new RuntimeException("Bad sound model");
            }
            mSoundTriggerService.updateSoundModel(soundModel);
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in updateSoundModel", e);
        }
        return true;
    }

    /**
     * Gets the sound model for the given keyphrase, null if none exists.
     * If a sound model for a given keyphrase exists, and it needs to be updated,
     * it should be obtained using this method, updated and then passed in to
     * {@link #addOrUpdateSoundModel(GenericSoundModel)} without changing the IDs.
     *
     * @param modelId The model ID to look-up the sound model for.
     * @return The sound model if one was found, null otherwise.
     */
    @Nullable
    public GenericSoundModel getSoundModel(UUID modelId) {
        GenericSoundModel model = null;
        try {
            model = mSoundTriggerService.getSoundModel(new ParcelUuid(modelId));
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in updateKeyphraseSoundModel");
        }

        if (model == null) {
            Log.w(TAG, "No models present for the given keyphrase ID");
            return null;
        } else {
            return model;
        }
    }

    /**
     * Deletes the sound model for the given keyphrase id.
     *
     * @param modelId The model ID to look-up the sound model for.
     * @return {@code true} if the call succeeds, {@code false} otherwise.
     */
    @Nullable
    public boolean deleteSoundModel(UUID modelId) {
        try {
            mSoundTriggerService.deleteSoundModel(new ParcelUuid(modelId));
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException in deleteSoundModel");
            return false;
        }
        return true;
    }

    public SoundTriggerDetector createSoundTriggerDetector(UUID modelId,
            SoundTriggerDetector.Callback callback) {
        return mSoundTriggerManager.createSoundTriggerDetector(modelId, callback, null);
    }
}
