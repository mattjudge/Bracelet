package com.localhost.bracelet;

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class KnownGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String SERIAL_COMMUNICATION = "0000ffe1-0000-1000-8000-00805f9b34fb";
    public static String SERIAL_COMMUNICATION_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";

    static {
        // Services.
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        attributes.put(SERIAL_COMMUNICATION_SERVICE, "Serial Communication Service");
        // Characteristics.
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");
        attributes.put(SERIAL_COMMUNICATION, "Serial Communication");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
