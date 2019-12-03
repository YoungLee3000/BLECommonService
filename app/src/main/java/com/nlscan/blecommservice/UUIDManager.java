package com.nlscan.blecommservice;

import java.util.UUID;

public class UUIDManager {
    //Nordic UART Service
    public static final UUID SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    //写  WRITE , WRITE NO RESPONSE
    public static final UUID UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");

    //  扫描结果 NOTIFY
    public static final UUID NOTIFY_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    // NOTIFY DESCRIPTOR
    public static final UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    //Battery service
    public static final UUID BATTERY_SERVICE_UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    //Battery Level charact  , NOTIFY READ
    public static final UUID BATTERY_LEVEL_UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
}
