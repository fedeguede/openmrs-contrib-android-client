package org.openmrs.mobile.bluetooth;

import java.util.UUID;

public class BleDefinedUUIDs {
	
	public static class Service {
		final static public UUID HEART_RATE               = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
		final static public UUID BIO_METER                = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
		final static public UUID CUSTOM_SERVICE           = UUID.fromString("00007809-0000-1000-8000-00805f9b34fb");

	};
	
	public static class Characteristic {
		final static public UUID HEART_RATE_MEASUREMENT   = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
		final static public UUID MANUFACTURER_STRING      = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb");
		final static public UUID MODEL_NUMBER_STRING      = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb");
		final static public UUID FIRMWARE_REVISION_STRING = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb");
		final static public UUID APPEARANCE               = UUID.fromString("00002a01-0000-1000-8000-00805f9b34fb");
		final static public UUID BODY_SENSOR_LOCATION     = UUID.fromString("00002a38-0000-1000-8000-00805f9b34fb");
		final static public UUID BATTERY_LEVEL            = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
        final static public UUID MONITOR_NOTIFY_CHARACTERISTIC = UUID.fromString("00008a92-0000-1000-8000-00805f9b34fb");
        final static public UUID READ_RANDOM_CHARACTERISITIC   = UUID.fromString("00008A82-0000-1000-8000-00805f9b34fb");
        final static public UUID READ_PRESSURE_CHARACTERISITIC   = UUID.fromString("00008A91-0000-1000-8000-00805f9b34fb");
        final static public UUID MONITOR_WRITE_CHARACTERISTIC = UUID.fromString("00008a81-0000-1000-8000-00805f9b34fb");

    }
	
	public static class Descriptor {
		final static public UUID CHAR_CLIENT_CONFIG       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
	}
	
}
