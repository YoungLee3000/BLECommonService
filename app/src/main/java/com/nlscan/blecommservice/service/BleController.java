package com.nlscan.blecommservice.service;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

import com.nlscan.blecommservice.IBatteryChangeListener;
import com.nlscan.blecommservice.IBleScanCallback;
import com.nlscan.blecommservice.IScanConfigCallback;
import com.nlscan.blecommservice.utils.UUIDManager;
import com.nlscan.blecommservice.utils.BluetoothUtils;
import com.nlscan.blecommservice.utils.CodeType;

import java.util.LinkedList;

public class BleController {
    private static final String TAG = "BleService";
    private final Context mContext;
    //private final Context mContext;
    private IBleScanCallback mScanCallback;//scan
    private IBleScanCallback mScanCallback1;//scan
    //private String lastData;
    private String mCurrentUnboundAddress;
    private RemoteCallbackList<IBatteryChangeListener> mBadgeBatteryListeners;//battery
    private IScanConfigCallback scanConfigOneCallback;
    private IScanConfigCallback scanConfigSecondCallback;

    //uhf相关
    private static final String RESPONE_UHF_PREFIX_HEX = "02FF";//uhf响应格式
    private static final String RESPONE_IMU_PREFIX_HEX = "02FE";//imu响应格式
    private LinkedList<String> mUhfList = new LinkedList<>();

    public String getUhfResult(){
        return   mUhfList.size() > 0 ? mUhfList.poll() : null;
    }

    public BleController(Context context) {
        mContext = context;
        mBadgeBatteryListeners = new RemoteCallbackList<>();
    }

    /**
     *  Is BLE Scan device.
     * @param gatt
     * @return
     */
    public boolean isClientCompatible(final BluetoothGatt gatt) {
        if (gatt == null)return false;
        final BluetoothGattService uartService = gatt.getService(UUIDManager.SERVICE_UUID);
        if (uartService == null)
            return false;
        final BluetoothGattCharacteristic characteristic = uartService.getCharacteristic(UUIDManager.NOTIFY_UUID);
        if (characteristic == null || characteristic.getDescriptor(UUIDManager.CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID) == null)
            return false;
        final BluetoothGattCharacteristic txCharacteristic = uartService.getCharacteristic(UUIDManager.UART_TX_CHARACTERISTIC_UUID);
        return txCharacteristic != null;
    }

    /**
     * 向 BLE 终端写入数据
     * @param data 16进制
     * @return
     */
    public boolean writeBluetoothData(BluetoothGatt gatt, String data){
        if (gatt == null || data == null)return false;
        BluetoothGattService service = gatt.getService(UUIDManager.SERVICE_UUID);
        if (service == null)return false;
        BluetoothGattCharacteristic writeCharact = service.getCharacteristic(UUIDManager.UART_TX_CHARACTERISTIC_UUID);
        if (writeCharact == null)return false;
        gatt.setCharacteristicNotification(writeCharact, true); // 设置监听
        // 当数据传递到蓝牙之后
        // 会回调BluetoothGattCallback里面的write方法
        writeCharact.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        byte[] hexBytes = BluetoothUtils.getHexBytes(data);
        Log.d(TAG,"the bytes are " + hexBytes.length);
        writeCharact.setValue(hexBytes);
        return gatt.writeCharacteristic(writeCharact);
    }

    /**
     * 0: 表示未插入电源
     * 1： 正在充电
     * 2： 插入电源，未充电
     * @param gatt
     */
    public void  getChargeState(BluetoothGatt gatt){
        //get charge state
        Log.v(TAG, " getChargeState "+gatt);
        if (gatt == null)return;
        String dataPacket = BluetoothUtils.getWriteDataPacket(BluetoothUtils.stringtoHex("@WLSQCS"));
        writeBluetoothData(gatt, dataPacket);
    }


    /**
     * 读取电池电量
     * @param gatt
     * @return
     */
    public boolean readBluetoothBattery(BluetoothGatt gatt){
        if (gatt == null)return false;
        BluetoothGattService service = gatt.getService(UUIDManager.BATTERY_SERVICE_UUID);
        if (service == null)return false;
        BluetoothGattCharacteristic readCharact = service.getCharacteristic(UUIDManager.BATTERY_LEVEL_UUID);

        if (readCharact == null)return false;
        gatt.setCharacteristicNotification(readCharact, true); // 设置监听


        return gatt.readCharacteristic(readCharact);
    }



