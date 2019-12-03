package com.nlscan.blecommservice;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

import java.util.UUID;

public class BluetoothUtils {

    /**
     *  是否开启蓝牙通知
     * @param bluetoothGatt
     * @param enable
     * @param characteristic
     * @return
     */
    public static boolean enableNotification(BluetoothGatt bluetoothGatt,
                                             boolean enable, BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null || characteristic == null) {
            return false;
        }
        int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0 )return false;
        if (!bluetoothGatt.setCharacteristicNotification(characteristic, enable)) {
            return false;
        }
        //获取到Notify当中的Descriptor通道  然后再进行注册
        BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(UUIDManager.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (clientConfig == null) {
            return false;
        }
        if (enable) {
            clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        } else {
            clientConfig.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        }
        return bluetoothGatt.writeDescriptor(clientConfig);
    }

    /**
     * 将字节 转换为字符串
     *
     * @param data 需要转换的字节数组
     * @return 返回转换完之后的数据
     */
    public static String bytesToHexString(byte[] data) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (data == null || data.length <= 0) {
            return null;
        }
        for(byte byteChar : data)
            stringBuilder.append(String.format("%02X", byteChar));
        return stringBuilder.toString();
    }
    public static byte[] splitBytes(byte[] data,int offset) {
        if (data == null || data.length <= 0 || data.length - offset <=0) {
            return null;
        }
        int len = data.length - offset - 4;
        byte[] strBytes = new byte[len];
        for (int i = 0; i != (len); ++i) strBytes[i] = data[offset + i];
        return strBytes;
    }
        /**
         * 将字符串转化为16进制的字节
         *
         * @param message  需要被转换的字符
         *
         * @return
         */
    public static byte[] getHexBytes(String message) {
        int len = message.length() / 2;
        char[] chars = message.toCharArray();

        String[] hexStr = new String[len];

        byte[] bytes = new byte[len];

        for (int i = 0, j = 0; j < len; i += 2, j++) {
            hexStr[j] = "" + chars[i] + chars[i + 1];
            bytes[j] = (byte) Integer.parseInt(hexStr[j], 16);
        }
        return bytes;
    }
}
