package org.openmrs.mobile.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BleWrapper {

	private static final int DELAY_BT_CONNECT = 120 * 1000;

	/* defines (in milliseconds) how often RSSI should be updated */
	private static final int RSSI_UPDATE_TIME_INTERVAL = 1500; // 1.5 seconds
    public static final  String END_DESCRIPTION = "END";

    /* callback object through which we are returning results to the caller */
	private BleWrapperUiCallbacks mUiCallback = null;

	/* define NULL object for UI callbacks */
	private static final BleWrapperUiCallbacks NULL_CALLBACK = new BleWrapperUiCallbacks.Null();

	/* defines which value we will pick */
	/* fitlab usa los RR */
	/* geocardio usa los HR */
	private static boolean mode_HR = false;
	private static boolean mode_RR = true;

	/* creates BleWrapper object, set its parent activity and callback object */
	public BleWrapper(Context parent, BleWrapperUiCallbacks callback) {
		this.mParent = parent;
		mUiCallback = callback;
		if (mUiCallback == null)
			mUiCallback = NULL_CALLBACK;

	}

	public BluetoothManager getManager() {
		return mBluetoothManager;
	}

	public BluetoothAdapter getAdapter() {
		return mBluetoothAdapter;
	}

	public BluetoothDevice getDevice() {
		return mBluetoothDevice;
	}

	public BluetoothGatt getGatt() {
		return mBluetoothGatt;
	}

	public BluetoothGattService getCachedService() {
		return mBluetoothSelectedService;
	}

	public List<BluetoothGattService> getCachedServices() {
		return mBluetoothGattServices;
	}

	public boolean isConnected() {

		return mConnected;
	}

	/* run test and check if this device has BT and BLE hardware available */
	public boolean checkBleHardwareAvailable() {
		// First check general Bluetooth Hardware:
		// get BluetoothManager...
		final BluetoothManager manager = (BluetoothManager) mParent.getSystemService(Context.BLUETOOTH_SERVICE);
		if (manager == null)
			return false;
		// .. and then get adapter from manager
		final BluetoothAdapter adapter = manager.getAdapter();
		if (adapter == null)
			return false;

		// and then check if BT LE is also available
		boolean hasBle = mParent.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
		return hasBle;
	}

	/*
	 * before any action check if BT is turned ON and enabled for us call this
	 * in onResume to be always sure that BT is ON when Your application is put
	 * into the foreground
	 */
	public boolean isBtEnabled() {
		final BluetoothManager manager = (BluetoothManager) mParent.getSystemService(Context.BLUETOOTH_SERVICE);
		if (manager == null)
			return false;

		final BluetoothAdapter adapter = manager.getAdapter();
		if (adapter == null)
			return false;

		return adapter.isEnabled();
	}

	/* start scanning for BT LE devices around */
	public void startScanning(boolean onlyHeart) {
        if (onlyHeart) {
            final UUID[] uuids = new UUID[] { BleDefinedUUIDs.Service.HEART_RATE };
            mBluetoothAdapter.startLeScan(uuids, mDeviceFoundCallback);
        } else {
            // final UUID[] uuids = new UUID[] {
            // BleDefinedUUIDs.Service.HEART_RATE };
            // mBluetoothAdapter.startLeScan(uuids, mDeviceFoundCallback);
            mBluetoothAdapter.startLeScan(mDeviceFoundCallback);
        }
    }
	public void startScanningCustom(boolean onlyPress) {
		if (onlyPress) {
			final UUID[] uuids = new UUID[]{BleDefinedUUIDs.Service.CUSTOM_SERVICE};
			if (Build.VERSION.SDK_INT < 25) {
				mBluetoothAdapter.startLeScan(uuids, mDeviceFoundCallback);
			} else {
				Log.d(TAG,"startScanning New Api");
				ScanFilter scanfilter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(BleDefinedUUIDs.Service.CUSTOM_SERVICE)).build();
				ScanSettings settings = new ScanSettings.Builder()
						.setScanMode(ScanSettings.SCAN_MODE_BALANCED)
						.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
						.setReportDelay(0)//1000
						.build();
				mBluetoothAdapter.getBluetoothLeScanner().startScan(Arrays.asList(scanfilter), settings, mScanCallback);
			}

		} else {
			if (Build.VERSION.SDK_INT < 25) {
				mBluetoothAdapter.startLeScan(mDeviceFoundCallback);
			} else {
				mBluetoothAdapter.getBluetoothLeScanner().startScan(mScanCallback);

			}
		}
	}

	/* stops current scanning */
	public void stopScanning() {

		if (Build.VERSION.SDK_INT < 25) {
			mBluetoothAdapter.stopLeScan(mDeviceFoundCallback);
		}
		else{
			if (mBluetoothAdapter.getBluetoothLeScanner()!=null)
				mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
		}

	}

	/* initialize BLE and get BT Manager & Adapter */
	public boolean initialize() {
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) mParent.getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				return false;
			}
		}

		if (mBluetoothAdapter == null)
			mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			return false;
		}
		if (!mBluetoothAdapter.isEnabled()){
			return mBluetoothAdapter.enable();
		}
		return true;
	}

	private TimerTask myTimerTask = new TimerTask() {

		@Override
		public void run() {

			if (!mConnected) {
				Log.d(TAG, "myTimerTask disconnecting");
				diconnect();
				mUiCallback.uiDeviceDisconnected(mBluetoothGatt, mBluetoothDevice);
			} else {
				Log.d(TAG, "myTimerTask connected");
			}

		}
	};
	private Timer timerBTConnect = new Timer();

	/* connect to the device with specified address */
	public boolean connect(final String deviceAddress) {
		if (mBluetoothAdapter == null || deviceAddress == null)
			return false;
		mDeviceAddress = deviceAddress;

		// check if we need to connect from scratch or just reconnect to
		// // previous device
		// if (mBluetoothGatt != null
		// && mBluetoothGatt.getDevice().getAddress()
		// .equals(deviceAddress)) {
		// // just reconnect
		// return mBluetoothGatt.connect();
		// } else {
		// connect from scratch
		// get BluetoothDevice object for specified address
		mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
		if (mBluetoothDevice == null) {
			// we got wrong address - that device is not available!
			return false;
		}
		// connect with remote device
        Log.d(TAG,"connect()"+" gatt()");
		mBluetoothGatt = mBluetoothDevice.connectGatt(mParent, false, mBleCallback);
		timerBTConnect.schedule(myTimerTask, DELAY_BT_CONNECT);

		// }
		return true;
	}

	public static boolean tryConnectionGatt(BluetoothDevice device, Context ctx) {
		BluetoothGatt auxGatt = device.connectGatt(ctx, false, new BluetoothGattCallback() {
		});
		boolean connected = false;
		if (auxGatt == null)
			return false;
		boolean connectedaux = auxGatt.connect();
		connected = auxGatt.readRemoteRssi();
		BluetoothManager btMgr = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
		boolean connected2 = (btMgr.getConnectionState(device,
				BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED);
		Log.d(TAG, "@:" + device.getAddress() + " connected_1->" + connected + " connected_2->" + connected2
				+ " conenected_3->" + connectedaux);
		auxGatt.disconnect();
		return connected;
	}

	/*
	 * disconnect the device. It is still possible to reconnect to it later with
	 * this Gatt client
	 */
	public void diconnect() {
		if (mBluetoothGatt != null)
			mBluetoothGatt.disconnect();
		// mUiCallback.uiDeviceDisconnected(mBluetoothGatt, mBluetoothDevice);
	}

	/* close GATT client completely */
	public void close() {
		if (mBluetoothGatt != null)
			mBluetoothGatt.close();
		mBluetoothGatt = null;
	}

	/* request new RSSi value for the connection */
	public void readPeriodicalyRssiValue(final boolean repeat) {
		mTimerEnabled = repeat;
		// check if we should stop checking RSSI value
		if (mConnected == false || mBluetoothGatt == null || mTimerEnabled == false) {
			mTimerEnabled = false;
			return;
		}

		mTimerHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (mBluetoothGatt == null || mBluetoothAdapter == null || mConnected == false) {
					mTimerEnabled = false;
					return;
				}

				// request RSSI value
				mBluetoothGatt.readRemoteRssi();
				// add call it once more in the future
				readPeriodicalyRssiValue(mTimerEnabled);
			}
		}, RSSI_UPDATE_TIME_INTERVAL);
	}

	/* starts monitoring RSSI value */
	public void startMonitoringRssiValue() {
		readPeriodicalyRssiValue(true);
	}

	/* stops monitoring of RSSI value */
	public void stopMonitoringRssiValue() {
		readPeriodicalyRssiValue(false);
	}

	/*
	 * request to discover all services available on the remote devices results
	 * are delivered through callback object
	 */
	public void startServicesDiscovery() {
		if (mBluetoothGatt != null)
			mBluetoothGatt.discoverServices();
	}

	/*
	 * gets services and calls UI callback to handle them before calling
	 * getServices() make sure service discovery is finished!
	 */
	public void getSupportedServices() {
		if (mBluetoothGattServices != null && mBluetoothGattServices.size() > 0)
			mBluetoothGattServices.clear();
		// keep reference to all services in local array:
		if (mBluetoothGatt != null)
			mBluetoothGattServices = mBluetoothGatt.getServices();

		mUiCallback.uiAvailableServices(mBluetoothGatt, mBluetoothDevice, mBluetoothGattServices);
	}

	/*
	 * get all characteristic for particular service and pass them to the UI
	 * callback
	 */
	public void getCharacteristicsForService(final BluetoothGattService service) {
		if (service == null)
			return;
		List<BluetoothGattCharacteristic> chars = null;

		chars = service.getCharacteristics();
		mUiCallback.uiCharacteristicForService(mBluetoothGatt, mBluetoothDevice, service, chars);
		// keep reference to the last selected service
		mBluetoothSelectedService = service;
	}

	/*
	 * request to fetch newest value stored on the remote device for particular
	 * characteristic
	 */
	public void requestCharacteristicValue(BluetoothGattCharacteristic ch) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null)
			return;

		mBluetoothGatt.readCharacteristic(ch);
		// new value available will be notified in Callback Object
	}

	/*
	 * get characteristic's value (and parse it for some types of
	 * characteristics) before calling this You should always update the value
	 * by calling requestCharacteristicValue()
	 */
    byte [] password =new byte[4];
    byte [] accounttID={(byte) 0x4b,(byte)0x09,(byte)0xdf, (byte)0x78};
    byte cmdAccount=(byte) 0x21;
    byte []randomNumber=new byte[4];
    byte []vercode=new byte[4];
    byte cmdVer=(byte) 0x20;
    byte cmdTimeOffset=(byte) 0x02;
    byte cmdWrite=0x00;
    public void getCharacteristicValue(BluetoothGattCharacteristic ch, int mode) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null || ch == null)
			return;
		byte[] rawValue = ch.getValue();
		String strValue = null;
		int intValue = 0;
		// lets read and do real parsing of some characteristic to get
		// meaningful value from it
		UUID uuid = ch.getUuid();
		if (uuid.equals(BleDefinedUUIDs.Characteristic.READ_RANDOM_CHARACTERISITIC)){
            String cmd= String.format("%x",rawValue[0]);
            Log.d(TAG,"PRESS pairing command:"+cmd);
            if (cmd.equals("a0")){
                Log.d(TAG,"Getting Password");
                password[0]=rawValue[1];
                password[1]=rawValue[2];
                password[2]=rawValue[3];
                password[3]=rawValue[4];

                final StringBuilder stringBuilder = new StringBuilder(rawValue.length);
                for (byte byteChar : rawValue) {
                    stringBuilder.append(String.format("%x", byteChar));
                }
                strValue = stringBuilder.toString();
                Log.i(TAG, "pwd: "+strValue);
                PressureMonitorUtils.savePassword(mParent,password);

                BluetoothGattCharacteristic   btchW = mBluetoothSelectedService.getCharacteristic
                        (BleDefinedUUIDs.Characteristic.MONITOR_WRITE_CHARACTERISTIC);
                if (btchW!=null){
                    Log.d(TAG,"PRESS write accountID");
                    cmdWrite=cmdAccount;
                    writeDataToCharacteristic(btchW,new byte[]{cmdAccount,accounttID[0],accounttID[1],accounttID[2],accounttID[3]});

                }
            }
            else if (cmd.equals("a1")) {
                Log.d(TAG, "Getting randomNumber");
                randomNumber[0]=rawValue[1];
                randomNumber[1]=rawValue[2];
                randomNumber[2]=rawValue[3];
                randomNumber[3]=rawValue[4];

                if (mode==0) {
                    password = PressureMonitorUtils.getPassword(mParent);
                    Log.d(TAG, "Get password mode normal");
                }
                vercode[0] = (byte) (randomNumber[0] ^ password[0]);
                vercode[1] = (byte) (randomNumber[1] ^ password[1]);
                vercode[2] = (byte) (randomNumber[2] ^ password[2]);
                vercode[3] = (byte) (randomNumber[3] ^ password[3]);
                BluetoothGattCharacteristic   btchW = mBluetoothSelectedService.getCharacteristic(BleDefinedUUIDs.Characteristic.MONITOR_WRITE_CHARACTERISTIC);
                if (btchW!=null){
                    Log.d(TAG,"Writing verification code");
                    cmdWrite=cmdVer;
                    writeDataToCharacteristic(btchW,new byte[]{cmdVer,vercode[0],vercode[1],vercode[2],vercode[3]});
                }
           }
        }
        else if (uuid.equals(BleDefinedUUIDs.Characteristic.READ_PRESSURE_CHARACTERISITIC)){
            Log.d(TAG,"PRESSURE NEW DATA");
            int systolic=ch.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16,1);
            int diastolic=ch.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16,3);
            int meanArterial=ch.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16,5);
            int hr=ch.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16,11);
            Log.d(TAG,"Systolic: "+systolic+ " Diastolic: "+diastolic+ " MeanBPM: " + hr);
            mUiCallback.uiNewValuePressForCharacteristic(mBluetoothGatt, mBluetoothDevice,
                    mBluetoothSelectedService,systolic,diastolic,hr);
        }
		else{
            Log.d(TAG,"PRESS unknown charact");

            // not known type of characteristic, so we need to handle this in
			// "general" way
			// get first four bytes and transform it to integer
			intValue = 0;
			if (rawValue.length > 0)
				intValue = (int) rawValue[0];
			if (rawValue.length > 1)
				intValue = intValue + ((int) rawValue[1] << 8);
			if (rawValue.length > 2)
				intValue = intValue + ((int) rawValue[2] << 8);
			if (rawValue.length > 3)
				intValue = intValue + ((int) rawValue[3] << 8);

			if (rawValue.length > 0) {
				final StringBuilder stringBuilder = new StringBuilder(rawValue.length);
				for (byte byteChar : rawValue) {
					stringBuilder.append(String.format("%c", byteChar));
				}
				strValue = stringBuilder.toString();
			}
		}


	}


	/*
	 * reads and return what what FORMAT is indicated by characteristic's
	 * properties seems that value makes no sense in most cases
	 */
	public int getValueFormat(BluetoothGattCharacteristic ch) {
		int properties = ch.getProperties();

		if ((BluetoothGattCharacteristic.FORMAT_FLOAT & properties) != 0)
			return BluetoothGattCharacteristic.FORMAT_FLOAT;
		if ((BluetoothGattCharacteristic.FORMAT_SFLOAT & properties) != 0)
			return BluetoothGattCharacteristic.FORMAT_SFLOAT;
		if ((BluetoothGattCharacteristic.FORMAT_SINT16 & properties) != 0)
			return BluetoothGattCharacteristic.FORMAT_SINT16;
		if ((BluetoothGattCharacteristic.FORMAT_SINT32 & properties) != 0)
			return BluetoothGattCharacteristic.FORMAT_SINT32;
		if ((BluetoothGattCharacteristic.FORMAT_SINT8 & properties) != 0)
			return BluetoothGattCharacteristic.FORMAT_SINT8;
		if ((BluetoothGattCharacteristic.FORMAT_UINT16 & properties) != 0)
			return BluetoothGattCharacteristic.FORMAT_UINT16;
		if ((BluetoothGattCharacteristic.FORMAT_UINT32 & properties) != 0)
			return BluetoothGattCharacteristic.FORMAT_UINT32;
		if ((BluetoothGattCharacteristic.FORMAT_UINT8 & properties) != 0)
			return BluetoothGattCharacteristic.FORMAT_UINT8;

		return 0;
	}

	/* set new value for particular characteristic */
	public void writeDataToCharacteristic(final BluetoothGattCharacteristic ch, final byte[] dataToWrite) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null || ch == null)
			return;

		// first set it locally....
		ch.setValue(dataToWrite);
		// ... and then "commit" changes to the peripheral
		mBluetoothGatt.writeCharacteristic(ch);
	}

	/* enables/disables notification for characteristic */
	public void setNotificationForCharacteristic(BluetoothGattCharacteristic ch, boolean enabled) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null)
			return;

		boolean success = mBluetoothGatt.setCharacteristicNotification(ch, enabled);
		if (!success) {
			Log.e("------", "Seting proper notification status for characteristic failed!");
		}

		// This is also sometimes required (e.g. for heart rate monitors) to
		// enable notifications/indications
		// see:
		// https://developer.bluetooth.org/gatt/descriptors/Pages/DescriptorViewer.aspx?u=org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
        for (BluetoothGattDescriptor descriptor : ch.getDescriptors()) {
            //find descriptor UUID that matches Client Characteristic Configuration (0x2902)
            // and then call setValue on that descriptor
            Log.d(TAG, "descriptor ENABLE");
            descriptor.setValue( BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
	}

	/* defines callback for scanning results */
	private BluetoothAdapter.LeScanCallback mDeviceFoundCallback = new BluetoothAdapter.LeScanCallback() {
		@Override
		public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
			mUiCallback.uiDeviceFound(device, rssi, scanRecord);
		}
	};
	private ScanCallback mScanCallback=new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			Log.d(TAG,"onScanResult");
			BluetoothDevice device = result.getDevice();
			String deviceAddress = device.getAddress();
			if (device.getName()!=null)
				mUiCallback.uiDeviceFound(device,result.getRssi(),result.getScanRecord().getBytes());
			else{
				Log.d(TAG,"onScanResult device null");
			}
		}
	};
	/* callbacks called for any action on particular Ble Device */
	private final BluetoothGattCallback mBleCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				mConnected = true;
				if (timerBTConnect != null) {
					myTimerTask.cancel();
					timerBTConnect.cancel();
				}
				mUiCallback.uiDeviceConnected(mBluetoothGatt, mBluetoothDevice);

				// now we can start talking with the device, e.g.
				mBluetoothGatt.readRemoteRssi();
				// response will be delivered to callback object!
				Log.d(TAG, "BluetoothProfile.STATE_CONNECTED");
				// in our case we would also like automatically to call for
				// services discovery
				startServicesDiscovery();

				// and we also want to get RSSI value to be updated periodically
				// startMonitoringRssiValue();
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				Log.d(TAG, "BluetoothProfile.STATE_DISCONNECTED");
				mConnected = false;
				mUiCallback.uiDeviceDisconnected(mBluetoothGatt, mBluetoothDevice);
			} else {
				Log.d(TAG, "BluetoothProfile.STATE_:" + newState);
				// mConnected = false;
				// mUiCallback.uiDeviceDisconnected(mBluetoothGatt,
				// mBluetoothDevice);
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered");

            if (status == BluetoothGatt.GATT_SUCCESS) {
				// now, when services discovery is finished, we can call
				// getServices() for Gatt
				getSupportedServices();
			}
		}
        int mode=0;
		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			// we got response regarding our request to fetch characteristic
			// value
            Log.d(TAG, "onCharacteristicRead");
            String name=gatt.getDevice().getName();
            mode=0;
            if (name.substring(0,1).equalsIgnoreCase("1"))
                   mode=1;

            if (status == BluetoothGatt.GATT_SUCCESS) {
				// and it success, so we can get the value
				getCharacteristicValue(characteristic,mode);
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			Log.d(TAG,"onCharacteristicChanged");
			// characteristic's value was updated due to enabled notification,
			// lets get this value
			// the value itself will be reported to the UI inside
			// getCharacteristicValue
            String name=gatt.getDevice().getName();
            mode=0;
            if (name.substring(0,1).equalsIgnoreCase("1"))
                mode=1;
			getCharacteristicValue(characteristic,mode);
			// also, notify UI that notification are enabled for particular
			// characteristic
			mUiCallback.uiGotNotification(mBluetoothGatt, mBluetoothDevice, mBluetoothSelectedService, characteristic);
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			String deviceName = gatt.getDevice().getName();
			String serviceName = BleNamesResolver
					.resolveServiceName(characteristic.getUuid().toString().toLowerCase(Locale.getDefault()));
			String charName = BleNamesResolver
					.resolveCharacteristicName(characteristic.getUuid().toString().toLowerCase(Locale.getDefault()));
			String description = "Device: " + deviceName + " Service: " + serviceName + " Characteristic: " + charName;

            mode=0;
            if (deviceName.substring(0,1).equalsIgnoreCase("1"))
                mode=1;
			// we got response regarding our request to write new value to the
			// characteristic
			// let see if it failed or not
            BluetoothGattCharacteristic   btchW = mBluetoothSelectedService.getCharacteristic(BleDefinedUUIDs.Characteristic.MONITOR_WRITE_CHARACTERISTIC);
            if (status == BluetoothGatt.GATT_SUCCESS) {
			/*	mUiCallback.uiSuccessfulWrite(mBluetoothGatt, mBluetoothDevice, mBluetoothSelectedService,
						characteristic, description);*/
                if (cmdWrite==cmdVer) { //PAIRING
                    Log.d(TAG, "PRESS write offsetTime");
                    byte [] timeoff= PressureMonitorUtils.getoffsetTime();
                    cmdWrite=cmdTimeOffset;
                    writeDataToCharacteristic(btchW,new byte[]{cmdTimeOffset,timeoff[3],timeoff[2],timeoff[1],timeoff[0]});
                }
                else if (mode==1 && cmdWrite==cmdTimeOffset){
                    Log.d(TAG, "PRESS write disconnect");
                    cmdWrite=0x22;
                    writeDataToCharacteristic(btchW,new byte[]{(byte)0x22});
                }
                else if (mode==1 && cmdWrite==0x22){
                    PressureMonitorUtils.saveAccountID(mParent,accounttID);
                    Log.d(TAG, "PRESS end");
                    mUiCallback.uiSuccessfulWrite(mBluetoothGatt,mBluetoothDevice,mBluetoothSelectedService,characteristic,END_DESCRIPTION);
                }
                else if (mode==0 && cmdWrite==cmdTimeOffset){
                    Log.d(TAG, "Read pressure set characteristic"+deviceName);
                    BluetoothGattCharacteristic  btch2 = mBluetoothSelectedService
                            .getCharacteristic(BleDefinedUUIDs.Characteristic.READ_PRESSURE_CHARACTERISITIC);
                    if (btch2 == null) {
                        Log.d(TAG,
                                "Could not find READ_PREAS_CHARACTERISITIC Characteristic");
                    } else {
                        Log.d(TAG,
                                "READ_PREAS_CHARACTERISITIC retrieved properly");
                        setNotificationForCharacteristic(btch2, true);
                    }

                }

            } else {
				mUiCallback.uiFailedWrite(mBluetoothGatt, mBluetoothDevice, mBluetoothSelectedService, characteristic,
						description + " STATUS = " + status);
			}
		};

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				// we got new value of RSSI of the connection, pass it to the UI
				mUiCallback.uiNewRssiAvailable(mBluetoothGatt, mBluetoothDevice, rssi);
			}
		};
	};

	protected static final String TAG = "BleWrapper.java";

	public static boolean isBLEDevice(String devAddress) {
		if (devAddress == null)
			return false;
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null)
			return false;
		BluetoothDevice auxDev = bluetoothAdapter.getRemoteDevice(devAddress);
		if (auxDev == null) {
			Log.d(TAG, "Trying: to connect...2:" + "auxDev == null");
			return false;
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
			return false;
		if (auxDev.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC
				|| auxDev.getType() == BluetoothDevice.DEVICE_TYPE_DUAL)
			return false;
		return true;
	}
	public static boolean isTypeDual(BluetoothDevice dev){
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2)
			return false;
		else
			return dev.getType() == BluetoothDevice.DEVICE_TYPE_DUAL;
		
	}

	private Context mParent = null;
	private boolean mConnected = false;
	private String mDeviceAddress = "";

	private BluetoothManager mBluetoothManager = null;
	private BluetoothAdapter mBluetoothAdapter = null;
	private BluetoothDevice mBluetoothDevice = null;
	private BluetoothGatt mBluetoothGatt = null;
	private BluetoothGattService mBluetoothSelectedService = null;
	private List<BluetoothGattService> mBluetoothGattServices = null;

	private Handler mTimerHandler = new Handler();
	private boolean mTimerEnabled = false;

}
