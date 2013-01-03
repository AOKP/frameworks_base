/*
 * Copyright (c) 2012, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in the
 *          documentation and/or other materials provided with the distribution.
 *        * Neither the name of Code Aurora nor
 *          the names of its contributors may be used to endorse or promote
 *          products derived from this software without specific prior written
 *          permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package android.bluetooth;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The Bluetooth GATT Server Application Configuration
 * @hide
 */
public final class BluetoothGattAppConfiguration implements Parcelable {
    private final String mName;
    private final int mRole;
    private final String mPath;
    private final int mRange;

    /**
     * Constructor for GATT server application configuration
     */
    BluetoothGattAppConfiguration(String name, int range) {
        mName = name;
        mRange = range;
        mRole = BluetoothGatt.SERVER_ROLE;
        mPath = "/android/bluetooth/gatt/" + name;
    }

    /**
     * Generic constructor for GATT application configuration
     */
    BluetoothGattAppConfiguration(String name, int role, int range) {
        mName = name;
        mRole = role;
        mRange = range;
        if (role == BluetoothGatt.SERVER_ROLE)
            mPath = "/android/bluetooth/gatt/" + name;
        else
            mPath = null;
    }


    @Override
    public boolean equals(Object o) {
        if (o instanceof BluetoothGattAppConfiguration) {
            BluetoothGattAppConfiguration config = (BluetoothGattAppConfiguration) o;
            // config.getName() can never be NULL
            return mName.equals(config.getName()) &&
                                mRole == config.getRole() &&
                                mRange == config.getRange();
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = BluetoothProfile.GATT;
        result = result + (mName != null ? mName.hashCode() : 0);
        result = 31 * result + mRole;
        return result;
    }

    @Override
    public String toString() {
        return "BluetoothGattAppConfiguration [mName = " + mName + "]";
    }

    public String getName() {
        return mName;
    }

    public int getRole() {
        return mRole;
    }

    public String getPath() {
        return mPath;
    }

    public int getRange() {
        return mRange;
    }

    public static final Parcelable.Creator<BluetoothGattAppConfiguration> CREATOR =
        new Parcelable.Creator<BluetoothGattAppConfiguration>() {
        @Override
        public BluetoothGattAppConfiguration createFromParcel(Parcel in) {
            String name = in.readString();
            int role = in.readInt();
            String path = in.readString();
            int range = in.readInt();
            return new BluetoothGattAppConfiguration(name, role, range);
        }

        @Override
        public BluetoothGattAppConfiguration[] newArray(int size) {
            return new BluetoothGattAppConfiguration[size];
        }
    };

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mName);
        out.writeInt(mRole);
        out.writeString(mPath);
        out.writeInt(mRange);
    }

    public int describeContents() {
        return 0;
    }

}
