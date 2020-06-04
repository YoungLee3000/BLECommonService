package com.nlscan.blecommservice.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public class BluetoothUtils {
    public static final String WRITE_PACKET_DATE_START = "7E0130303030"; //发送数据固定起始数据域格式
    public static final String WRITE_PACKET_DATE_END = "3B03"; // 发送数据固定结束数据域格式
    public static final String START_PACKET_HEX = "5DCC"; // 扫描数据固定起始格式
    public static final String START_COMMAND_HEX = "5DCE"; // 命令固定起始格式
    public static final String START_BATTERY_COMMAND_HEX = "5DCF"; // 电量固定起始格式
    public static final String START_BATTERY_STATUS_COMMAND_HEX = "5DDF"; // 充电状态变化固定起始格式
    public static final String GET_CONFIG_CALLBACK_PACKET_START = "020130303030";// 获取配置回调固定数据域开始格式
    public static final String GET_CONFIG_CALLBACK_PACKET_END = "063B03"; // 获取配置回调固定数据域结束格式


    private static StringBuffer buffer;

    private static final String TAG = "BleService-Util";
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

    public static boolean isHexString(String input){
        String regex="^[A-Fa-f0-9]+$";

        if(input.matches(regex) && input.length()%2 ==0){
            return true;
        }else{
            return false;
        }
    }
    /**
     * 将字节 转换为16进制字符串
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

    public static String subHexString(String hexdata, int start, int end) {
        if (hexdata == null || hexdata.length() <= 0 || hexdata.length() - start <= 0 || hexdata.length()-end < start) {
            return null;
        }
        return hexdata.substring(start, hexdata.length()-end);
    }

    /**
     * 字符串转16进制
     * @param s
     * @return
     */
    public static String stringtoHex(String s) {
        String str = "";
        for (int i = 0; i < s.length(); i++) {
            int ch = (int) s.charAt(i);
            String s4 = Integer.toHexString(ch);
            str = str + s4;
        }
        return str;
    }

    /**
     * 将字符串转化为16进制的字节
     *
     * @param message  需要被转换的16进制字符
     *
     * @return
     */
    public static byte[] getHexBytes(String message) {
        if (message == null)return null;
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

    /**
     * 16进制转换成字符串
     * @param hexString 16进制字符串
     * @return
     */
    public static String fromHexString(String hexString){
        String result = "";
        if (hexString == null || hexString.equals("")) return result;

        String hexDigital = "0123456789ABCDEF";
        char[] hexs = hexString.toCharArray();

        byte[] bytes = new byte[hexString.length() / 2];

        int n;
        for (int i = 0; i < bytes.length; i++) {
            try {
                n = hexDigital.indexOf(hexs[2 * i]) * 16 + hexDigital.indexOf(hexs[2 * i +1]);
                bytes[i] = (byte)(n & 0xff);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        try {
            result = new String(bytes,"UTF-8");
        }catch (Exception e){
            e.printStackTrace();
        }
        return result;
    }
    /**
     * 分包
     */
    private static int currentPacketLen = 0;
    public static int currentPacketCodeType = 0;
    public static String appendHexString(String hexstring, int packetSum, int packetIndex, int sigleLen, int allpacketLen) {
        if (packetIndex == 1){
            if (buffer == null) {
                buffer = new StringBuffer();
            }else {
                buffer.delete(0,buffer.length());
            }
            if (hexstring.length() > 26) {
                currentPacketCodeType = Integer.parseInt(hexstring.substring(24, 26), 16);
            }
            //标记是同一个数据包，不同分包
            currentPacketLen = allpacketLen;
        }
        if (currentPacketLen == allpacketLen) {
            String dataFiled = subHexString(hexstring, 14, 4);// data filed
            buffer.append(dataFiled);
        }

        if (packetSum == packetIndex && buffer.length() == currentPacketLen*2) {
            currentPacketLen = 0;
            return subHexString(buffer.toString(),12,2);////保留 倒数 4~2位(0D) , 判断是不是 扫描数据
        }
        return null;
    }

    public static final String UPDATE_HEAD = "5DCB00005DCB";
    /**
     * 获取写数据的完整数据包， 包括统一协议和升级协议两种
     * @param inputHex
     * @return
     */
    public static String getWriteDataPacket(String inputHex){
        if (!TextUtils.isEmpty(inputHex) && inputHex.length()%2 == 0){


            String dataField = "";
            if (inputHex.startsWith(UPDATE_HEAD)){
                dataField = inputHex.replace(UPDATE_HEAD,"");
            }else {
                dataField = String.format("%s%s%s", WRITE_PACKET_DATE_START, inputHex, WRITE_PACKET_DATE_END);
            }

            int len = dataField.length() / 2;

            byte[] bytes = CRC16.setParamCRC(getHexBytes(dataField));

            String hexData = String.format("5DCC0101%02x%04x%s",len, len, bytesToHexString(bytes));

            return hexData;
        }
        return "";
    }


    /**
     *  is Scan data or other
     * @param data
     * @return
     */
    public static boolean isScanResultData(String data) {
        return  (data != null && data.endsWith("0D"));
    }

    private List<BluetoothGattService> getSupportedGattServices(BluetoothGatt bluetoothGatt) {
        if (bluetoothGatt == null) return null;
        List<BluetoothGattService> bluetoothGattServices = bluetoothGatt.getServices();
        Log.d(TAG," init BLE: "+bluetoothGattServices.size()+" ");
        for (BluetoothGattService gattService : bluetoothGattServices) {
            Log.d(""," BluetoothGattService "+gattService+" Characteristics.size: "+gattService.getCharacteristics().size()+" uuid: "+gattService.getUuid().toString());
            for (BluetoothGattCharacteristic bluetoothGattCharacteristic : gattService.getCharacteristics()) {
                String uuid = bluetoothGattCharacteristic.getUuid().toString();
                boolean b = bluetoothGatt.readCharacteristic(bluetoothGattCharacteristic);
                int properties = bluetoothGattCharacteristic.getProperties();
                Log.d(""," ===================Characteristic uuid: "+uuid+" "+b+" properties: "+properties+" value: can read: "+((properties
                        &BluetoothGattCharacteristic.PROPERTY_READ) !=0)+" can write: "+((properties & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
                        || (properties
                        & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) +
                        " notify: "+((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0));
                for (BluetoothGattDescriptor descriptor : bluetoothGattCharacteristic.getDescriptors()) {
                    Log.d(TAG, uuid+" characteristic descriptor uuid : "+ descriptor.getUuid());
                }
            }
        }
        return bluetoothGattServices;
    }

    /**
     *
     * @param device
     * @return
     */
    public static boolean getConnectState(BluetoothDevice device){
        try {
            Method isConnectedMethod = BluetoothDevice.class.getDeclaredMethod("isConnected", (Class[]) null);
            isConnectedMethod.setAccessible(true);
            boolean isConnected = (boolean) isConnectedMethod.invoke(device, (Object[]) null);
            if(isConnected){
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getMacAddress(Context context){
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        String address = null;
        if (bluetooth != null && bluetooth.isEnabled()) {
            address = bluetooth.getAddress();
        }
        if (address == null){
            address = android.provider.Settings.Secure.getString(context.getContentResolver(), "bluetooth_address");
        }
        if (address == null){
            address = "25:05:C4:65:82:3F";//default
        }
        address = address.replaceAll(":","");
        if (address.length() == 12)
            address = address.substring(6,12);
        if (address !=null && address.length() == 6)
            return address;
        else {
            return "C4823F";
        }
    }
    /**
     *
     * @param address bluetooth address
     * @return
     */
    public static boolean isBLEDevice(String address, Context context){
        Log.i(TAG,"[Is BLE Device] start : "+address);
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && address != null) {
            BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(address);
            BluetoothClass bluetoothClass = remoteDevice.getBluetoothClass();
            Log.i(TAG,"[Is BLE Device] class: "+bluetoothClass+" name: ["+(remoteDevice!=null ? remoteDevice.getName()+"]":"]")+
                    (bluetoothClass != null? "major: "+bluetoothClass.getMajorDeviceClass():"]  ")+" model: ["+Build.MODEL+"] type: "+ (remoteDevice!=null ?remoteDevice.getType():-1));
            Log.i(TAG," "+(remoteDevice.getName().equals(Build.MODEL))+" "+(remoteDevice.getType() == BluetoothDevice.DEVICE_TYPE_LE)+(bluetoothClass.getMajorDeviceClass() == 1280));
            if (remoteDevice != null
                    && remoteDevice.getName() != null
                    //modify 20191218
                    //&& remoteDevice.getName().startsWith(String.format("BG%s",BluetoothUtils.getMacAddress(context)))
                    //&& remoteDevice.getName().startsWith(Build.MODEL)//add for device will changed , 20200324
                    && remoteDevice.getType() == BluetoothDevice.DEVICE_TYPE_LE
                    && bluetoothClass != null
                    && (bluetoothClass.getMajorDeviceClass() == 1280 || bluetoothClass.getMajorDeviceClass() == 7936) ) { //PERIPHERAL_KEYBOARD 0x540 外围键盘设备
                Log.i(TAG,"isBLEDevice true : "+ address);
                return true;//name: NLS-NFT10 major: 1280 model: NLS-NFT10 type: 2
            }
        }
        Log.i(TAG,"isBLEDevice false");
        return false;
    }

    public static void unboundDevice(String macAddress){
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        if (defaultAdapter != null && !TextUtils.isEmpty(macAddress)){
            Set<BluetoothDevice> devices = defaultAdapter.getBondedDevices();
            for (BluetoothDevice device : devices) {
                if (device != null && !TextUtils.isEmpty(device.getAddress())){
                    String badgeMac = device.getAddress().replaceAll(":", "");
                    if (macAddress.equals(badgeMac))unboundDevice(device);
                }
            }
        }
    }
    /**
     * 解绑
     * @param device
     * @return
     */
    public static boolean unboundDevice(BluetoothDevice device){
        //Log.i("BadgeService-Util","unboundDevice : "+device.getName());
        if (device.getBondState() == BluetoothDevice.BOND_BONDING) {
            return cancelBondProcess(device);
        }
        if (device.getBondState() != BluetoothDevice.BOND_NONE)
            return removeBound(device);

        return true;
    }

    private static boolean cancelBondProcess(BluetoothDevice device) {
        try {
            Method method = BluetoothDevice.class.getMethod("cancelBondProcess");
            return (boolean) method.invoke(device);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean removeBound(BluetoothDevice device) {
        //Log.i("BadgeService-Util","removeBound : "+device.getName());
        try {
            Method method = BluetoothDevice.class.getMethod("removeBond");
            return (boolean) method.invoke(device);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
        return false;
    }

    //disconnect -10
    public static void sendBatteryInfo(Context context, int level) {
        Intent intent = new Intent("nlscan.action.BG_BATTERY_CHANGED");
        intent.putExtra("level", level);
        if (context != null) {
            context.sendBroadcast(intent);
        }
    }

    public static void sendBatteryChargeStateInfo(Context context, int state) {
        Intent intent = new Intent("nlscan.action.BG_BATTERY_CHARGE_STATE_CHANGED");
        intent.putExtra("state", state);
        if (context != null) {
            context.sendBroadcast(intent);
        }
    }
}
