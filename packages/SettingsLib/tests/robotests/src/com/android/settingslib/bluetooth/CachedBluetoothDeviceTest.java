/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.settingslib.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioManager;

import com.android.settingslib.SettingsLibRobolectricTestRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAudioManager;

@RunWith(SettingsLibRobolectricTestRunner.class)
public class CachedBluetoothDeviceTest {
    private final static String DEVICE_NAME = "TestName";
    private final static String DEVICE_ALIAS = "TestAlias";
    private final static String DEVICE_ADDRESS = "AA:BB:CC:DD:EE:FF";
    private final static String DEVICE_ALIAS_NEW = "TestAliasNew";
    @Mock
    private LocalBluetoothAdapter mAdapter;
    @Mock
    private LocalBluetoothProfileManager mProfileManager;
    @Mock
    private HeadsetProfile mHfpProfile;
    @Mock
    private A2dpProfile mA2dpProfile;
    @Mock
    private PanProfile mPanProfile;
    @Mock
    private HearingAidProfile mHearingAidProfile;
    @Mock
    private BluetoothDevice mDevice;
    private CachedBluetoothDevice mCachedDevice;
    private ShadowAudioManager mShadowAudioManager;
    private Context mContext;
    private int mBatteryLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mShadowAudioManager = shadowOf(mContext.getSystemService(AudioManager.class));
        when(mDevice.getAddress()).thenReturn(DEVICE_ADDRESS);
        when(mAdapter.getBluetoothState()).thenReturn(BluetoothAdapter.STATE_ON);
        when(mHfpProfile.isProfileReady()).thenReturn(true);
        when(mA2dpProfile.isProfileReady()).thenReturn(true);
        when(mPanProfile.isProfileReady()).thenReturn(true);
        when(mHearingAidProfile.isProfileReady()).thenReturn(true);
        mCachedDevice = spy(
                new CachedBluetoothDevice(mContext, mAdapter, mProfileManager, mDevice));
        doAnswer((invocation) -> mBatteryLevel).when(mCachedDevice).getBatteryLevel();
    }

    @Test
    public void testGetConnectionSummary_testSingleProfileConnectDisconnect() {
        // Test without battery level
        // Set PAN profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();

        // Set PAN profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();

        // Test with battery level
        mBatteryLevel = 10;
        // Set PAN profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo("10% battery");

        // Set PAN profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();

        // Test with BluetoothDevice.BATTERY_LEVEL_UNKNOWN battery level
        mBatteryLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;

        // Set PAN profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();

        // Set PAN profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();
    }

    @Test
    public void testGetConnectionSummary_testMultipleProfileConnectDisconnect() {
        mBatteryLevel = 10;

        // Set HFP, A2DP and PAN profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo("10% battery");

        // Disconnect HFP only and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo(
                "10% battery");

        // Disconnect A2DP only and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo(
                "10% battery");

        // Disconnect both HFP and A2DP and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo(
                "10% battery");

        // Disconnect all profiles and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();
    }

    @Test
    public void testGetConnectionSummary_testSingleProfileActiveDeviceA2dp() {
        // Test without battery level
        // Set A2DP profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();

        // Set device as Active for A2DP and test connection state summary
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.A2DP);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo("Active");

        // Test with battery level
        mBatteryLevel = 10;
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo(
                "Active, 10% battery");

        // Set A2DP profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();

        // Test with BluetoothDevice.BATTERY_LEVEL_UNKNOWN battery level
        mBatteryLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        // Set A2DP profile to be connected, Active and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.A2DP);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo("Active");

        // Set A2DP profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();
    }

    @Test
    public void testGetConnectionSummary_testSingleProfileActiveDeviceHfp() {
        // Test without battery level
        // Set HFP profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();

        // Set device as Active for HFP and test connection state summary
        mCachedDevice.onAudioModeChanged();
        mShadowAudioManager.setMode(AudioManager.MODE_IN_CALL);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.HEADSET);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo("Active");

        // Test with battery level
        mBatteryLevel = 10;
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo(
                "Active, 10% battery");

        // Set HFP profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();

        // Test with BluetoothDevice.BATTERY_LEVEL_UNKNOWN battery level
        mBatteryLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        // Set HFP profile to be connected, Active and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.HEADSET);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo("Active");

        // Set HFP profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();
    }

    @Test
    public void testGetConnectionSummary_testSingleProfileActiveDeviceHearingAid() {
        // Test without battery level
        // Set Hearing Aid profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mHearingAidProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();

        // Set device as Active for Hearing Aid and test connection state summary
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.HEARING_AID);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo("Active");

        // Set Hearing Aid profile to be disconnected and test connection state summary
        mCachedDevice.onActiveDeviceChanged(false, BluetoothProfile.HEARING_AID);
        mCachedDevice.
                onProfileStateChanged(mHearingAidProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();
    }

    @Test
    public void testGetConnectionSummary_testMultipleProfilesActiveDevice() {
        // Test without battery level
        // Set A2DP and HFP profiles to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();

        // Set device as Active for A2DP and HFP and test connection state summary
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.A2DP);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.HEADSET);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo("Active");

        // Test with battery level
        mBatteryLevel = 10;
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo(
                "Active, 10% battery");

        // Disconnect A2DP only and test connection state summary
        mCachedDevice.onActiveDeviceChanged(false, BluetoothProfile.A2DP);
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo(
                "10% battery");

        // Disconnect HFP only and test connection state summary
        mCachedDevice.onActiveDeviceChanged(false, BluetoothProfile.HEADSET);
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.A2DP);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo(
                "Active, 10% battery");

        // Test with BluetoothDevice.BATTERY_LEVEL_UNKNOWN battery level
        mBatteryLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        // Set A2DP and HFP profiles to be connected, Active and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.A2DP);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.HEADSET);
        assertThat(mCachedDevice.getConnectionSummary()).isEqualTo("Active");

        // Set A2DP and HFP profiles to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getConnectionSummary()).isNull();
    }

    @Test
    public void getCarConnectionSummary_singleProfileConnectDisconnect() {
        // Test without battery level
        // Set PAN profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected");

        // Set PAN profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isNull();

        // Test with battery level
        mBatteryLevel = 10;
        // Set PAN profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected, battery 10%");

        // Set PAN profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isNull();

        // Test with BluetoothDevice.BATTERY_LEVEL_UNKNOWN battery level
        mBatteryLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;

        // Set PAN profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected");

        // Set PAN profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isNull();
    }

    @Test
    public void getCarConnectionSummary_multipleProfileConnectDisconnect() {
        mBatteryLevel = 10;

        // Set HFP, A2DP and PAN profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected, battery 10%");

        // Disconnect HFP only and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo(
                "Connected (no phone), battery 10%");

        // Disconnect A2DP only and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo(
                "Connected (no media), battery 10%");

        // Disconnect both HFP and A2DP and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo(
                "Connected (no phone or media), battery 10%");

        // Disconnect all profiles and test connection state summary
        mCachedDevice.onProfileStateChanged(mPanProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isNull();
    }

    @Test
    public void getCarConnectionSummary_singleProfileActiveDeviceA2dp() {
        // Test without battery level
        // Set A2DP profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected");

        // Set device as Active for A2DP and test connection state summary
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.A2DP);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected, active (media)");

        // Test with battery level
        mBatteryLevel = 10;
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo(
                "Connected, battery 10%, active (media)");

        // Set A2DP profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isNull();

        // Test with BluetoothDevice.BATTERY_LEVEL_UNKNOWN battery level
        mBatteryLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        // Set A2DP profile to be connected, Active and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.A2DP);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected, active (media)");

        // Set A2DP profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isNull();
    }

    @Test
    public void getCarConnectionSummary_singleProfileActiveDeviceHfp() {
        // Test without battery level
        // Set HFP profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected");

        // Set device as Active for HFP and test connection state summary
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.HEADSET);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected, active (phone)");

        // Test with battery level
        mBatteryLevel = 10;
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo(
                "Connected, battery 10%, active (phone)");

        // Set HFP profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isNull();

        // Test with BluetoothDevice.BATTERY_LEVEL_UNKNOWN battery level
        mBatteryLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        // Set HFP profile to be connected, Active and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.HEADSET);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected, active (phone)");

        // Set HFP profile to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isNull();
    }

    @Test
    public void getCarConnectionSummary_singleProfileActiveDeviceHearingAid() {
        // Test without battery level
        // Set Hearing Aid profile to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mHearingAidProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected");

        // Set device as Active for Hearing Aid and test connection state summary
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.HEARING_AID);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected, active");

        // Set Hearing Aid profile to be disconnected and test connection state summary
        mCachedDevice.onActiveDeviceChanged(false, BluetoothProfile.HEARING_AID);
        mCachedDevice.onProfileStateChanged(mHearingAidProfile,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isNull();
    }

    @Test
    public void getCarConnectionSummary_multipleProfilesActiveDevice() {
        // Test without battery level
        // Set A2DP and HFP profiles to be connected and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected");

        // Set device as Active for A2DP and HFP and test connection state summary
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.A2DP);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.HEADSET);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected, active");

        // Test with battery level
        mBatteryLevel = 10;
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo(
                "Connected, battery 10%, active");

        // Disconnect A2DP only and test connection state summary
        mCachedDevice.onActiveDeviceChanged(false, BluetoothProfile.A2DP);
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo(
                "Connected (no media), battery 10%, active (phone)");

        // Disconnect HFP only and test connection state summary
        mCachedDevice.onActiveDeviceChanged(false, BluetoothProfile.HEADSET);
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.A2DP);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo(
                "Connected (no phone), battery 10%, active (media)");

        // Test with BluetoothDevice.BATTERY_LEVEL_UNKNOWN battery level
        mBatteryLevel = BluetoothDevice.BATTERY_LEVEL_UNKNOWN;
        // Set A2DP and HFP profiles to be connected, Active and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.A2DP);
        mCachedDevice.onActiveDeviceChanged(true, BluetoothProfile.HEADSET);
        assertThat(mCachedDevice.getCarConnectionSummary()).isEqualTo("Connected, active");

        // Set A2DP and HFP profiles to be disconnected and test connection state summary
        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.getCarConnectionSummary()).isNull();
    }


    @Test
    public void testDeviceName_testAliasNameAvailable() {
        when(mDevice.getAliasName()).thenReturn(DEVICE_ALIAS);
        when(mDevice.getName()).thenReturn(DEVICE_NAME);
        CachedBluetoothDevice cachedBluetoothDevice =
                new CachedBluetoothDevice(mContext, mAdapter, mProfileManager, mDevice);
        // Verify alias is returned on getName
        assertThat(cachedBluetoothDevice.getName()).isEqualTo(DEVICE_ALIAS);
        // Verify device is visible
        assertThat(cachedBluetoothDevice.hasHumanReadableName()).isTrue();
    }

    @Test
    public void testDeviceName_testNameNotAvailable() {
        CachedBluetoothDevice cachedBluetoothDevice =
                new CachedBluetoothDevice(mContext, mAdapter, mProfileManager, mDevice);
        // Verify device address is returned on getName
        assertThat(cachedBluetoothDevice.getName()).isEqualTo(DEVICE_ADDRESS);
        // Verify device is not visible
        assertThat(cachedBluetoothDevice.hasHumanReadableName()).isFalse();
    }

    @Test
    public void testDeviceName_testRenameDevice() {
        final String[] alias = {DEVICE_ALIAS};
        doAnswer(invocation -> alias[0]).when(mDevice).getAliasName();
        doAnswer(invocation -> {
            alias[0] = (String) invocation.getArguments()[0];
            return true;
        }).when(mDevice).setAlias(anyString());
        when(mDevice.getName()).thenReturn(DEVICE_NAME);
        CachedBluetoothDevice cachedBluetoothDevice =
                new CachedBluetoothDevice(mContext, mAdapter, mProfileManager, mDevice);
        // Verify alias is returned on getName
        assertThat(cachedBluetoothDevice.getName()).isEqualTo(DEVICE_ALIAS);
        // Verify null name does not get set
        cachedBluetoothDevice.setName(null);
        verify(mDevice, never()).setAlias(any());
        // Verify new name is set properly
        cachedBluetoothDevice.setName(DEVICE_ALIAS_NEW);
        verify(mDevice).setAlias(DEVICE_ALIAS_NEW);
        // Verify new alias is returned on getName
        assertThat(cachedBluetoothDevice.getName()).isEqualTo(DEVICE_ALIAS_NEW);
    }

    @Test
    public void testSetActive() {
        when(mProfileManager.getA2dpProfile()).thenReturn(mA2dpProfile);
        when(mProfileManager.getHeadsetProfile()).thenReturn(mHfpProfile);
        when(mA2dpProfile.setActiveDevice(any(BluetoothDevice.class))).thenReturn(true);
        when(mHfpProfile.setActiveDevice(any(BluetoothDevice.class))).thenReturn(true);

        assertThat(mCachedDevice.setActive()).isFalse();

        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.setActive()).isTrue();

        mCachedDevice.onProfileStateChanged(mA2dpProfile, BluetoothProfile.STATE_DISCONNECTED);
        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_CONNECTED);
        assertThat(mCachedDevice.setActive()).isTrue();

        mCachedDevice.onProfileStateChanged(mHfpProfile, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mCachedDevice.setActive()).isFalse();
    }

    @Test
    public void testIsA2dpDevice_isA2dpDevice() {
        when(mProfileManager.getA2dpProfile()).thenReturn(mA2dpProfile);
        when(mA2dpProfile.getConnectionStatus(mDevice)).
                thenReturn(BluetoothProfile.STATE_CONNECTED);

        assertThat(mCachedDevice.isA2dpDevice()).isTrue();
    }

    @Test
    public void testIsA2dpDevice_isNotA2dpDevice() {
        when(mProfileManager.getA2dpProfile()).thenReturn(mA2dpProfile);
        when(mA2dpProfile.getConnectionStatus(mDevice)).
                thenReturn(BluetoothProfile.STATE_DISCONNECTING);

        assertThat(mCachedDevice.isA2dpDevice()).isFalse();
    }

    @Test
    public void testIsHfpDevice_isHfpDevice() {
        when(mProfileManager.getHeadsetProfile()).thenReturn(mHfpProfile);
        when(mHfpProfile.getConnectionStatus(mDevice)).
                thenReturn(BluetoothProfile.STATE_CONNECTED);

        assertThat(mCachedDevice.isHfpDevice()).isTrue();
    }

    @Test
    public void testIsHfpDevice_isNotHfpDevice() {
        when(mProfileManager.getHeadsetProfile()).thenReturn(mHfpProfile);
        when(mHfpProfile.getConnectionStatus(mDevice)).
                thenReturn(BluetoothProfile.STATE_DISCONNECTING);

        assertThat(mCachedDevice.isHfpDevice()).isFalse();
    }

    @Test
    public void isConnectedHearingAidDevice_connected_returnTrue() {
        when(mProfileManager.getHearingAidProfile()).thenReturn(mHearingAidProfile);
        when(mHearingAidProfile.getConnectionStatus(mDevice)).
                thenReturn(BluetoothProfile.STATE_CONNECTED);

        assertThat(mCachedDevice.isConnectedHearingAidDevice()).isTrue();
    }

    @Test
    public void isConnectedHearingAidDevice_disconnected_returnFalse() {
        when(mProfileManager.getHearingAidProfile()).thenReturn(mHearingAidProfile);
        when(mHearingAidProfile.getConnectionStatus(mDevice)).
                thenReturn(BluetoothProfile.STATE_DISCONNECTED);

        assertThat(mCachedDevice.isConnectedHearingAidDevice()).isFalse();
    }

    @Test
    public void isConnectedHfpDevice_profileIsNull_returnFalse() {
        when(mProfileManager.getHeadsetProfile()).thenReturn(null);

        assertThat(mCachedDevice.isHfpDevice()).isFalse();
    }

    @Test
    public void isConnectedA2dpDevice_profileIsNull_returnFalse() {
        when(mProfileManager.getA2dpProfile()).thenReturn(null);

        assertThat(mCachedDevice.isA2dpDevice()).isFalse();
    }

    @Test
    public void isConnectedHearingAidDevice_profileIsNull_returnFalse() {
        when(mProfileManager.getHearingAidProfile()).thenReturn(null);

        assertThat(mCachedDevice.isConnectedHearingAidDevice()).isFalse();
    }

    @Test
    public void testIsHearingAidDevice_isHearingAidDevice() {
        mCachedDevice.setHiSyncId(0x1234);
        assertThat(mCachedDevice.isHearingAidDevice()).isTrue();
    }

    @Test
    public void testIsHearingAidDevice_isNotHearingAidDevice() {
        mCachedDevice.setHiSyncId(BluetoothHearingAid.HI_SYNC_ID_INVALID);
        assertThat(mCachedDevice.isHearingAidDevice()).isFalse();
    }
}
