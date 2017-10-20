/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */


package org.openmrs.mobile.activities.settings;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import org.openmrs.mobile.R;
import org.openmrs.mobile.activities.ACBaseFragment;
import org.openmrs.mobile.bluetooth.BleDefinedUUIDs;
import org.openmrs.mobile.bluetooth.BleWrapper;
import org.openmrs.mobile.bluetooth.BleWrapperUiCallbacks;
import org.openmrs.mobile.bluetooth.PressureMonitorUtils;
import org.openmrs.mobile.models.SettingsListItemDTO;
import org.openmrs.mobile.services.ConceptDownloadService;
import org.openmrs.mobile.utilities.ApplicationConstants;

import java.util.ArrayList;
import java.util.List;

public class SettingsFragment extends ACBaseFragment<SettingsContract.Presenter> implements SettingsContract.View {

    private List<SettingsListItemDTO> mListItem = new ArrayList<>();
    private RecyclerView settingsRecyclerView;

    private BroadcastReceiver bReceiver;

    private TextView conceptsInDbTextView,bluetoothText;
    private ImageButton downloadConceptsButton, bluetoothButton,removeButton;
    public Toast mToast;
    private boolean bReconnect;
    ProgressDialog mPDialog;
    BleWrapper mBleWrapper;
    private final String TAG_CLASS="SettingsFragment.java";
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_settings, container, false);

        bReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mPresenter.updateConceptsInDBTextView();
            }
        };

        settingsRecyclerView = (RecyclerView) root.findViewById(R.id.settingsRecyclerView);
        settingsRecyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        settingsRecyclerView.setLayoutManager(linearLayoutManager);

        conceptsInDbTextView = ((TextView) root.findViewById(R.id.conceptsInDbTextView));
        downloadConceptsButton = ((ImageButton) root.findViewById(R.id.downloadConceptsButton));

        downloadConceptsButton.setOnClickListener(view -> {
            downloadConceptsButton.setEnabled(false);
            Intent startIntent = new Intent(getActivity(), ConceptDownloadService.class);
            startIntent.setAction(ApplicationConstants.ServiceActions.START_CONCEPT_DOWNLOAD_ACTION);
            getActivity().startService(startIntent);
        });

        bluetoothText=(TextView) root.findViewById(R.id.btDeviceTextView);

        bluetoothText.setText(PressureMonitorUtils.getAccountIDText(getContext()));

        removeButton =(ImageButton) root.findViewById(R.id.btRemove);
        removeButton.setOnClickListener(view->{
                PressureMonitorUtils.clearAccountID(getContext());
            bluetoothText.setText(PressureMonitorUtils.getAccountIDText(getContext()));

        });

        bluetoothButton= (ImageButton) root.findViewById(R.id.btPairButton);
        bluetoothButton.setOnClickListener(view->{
            bluetoothButton.setEnabled(false);
            mPDialog.show();
            bReconnect=true;

            mBleWrapper= new BleWrapper(getContext(), new BleWrapperUiCallbacks() {
                @Override
                public void uiDeviceFound(BluetoothDevice device, int rssi, byte[] record) {
                    Log.d(TAG_CLASS,"uiDeviceFound"+ device.getName());
                    if(device.getName().startsWith("1") ) //ModePAIRING
                    {
                        mBleWrapper.stopScanning();
                        Log.d(TAG_CLASS,"uiDeviceFound connecting to"+ device.getName());
                        mBleWrapper.connect(device.getAddress());
                    }
                }

                @Override
                public void uiDeviceConnected(BluetoothGatt gatt, BluetoothDevice device) {
                    Log.d(TAG_CLASS,"uiDeviceConnected"+ device.getName());

                }

                @Override
                public void uiDeviceDisconnected(BluetoothGatt gatt, BluetoothDevice device) {
                    Log.d(TAG_CLASS,"uiDeviceDisconnected"+ device.getName());
                    if (bReconnect)
                        mBleWrapper.connect(device.getAddress());

                }

                @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
                @Override
                public void uiAvailableServices(BluetoothGatt gatt, BluetoothDevice device, List< BluetoothGattService > services) {
                    bReconnect=false;
                    Log.d(TAG_CLASS, "uiAvailableServices name" + device.getName());
                    Log.d(TAG_CLASS, "getService PPRESS");
                    String acountID = device.getName().substring(1, 6);
                    Log.d(TAG_CLASS, "uiAvailableServices account id" + acountID);
                    BluetoothGattService service = gatt.getService(BleDefinedUUIDs.Service.CUSTOM_SERVICE);
                    if (service == null)
                        Log.d(TAG_CLASS, "Could not get CUSTOM_SERVICE Service");
                    else {
                        Log.d(TAG_CLASS, "Heart Rate Service successfully retrieved");
                        mBleWrapper.getCharacteristicsForService(service);
                    }
                }

                @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
                @Override
                public void uiCharacteristicForService(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, List< BluetoothGattCharacteristic > chars) {
                    Log.d(TAG_CLASS, "uiCharacteristicForService");
                    BluetoothGattCharacteristic btch = service
                            .getCharacteristic(BleDefinedUUIDs.Characteristic.READ_RANDOM_CHARACTERISITIC);
                    if (btch == null) {
                        Log.d(TAG_CLASS,
                                "Could not find READ_RANDOM_CHARACTERISITIC Characteristic");
                    } else {
                        Log.d(TAG_CLASS,
                                "READ_RANDOM_CHARACTERISITIC retrieved properly");
                        mBleWrapper.setNotificationForCharacteristic(btch, true);
                    }
                }

                @Override
                public void uiCharacteristicsDetails(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic characteristic) {
                    Log.d(TAG_CLASS, "uiCharacteristicsDetails");

                }

                @Override
                public void uiNewValueForCharacteristic(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic ch, String strValue, int intValue, byte[] rawValue, String timestamp) {

                }

                @Override
                public void uiNewValueHRForCharacteristic(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic ch, String strValue, int intValue, byte[] rawValue, String timestamp) {

                }

                @Override
                public void uiGotNotification(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic characteristic) {

                }

                @Override
                public void uiSuccessfulWrite(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic ch, String description) {
                    Log.d(TAG_CLASS, "uiSuccessfulWrite");
                    if (description.equalsIgnoreCase(BleWrapper.END_DESCRIPTION)) {
                        Log.d(TAG_CLASS, "end pairing");
                        mBleWrapper.close();
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mPDialog.cancel();
                                bluetoothButton.setEnabled(true);
                                bluetoothText.setText(PressureMonitorUtils.getAccountIDText(getContext()));
                                mToast=Toast.makeText(getContext(),"PAIRED!!",Toast.LENGTH_SHORT);
                                mToast.show();

                            }
                        });
                    }

                }

                @Override
                public void uiFailedWrite(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, BluetoothGattCharacteristic ch, String description) {

                }

                @Override
                public void uiNewRssiAvailable(BluetoothGatt gatt, BluetoothDevice device, int rssi) {

                }

                @Override
                public void uiNewValuePressForCharacteristic(BluetoothGatt gatt, BluetoothDevice device, BluetoothGattService service, int systolic, int diastolic, int hr) {
                    Log.d(TAG_CLASS,"uiNewValuePressForCharacteristic");
                    mPDialog.dismiss();
                    Log.d(TAG_CLASS,"ui systolic: "+systolic+" diastolic: "+diastolic);


                }


            });


            if ( mBleWrapper.initialize())
                mBleWrapper.startScanningCustom(true);
            else
                mPDialog.cancel();
        });
        mPDialog= new ProgressDialog(this.getActivity());
        mPDialog.setMessage("Connecting to blood pressure monitor it can take over 6 seconds");
        mPDialog.setIndeterminate(true);
        mPDialog.setCancelable(true);
        mPDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                bReconnect=false;
                mBleWrapper.stopScanning();
                mBleWrapper.diconnect();
                mBleWrapper.close();
                mPDialog.cancel();
                bluetoothButton.setEnabled(true);

            }
        });


        return root;
    }




    @Override
    public void onPause() {
        super.onPause();
        mListItem = new ArrayList<>();
        LocalBroadcastManager.getInstance(this.getActivity()).unregisterReceiver(bReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        mPresenter.updateConceptsInDBTextView();
        LocalBroadcastManager.getInstance(this.getActivity()).registerReceiver(bReceiver, new IntentFilter(ApplicationConstants.BroadcastActions.CONCEPT_DOWNLOAD_BROADCAST_INTENT_ID));
    }

    @Override
    public void setConceptsInDbText(String text) {
        conceptsInDbTextView.setText(text);
    }

    @Override
    public void addLogsInfo(long logSize, String logFilename) {


        mListItem.add(new SettingsListItemDTO(getResources().getString(R.string.settings_logs),
                logFilename,
                "Size: " + logSize + "kB"));
    }

    @Override
    public void addBuildVersionInfo() {
        String versionName = "";
        int buildVersion = 0;

        PackageManager packageManager = this.getActivity().getPackageManager();
        String packageName = this.getActivity().getPackageName();

        try {
            versionName = packageManager.getPackageInfo(packageName, 0).versionName;
            ApplicationInfo ai = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            buildVersion = ai.metaData.getInt("buildVersion");
        } catch (PackageManager.NameNotFoundException e) {
            mPresenter.logException("Failed to load meta-data, NameNotFound: " + e.getMessage());
        } catch (NullPointerException e) {
            mPresenter.logException("Failed to load meta-data, NullPointer: " + e.getMessage());
        }

        mListItem.add(new SettingsListItemDTO(getResources().getString(R.string.settings_about),
                getResources().getString(R.string.app_name),
                versionName + " Build: " + buildVersion));
    }

    @Override
    public void applyChanges() {
        SettingsRecyclerViewAdapter adapter = new SettingsRecyclerViewAdapter(mListItem);
        settingsRecyclerView.setAdapter(adapter);
    }

    public static SettingsFragment newInstance() {
        return new SettingsFragment();
    }

}