    /**
     * 读取设备的产商信息
     * @param gatt
     * @return
     */
    public boolean readDeviceInformation(BluetoothGatt gatt){
        Log.d(TAG,"begin to read device information " );
        if (gatt == null)return false;
        BluetoothGattService service = gatt.getService(UUIDManager.DEVICE_INFORMATION_UUID);
        Log.d(TAG,"the service is null " + (service == null));
        if (service == null)return false;
        BluetoothGattCharacteristic readCharact = service.getCharacteristic(UUIDManager.DEVICE_INFORMATION_MODEL_UUID);

        if (readCharact == null)return false;
        gatt.setCharacteristicNotification(readCharact, true); // 设置监听


        return gatt.readCharacteristic(readCharact);
    }





    /**
     *  post data to client
     * @param hexResult
     */
    //test private->public
    private void postData(String hexResult,int codeType, String rawHexData){
        if(hexResult == null)return;
        Log.i(TAG, "postData: hex: ["+hexResult+"]  parse: ["+ new String(BluetoothUtils.getHexBytes(hexResult))+"] codeType = ["+ CodeType.getCodeTypeString(codeType)+"] callback: " + (mScanCallback!=null));
        if (mScanCallback != null){
            try {
                mScanCallback.onReceiveResult(hexResult, CodeType.changeCodeTypeToCommon(codeType),  rawHexData);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        if (mScanCallback1 != null){
            try {
                mScanCallback1.onReceiveResult(hexResult, codeType,  rawHexData);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     * @param scanCallback
     */
    public void setScanCallback(IBleScanCallback scanCallback) {
        if (mScanCallback == null){
            this.mScanCallback = scanCallback;
        }else {
            //will replease
            this.mScanCallback1 = scanCallback;
        }

    }

    /**
     *
     * 起始字节 总包数   序号  单次包长    总长度               数据域    CRC
     * 5DCC     01       01     1F         001F                 0x00      0x0000
     *  get response packet
     * @return
     */
    public boolean sendResponsePacket(BluetoothGatt gatt, String hexstring){
        if (hexstring != null && hexstring.length() > 18){
            if (hexstring.startsWith(BluetoothUtils.START_PACKET_HEX)) {
                /*String responsePacket = String.format("%s00%s%s%s",
                        BluetoothUtils.START_PACKET_HEX, //起始字节
                        hexstring.substring(6, 14),// 序号 单包长  总长
                        "01",//数据域
                        "F1D1");// CRC
                writeBluetoothData(gatt, responsePacket);  modified , no response packet*/
                return true;
            }else if (hexstring.startsWith(BluetoothUtils.START_BATTERY_COMMAND_HEX)){
                handleCharacteristicRead(BluetoothUtils.subHexString(hexstring, 14,4));
            }else if (hexstring.startsWith(BluetoothUtils.START_BATTERY_STATUS_COMMAND_HEX)){//5DDF0101010001 30 D7A3
                handleChargeStateChange(BluetoothUtils.subHexString(hexstring, 14,4));
            }else {
                specialHandle(gatt, hexstring);
            }
        }
        return false;
    }

    /**
     *
     * @param hexString  raw hex data
     *                   STX   ATTR  LEN       AL_TYPE   Symbology_ID   Data
     *                   0x02  0x00  0x00000   0x3B      0x01
     */
    public String sendScanResult(String hexString){
        if (hexString != null && hexString.length() > 18){
            if (hexString.startsWith(BluetoothUtils.START_PACKET_HEX)){
                int packetSum= Integer.parseInt(hexString.substring(4, 6), 16);//总包数
                int packetIndex = Integer.parseInt(hexString.substring(6, 8), 16);//序号
                int singlePacketLen = Integer.parseInt(hexString.substring(8, 10), 16);//单次包长 1F
                int allPacketLen = Integer.parseInt(hexString.substring(10, 14), 16);//总包长
                //debug
                /*if (!BluetoothUtils.subHexString(hexString, 42, 6).equals(lastData)){
                    Log.w(TAG,"++++++++++++++++++++++++++data change++++++++++++++++++++++++++++ hexstring:"+hexString);
                    lastData = BluetoothUtils.subHexString(hexString, 42, 6);
                }*/

                if (packetSum == 1 && singlePacketLen == allPacketLen){
                    String dataFiled = BluetoothUtils.subHexString(hexString, 14, 4);
                    if (dataFiled == null)return null;

                    if(dataFiled.startsWith(RESPONE_UHF_PREFIX_HEX)){ //处理UHF模块返回数据
//                        Log.d(TAG,"uhf data is 1 " + dataFiled);
//                        mUhfList.add(BluetoothUtils.subHexString(dataFiled, 8,0));
//                        postData(dataFiled , BluetoothUtils.currentPacketCodeType, hexString);
                        return dataFiled.substring(4);
//                        return BluetoothUtils.subHexString(dataFiled, 8,0);
                    }
                    else if (dataFiled.startsWith(RESPONE_IMU_PREFIX_HEX)){
                        return dataFiled;
                    }
                    else if (dataFiled.startsWith("0200") && dataFiled.length() >= 16){//新的数据域 ， CRC校验
                        //STX(0x02)   ATTR(0x00)  LEN(0x0000)  AL_TYPE(0x3B)   Symbology_ID(0x00)     数据域                 LRC校验位
                        //02              00           000D       3B                  02             323930383831393034373830 C3
//                        Log.d(TAG,"post data 1");
                        postData(BluetoothUtils.subHexString(dataFiled, 12,2),//code result
                                Integer.parseInt(dataFiled.substring(10,12),16),hexString);//code type
                    }else if (dataFiled.startsWith(BluetoothUtils.GET_CONFIG_CALLBACK_PACKET_START)
                            && dataFiled.endsWith(BluetoothUtils.GET_CONFIG_CALLBACK_PACKET_END)){
                        //处理配置回包  查询 都会带 063B03
                        handleConfigCallback(BluetoothUtils.subHexString(dataFiled,12,6));
                        return dataFiled;
                    }else if (dataFiled.startsWith("7E")){//兼容处理枪的数据 only for test
//                        Log.d(TAG,"post data 2");
                        postData(BluetoothUtils.subHexString(hexString, 48,6), 0,hexString);//保留 倒数 8~6位(0D) , 判断是不是 扫描数据
                    }
                    else {
                        handleConfigCallbackReturnHex(dataFiled);
                        return dataFiled;
                    }
                }else if (packetSum > 1){ // 分包处理
                    if (packetIndex == 1){
                        rawHexString = hexString;
                    }else {
                        rawHexString += (hexString+"\n");
                    }
                    String data = BluetoothUtils.appendHexString(hexString, packetSum, packetIndex, singlePacketLen, allPacketLen);
                    if (data != null){
//                        Log.d(TAG,"uhf data is  2 " + data);


                        BluetoothUtils.currentPacketCodeType = 0;
                        if (data.startsWith(RESPONE_UHF_PREFIX_HEX) ){
//                            Log.d(TAG,"uhf data is  3 " + data);
//                            mUhfList.add(data);
                            return data.substring(4);
                        }
                        else if (data.startsWith(RESPONE_IMU_PREFIX_HEX)){
                            return data;
                        }
                        else if ( "FF".equals(data.substring(8,10)) ){
                            return data;
                        }
                        else{
//                            Log.d(TAG,"post data 3");
                            postData(data , BluetoothUtils.currentPacketCodeType, rawHexString);
                        }

                    }
                }
            }
        }
        return null;
    }







    String rawHexString = "";

    /**
     * 处理清除配置命令
     * @param gatt
     * @param hexstring
     */
    private void specialHandle(BluetoothGatt gatt,final String hexstring) {
        if (hexstring.startsWith(BluetoothUtils.START_COMMAND_HEX) && hexstring.length() == 30) {
            Log.v(TAG, " command handle");
            String responsePacket = String.format("%s00%s%s%s",
                    BluetoothUtils.START_COMMAND_HEX, //起始字节
                    hexstring.substring(6, 14),// 序号 单包长  总长
                    "02",//数据域
                    "F1D1");// CRC

            writeBluetoothData(gatt, responsePacket);

            mCurrentUnboundAddress = hexstring.substring(14, 26);
            //wait to unbound
        }
    }

    public void onCharacteristicWriteFinishHandle(String hexString) {
        if (mCurrentUnboundAddress != null && hexString != null && hexString.startsWith(BluetoothUtils.START_COMMAND_HEX)){
            Log.v(TAG, " unboundDevice:  "+mCurrentUnboundAddress);
            BluetoothUtils.unboundDevice(mCurrentUnboundAddress);
            mCurrentUnboundAddress = null;
        }
    }

    public void setScanConfigOnceCallback(IScanConfigCallback scanConfigOnceCallback) {
        if (scanConfigOneCallback == null){
            this.scanConfigOneCallback = scanConfigOnceCallback;
        }else {
            this.scanConfigSecondCallback = scanConfigOnceCallback;
        }

    }
    /**
     * Battery Level
     * @param hexString
     */
    private void handleConfigCallback(String hexString) {
        if (hexString != null){
            String data = BluetoothUtils.fromHexString(hexString);
            Log.v(TAG, " handleConfigCallback:  "+hexString +"  "+ data +" callback: "+(scanConfigOneCallback!=null)+" callback1: "+(scanConfigSecondCallback!=null));
            if (scanConfigOneCallback != null){
                try {
                    scanConfigOneCallback.onConfigCallback(data);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                scanConfigOneCallback = null;
            }else if (scanConfigSecondCallback != null){
                try {
                    scanConfigSecondCallback.onConfigCallback(data);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                scanConfigSecondCallback = null;
            }

            if (data != null && data.startsWith("@WLSRSS")){
                if (BleService.ENABLE_TEST) {
                    Intent intent = new Intent("nlscan.acation.getbgrssi");
                    intent.putExtra("rssi", data.substring(7,data.length()));
                    mContext.sendBroadcast(intent);
                }
            }

            if (data != null && data.startsWith("@WLSQCS")){//充电状态
                BluetoothUtils.sendBatteryChargeStateInfo(mContext,data.endsWith("0")?0:1);
            }
        }
    }

    /**
     * 直接返回hex
     * @param hexString
     * @return
     */
    private void handleConfigCallbackReturnHex(String hexString){
        if (hexString != null) {
            Log.v(TAG, " handleConfigCallback:  " + hexString + " callback: " + (scanConfigOneCallback!=null));
            if (scanConfigOneCallback != null){
                try {
                    scanConfigOneCallback.onConfigCallback(hexString);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                scanConfigOneCallback = null;
            }else if (scanConfigSecondCallback != null){
                try {
                    scanConfigSecondCallback.onConfigCallback(hexString);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                scanConfigSecondCallback = null;
            }
        }
    }

    /**
     * Battery Level
     * @param hexString
     */
    public void handleCharacteristicRead(String hexString) {
        Log.i(TAG,"handleCharacteristicRead: "+hexString);
        if (mBadgeBatteryListeners != null && hexString != null){
            int N = mBadgeBatteryListeners.beginBroadcast();
            for (int i = 0; i < N; i++) {
                IBatteryChangeListener broadcastItem = mBadgeBatteryListeners.getBroadcastItem(i);
                if (broadcastItem != null){
                    try {
                        broadcastItem.onBatteryChangeListener(hexString.length() >= 4?
                                Integer.parseInt(BluetoothUtils.fromHexString(hexString)):
                                Integer.parseInt(hexString, 16));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            mBadgeBatteryListeners.finishBroadcast();
        }
        //send broadcast battery info
        sendBroadcast(hexString);
    }

    private void sendBroadcast(String hexString){
        if (hexString != null){
            int batteryLevel = hexString.length() >= 4 ?
                    Integer.parseInt(BluetoothUtils.fromHexString(hexString)) :
                    Integer.parseInt(hexString, 16);
            BluetoothUtils.sendBatteryInfo(mContext, batteryLevel);
        }
    }

    /**
     * Battery Charge State change
     * @param hexString
     */
    public void handleChargeStateChange(String hexString) {
        Log.i(TAG,"handleChargeStateChange: "+hexString);

        //send broadcast battery info
        if (hexString != null && hexString.length() == 2){
            int batteryState = hexString.equals("31")?1:0;
            BluetoothUtils.sendBatteryChargeStateInfo(mContext, batteryState);
        }
    }


    public void addBatteryListener(IBatteryChangeListener listener) {
        if (listener != null && mBadgeBatteryListeners != null){
            mBadgeBatteryListeners.register(listener);
        }
    }

    public void removeBatteryListener(IBatteryChangeListener listener) {
        if (listener != null && mBadgeBatteryListeners != null){
            try {
                mBadgeBatteryListeners.unregister(listener);
            }catch (Exception e){}

        }
    }

    public void release(){
        mCurrentUnboundAddress = null;
        mScanCallback = null;
        mScanCallback1 = null;
        //clean all listener
        if (mBadgeBatteryListeners != null) {
            mBadgeBatteryListeners.kill();
        }
    }

}
