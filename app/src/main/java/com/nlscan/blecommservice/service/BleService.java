package com.nlscan.blecommservice.service;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.Settings;
import android.provider.SyncStateContract;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.nlscan.android.scan.ScanManager;
import com.nlscan.android.scan.ScanSettings;
import com.nlscan.blecommservice.IBleStateCallback;
import com.nlscan.blecommservice.R;
import com.nlscan.blecommservice.utils.BluetoothUtils;
import com.nlscan.blecommservice.utils.Command;
import com.nlscan.blecommservice.IBatteryChangeListener;
import com.nlscan.blecommservice.IBleInterface;
import com.nlscan.blecommservice.IBleScanCallback;
import com.nlscan.blecommservice.IScanConfigCallback;
import com.nlscan.blecommservice.utils.DeviceType;
import com.nlscan.blecommservice.utils.LogUtil;
import com.nlscan.blecommservice.utils.Setting;
import com.nlscan.blecommservice.utils.UUIDManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.Timer;
import java.util.TimerTask;

public class BleService extends Service implements Handler.Callback{
    private static final String TAG = "BleService";
    public static final boolean ENABLE_TEST = false;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothGattService mUartGattService;
    private BluetoothGatt mBluetoothGatt;
    private String mBluetoothDeviceAddress;
    private String mLastConnectedDeviceAddress;
    private BleController mBleController;
    private String mCurrentACLAddress;//ACL BOUND STATUS 11
    private String mConnectAddress = "";
    private boolean mIfConnect = false;

    private final int MSG_WHAT_BATTERY       = 0x00; // get battery level
    private final int MSG_WHAT_CHARGE_STATE  = 0x10; // get charge state
    private final int MSG_WHAT_RSSI          = 0x100; // get rssi
    private final int MSG_WHAT_ENABLE_BT          = 0x200; // enable bt
    private final int MSG_WHAT_DEVICE_GET          = 0x300; // enable bt
	
	
	
	
	 public static final int MSG_WHAT_BT_STATE_CHANGED          = 0x300; // ACTION_STATE_CHANGED
    public static final int MSG_WHAT_BT_BOND_STATE_CHANGED     = 0x310; // ACTION_BOND_STATE_CHANGED
    public static final int MSG_WHAT_BT_ACL_DISCONNECTED       = 0x320; // ACTION_ACL_DISCONNECTED
    public static final int MSG_WHAT_BT_ACL_CONNECTED          = 0x330; // ACTION_ACL_CONNECTED
    public static final int MSG_WHAT_BT_CONNECTION_STATE_CHANGED   = 0x340; // ACTION_CONNECTION_STATE_CHANGED

    public static final int  MSG_WHAT_UPDATE_SCAN_SETTINGS         = 0x400; // UPDATE_SCAN_SETTINGS
    private static final int MSG_WHAT_ENABLE_BATTERY_NOTIFY        = 0x500;// ENABLE_BATTERY_NOTIFY
    private static final int MSG_WHAT_ENABLE_UART_NOTIFY              = 0x600;// ENABLE_UART_NOTIFY


    //uhf 数据相关


    public static final String START_UHF_COMMAND_HEX = "7EFF";//uhf命令开头

    public static final String RESPONE_UHF_PREFIX_HEX = "02FF";//uhf响应格式

    private Stack<String> mSetStack = new Stack<>();

    private LinkedList<String> mUhfList = new LinkedList<>();
    private LinkedList<String> mImuList = new LinkedList<>();
    private Map<String,String> mDevicesMap = new HashMap<>();

    private static final String FAILED_STR = "failed";
    private static final int MAX_UHF_TAG = 15;
    private static final int MAX_IMU_TAG = 10;
    private String mLastReceiveData = "";
    private int mLastPackIndex =0;

    /**
     * Lock used in synchronization purposes
     */
    private final Object mLock = new Object();

    public static final int ERROR_MASK = 0x1000;

    private int mError;
    protected int mConnectionState;
    protected final static int STATE_DISCONNECTED = 0;
    protected final static int STATE_CONNECTING = -1;
    protected final static int STATE_CONNECTED = -2;
    protected final static int STATE_CONNECTED_AND_READY = -3; // indicates that services were discovered
    protected final static int STATE_CLOSED = -4;
	
	private IBleStateCallback mBleStateCallback;

    private ScanManager mScanManager;
    private Handler mHandler;
    private BleReceiver mReceiver;

    private String nameForState(int state){
        if (state == STATE_DISCONNECTED){
            return getString(R.string.disconnected);
        }else if (state == STATE_CONNECTING){
            return getString(R.string.connecting);
        }else if (state == STATE_CONNECTED){
            return getString(R.string.connected);
        }else if (state == STATE_CONNECTED_AND_READY){
            return getString(R.string.connected_ready);
        }else if (state == STATE_CLOSED){
            return getString(R.string.closed);
        }
        return "UnKnow State "+state;
    }

    private IBleInterface.Stub stub = new IBleInterface.Stub() {

        @Override
        public void setScanCallback(IBleScanCallback callback)  {
            Log.d(TAG, "setScanCallback "+ callback);
            if (initialize()) {
                if (mBleController != null){
                    mBleController.setScanCallback(callback,true);
                }
                //Auto find ble device to connect
                //first to find
                try {
                    if (callback != null) {
                        findBleDeviceToConnect(null);
                    }
                }catch (Exception e){
                }
            }
        }

        @Override
        public boolean setScanConfig(IScanConfigCallback callback, String str) throws RemoteException {
            if (!TextUtils.isEmpty(str) && str.startsWith(BluetoothUtils.UPDATE_HEAD)){
                Log.d(TAG,"blecontroller is null? " + (mBleController == null));
                Log.d(TAG, "setUpdateData:  "+ str.replaceAll(BluetoothUtils.UPDATE_HEAD,"")+" callback: "+(callback!=null));
            }else {
                Log.d(TAG, "setScanConfig " + (callback != null) + " " + str);
            }
            if (callback != null && str != null && "GETADDRESS".equals(str)){
                callback.onConfigCallback(mLastConnectedDeviceAddress);
                return true;
            }
            if (mBleController != null && !TextUtils.isEmpty(str)){

                if (callback != null)mBleController.setScanConfigOnceCallback(callback);//20191227 return config
                boolean state = mBleController.writeBluetoothDataPacket(mBluetoothGatt, BluetoothUtils.isHexString(str)?str:BluetoothUtils.stringtoHex(str));
                return state;
            }
            return false;
        }

        @Override
        public void addBatteryLevelChangeListener(IBatteryChangeListener callback) throws RemoteException {
            if (mBleController != null) {
                mBleController.addBatteryListener(callback);

                //主动读取电量
                mBleController.readBluetoothBattery(mBluetoothGatt);

                //get charge State
                //mHandler.removeMessages(MSG_WHAT_CHARGE_STATE);
                //mHandler.sendEmptyMessageDelayed(MSG_WHAT_CHARGE_STATE, 2000);
            }
        }

        @Override
        public void removeBatteryLevelChangeListener(IBatteryChangeListener callback) throws RemoteException {
            if (mBleController != null) {
                mBleController.removeBatteryListener(callback);
            }
        }

        @Override
        public void sendCommand(int cmd) throws RemoteException {
            //find badge
            if (cmd == Command.CMD_FIND_BADGE){
                if (mBleController != null){   // 频率2700F 持续 800Tms 最大音量 20V
                    //String dataPacket = BluetoothUtils.getWriteDataPacket(BluetoothUtils.stringtoHex("#BEEPON4000F1000T20V;LEDONS1C1000D"));
                    mBleController.writeBluetoothData(mBluetoothGatt, "#BEEPON4000F1000T20V;LEDONS1C1000D");
                }
            }
            // trigger once send battery level value
            else if (cmd == Command.CMD_GET_BATTERY_LEVEL){
                if (mBleController != null) {
                    mBleController.readBluetoothBattery(mBluetoothGatt);
                }
            }

        }




        /**
         * 发送uhf命令
         * @param command
         * @return
         * @throws RemoteException
         */
        @Override
        public String writeData(String command) throws RemoteException {
            mSetStack.clear();

//            if (command.substring(4,6).equals("22")) return "FF0422000004000002B76E";
            int timeout = command.substring(4,6).equals("29") ? 100 : 500;
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    mSetStack.add(FAILED_STR);
                    this.cancel();
                }
            },timeout);

            String operateCode = command.length() >= 6 ? command.substring(4,6) : "";


            //FF开头为UHF模块的命令，其它的则为蓝牙通讯的命令
            if (command.startsWith("FF")){
                mBleController.writeBluetoothData(mBluetoothGatt,START_UHF_COMMAND_HEX +
                        String.format("%04X",command.length()/2)  +  command);
            }
            else{
                mBleController.writeBluetoothData(mBluetoothGatt, command);
            }


            while (mSetStack.size() == 0){}
            String resultStr = "";
            if (mSetStack.size() > 0)   resultStr  = mSetStack.pop();
            Log.d(TAG,"the return str is " + resultStr);

            if (resultStr.startsWith("FF")){
                String relOperate = resultStr.length() >= 6 ? resultStr.substring(4,6) : "";
                if ("".equals(relOperate) || ! relOperate.equals(operateCode))
                    return  FAILED_STR;
            }



            return resultStr;


        }


        @Override
        public String getCachedData() throws RemoteException {
//            if (mUhfList.size() > 0) {

                StringBuilder sb = new StringBuilder("");
                int count = 0;

//            Log.d(TAG,"get tag ble change 4 ");

                if (mUhfList.size() > 0){
                    while (count < MAX_UHF_TAG){
//                    if (mUhfList.size() > 0) {
                        String data = mUhfList.poll();
                        if (data != null){
//                            Log.d(TAG,"list data is " + data);
                            sb.append(data);
                            sb.append(";");
                        }

//                    }
                        count++;
                    }
                }
                else{
                    while (count < MAX_IMU_TAG){
//                    if (mUhfList.size() > 0) {
                        String data = mImuList.poll();
                        if (data != null){
//                            Log.d(TAG,"list data is " + data);
                            sb.append(data);
                            sb.append(";");
                        }

//                    }
                        count++;
                    }
                }


                String result = sb.toString();
                return  result.length() > 0 ? result.substring(0,result.length()-1) : "";
//            }
//            else {
//                return "";
//            }
        }


        @Override
        public void clearCachedData() throws RemoteException {
            mUhfList.clear();
            mImuList.clear();
        }



        @Override
        public void setBleStateCallback(IBleStateCallback callback){
            mBleStateCallback = callback;
        }



    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG,"service onBind");
        return stub;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG,"service onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG,"service onCreate");
        mHandler = new Handler(this);
        mScanManager = ScanManager.getInstance();

        mReceiver = new BleReceiver(this, mHandler);

        //for test , 20200214
        if (initialize()){
            try {
                findBleDeviceToConnect(null);
            }catch (Exception e){
            }
        }
    }


   


    /**
     * 初始化
     * @return
     */
    private boolean initialize(){
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                //return false;
            }
        }

        if (mBluetoothManager != null) {
            mBluetoothAdapter = mBluetoothManager.getAdapter();
        }else {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        }

        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        if (mBleController == null) {
            mBleController = new BleController(this);
            mBleController.setDeviceType(mDeviceType);
        }
        return true;
    }

    @Override
    public boolean handleMessage(Message msg) {
        if (msg.what == MSG_WHAT_BATTERY) {
            if (mBleController != null) {
                Log.i(TAG, "read battery");
                mBleController.readBluetoothBattery(mBluetoothGatt);
            }
        } else if (msg.what == MSG_WHAT_CHARGE_STATE) {
            if (mBleController != null) {
                //get charge state
                mBleController.getChargeState(mBluetoothGatt);
            }
        } else if (msg.what == MSG_WHAT_RSSI) {
            if (ENABLE_TEST) {
                Log.i(TAG, "handleMessage read rssi " + mBluetoothGatt);

                if (mBluetoothGatt != null) {
                    boolean result = mBluetoothGatt.readRemoteRssi();
                    if (result) {
                        mHandler.sendEmptyMessageDelayed(MSG_WHAT_RSSI, 1400);

                        //replace by jar 20201021
                        //String packet = BluetoothUtils.getWriteDataPacket(BluetoothUtils.stringtoHex("@WLSRSS"));
                        //Log.i(TAG, "handleMessage read bg packet:  " + packet);
                        mBleController.writeBluetoothDataPacket(mBluetoothGatt, "@WLSRSS");
                    }
                }
            }
        } else if (msg.what == MSG_WHAT_ENABLE_BT) {
            if (mBluetoothAdapter != null) {
                mBluetoothAdapter.enable();
            }
        }else if (msg.what == MSG_WHAT_BT_STATE_CHANGED) {
            int state = msg.arg1;
            Log.i(TAG, "BT_STATE_CHANGED   STATE " + state+" mConnectionState: "+mConnectionState);
            if (state == BluetoothAdapter.STATE_OFF && (mConnectionState == STATE_CONNECTED||mConnectionState == STATE_CONNECTED_AND_READY)){
                disconnect("BT OFF");
            }
            //快速开关蓝牙
            //Log.i(TAG, "ACTION_STATE_CHANGED 1  STATE " + state+" mConnectionState: "+mConnectionState+" "+device);
            if (state == BluetoothAdapter.STATE_ON && (mConnectionState == STATE_CLOSED || mConnectionState ==STATE_DISCONNECTED)){
                findBleDeviceToConnect(null);
            }
        }else if (msg.what == MSG_WHAT_BT_BOND_STATE_CHANGED){
            String deviceAddress = (String) msg.obj;
            int previewBoundState = msg.arg1;
            int bondState = msg.arg2;
            Log.i(TAG, "BT_BOND_STATE_CHANGED: " + deviceAddress+" boundState: "+ bondState +" preview: "+previewBoundState+" "+mCurrentACLAddress+" filterAddressList: "+filterAddressList.size()+" "+filterAddressList.toString());

            if (((mCurrentACLAddress != null && mCurrentACLAddress.equals(deviceAddress))
                    || (mCurrentACLAddress == null && (mConnectionState!= STATE_CONNECTED && mConnectionState!= STATE_CONNECTED_AND_READY)))
                    && bondState == BluetoothDevice.BOND_BONDED && previewBoundState == BluetoothDevice.BOND_BONDING){
                if (deviceAddress != null && !filterAddressList.contains(deviceAddress)) {
                    findBleDeviceToConnect(deviceAddress);
                }
            }
        }else if (msg.what == MSG_WHAT_BT_ACL_DISCONNECTED){
            String deviceAddress = (String) msg.obj;
            int bondState = msg.arg1;
            Log.i(TAG, "BT_ACL_DISCONNECTED: "  + deviceAddress+" boundState: "+ bondState);
            if (mBluetoothDeviceAddress != null && mBluetoothDeviceAddress.equals(deviceAddress)){
                if (bondState == BluetoothDevice.BOND_BONDED){
                    //no to disconnect, because less to use settings do disconnect.
                }else {
                    disconnect("ACL DISCONNECTED");
                }
            }
            if (mCurrentACLAddress != null && mCurrentACLAddress.equals(deviceAddress)){
                mCurrentACLAddress = null;
            }
        }else if (msg.what == MSG_WHAT_BT_ACL_CONNECTED){
            String deviceAddress = (String) msg.obj;
            Log.i(TAG, "MSG_WHAT_BT_ACL_CONNECTED" +" filterAddressList: "+filterAddressList.size()+" "+filterAddressList.toString());
            if (deviceAddress != null && !filterAddressList.contains(deviceAddress)) {
                findBleDeviceToConnect(deviceAddress);
            }
        }else if (msg.what == MSG_WHAT_UPDATE_SCAN_SETTINGS){
            initScanSettings();
        }else if (msg.what == MSG_WHAT_ENABLE_BATTERY_NOTIFY){
            enableBatteryNotification();
        }else if (msg.what == MSG_WHAT_ENABLE_UART_NOTIFY){
            enableUartNotification();
        }
        return true;
    }
    /**
     *  find BLE device
     */
    private static final String STATE_CONNECT = "connect";
    private static final String STATE_DISCONNECT = "dis_connect";
    boolean foundDevice = false;
    public void findBleDeviceToConnect(String address){
        Log.i(TAG, "findBleDeviceToConnect:  address:" + address + " adapter: "+(mBluetoothAdapter!=null)+" enable: "+(mBluetoothAdapter!=null ?mBluetoothAdapter.isEnabled():false));
        mCurrentACLAddress = null;
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            if (address != null) {
                foundDevice = false;
            }
            Set<BluetoothDevice> connectedDevicesList = mBluetoothAdapter.getBondedDevices();
            if (connectedDevicesList == null) return;
            Log.i(TAG, "findBleDeviceToConnect: BondedDevicesList " + connectedDevicesList.size());
            for (final BluetoothDevice device : connectedDevicesList) {
                Log.i(TAG, "for each devices : "  + device.getAddress() + " " + device.getName()+" filterAddressList: "+filterAddressList.size()+" "+filterAddressList.toString());
                if (BluetoothUtils.isBLEDevice(device.getAddress(), BleService.this)
                        && !filterAddressList.contains(device.getAddress())
                        && (address == null || (address != null && device.getAddress().equals(address)))) { //PERIPHERAL_KEYBOARD 0x540 外围键盘设备

                    boolean connectState = BluetoothUtils.getConnectState(device);
                    Log.i(TAG, "getConnectedDevice: " + device.getAddress() + " " + device.getName() + "  state： " + connectState + " address: " + address);
                    if (connectState) {
                        Log.i(TAG, "fonded bonded BLE device: " + device.getAddress() + " Now to Connect " + mBluetoothGatt);
                        //disconnect("create new connection");//disconnect first  modified 2020623

                        Log.i(TAG, "start connect device");
                        if (mBleController != null)mBleController.setUpgradeMode(false);//reset mode
                        boolean stat = connectDevice(device.getAddress());
                        if (stat){
                            if (mBluetoothGatt != null && mError == 0) {
                                if (mBleController != null && mBleController.isClientCompatible(mBluetoothGatt)) {
                                    foundDevice = true;
                                    Log.i(TAG, "Connected to LE succeed  [" + device.getAddress() + " " + device.getName() + "]");
                                    LogUtil.saveLog("Connected succeed [" +  device.getName()+ " " + device.getAddress() + "]");
                                    //connect succeed , get battery info

                                    try {
                                        mBleStateCallback.onReceiveState(STATE_CONNECT);
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                    mConnectAddress = device.getAddress();

                                    BluetoothUtils.sendBatteryInfo(this,-10,mDeviceType);

                                    //未检测到产商信息时改用蓝牙名称判断类型
                                    if(device.getName().startsWith("SR")) mDeviceType = DeviceType.DT_CERES;
                                    if(device.getName().contains("BS")) mDeviceType = DeviceType.DT_BS30;
                                    mBleController.setDeviceType(mDeviceType);
                                    mDevicesMap.put(mConnectAddress, mDeviceType);
                                    removeAll(mConnectAddress);

                                    //使用产商信息判断设备类型
                                    getDeviceInfo();
                                    return;
                                } else {
                                    // not scan device , to disconnect.
                                    disconnect("device not Compatible");
                                }
                            }
                        }else {
                            address = null;
                            if (mBluetoothGatt == null) {
                                if (mError == -1) {
                                    Log.i(TAG, "connectGatt fail , reOpen bt   [" + device.getAddress() + " " + device.getName() + "]");
                                    //findBleDeviceToConnect(address);
                                    //2020628 , add for DeadObjectException , reOpen BT
                                    mBluetoothAdapter.disable();//disable bt first .
                                    mHandler.removeMessages(MSG_WHAT_ENABLE_BT);
                                    mHandler.sendEmptyMessageDelayed(MSG_WHAT_ENABLE_BT, 50);//enable.
                                    return;
                                }
                            }else {
                                if (device.getName() != null
                                       // && device.getName().contains("BS30")
                                ){
                                    //do noThing
                                }else {
                                    noBleDeviceDoDisconnect(null);
                                }
                            }
                        }
                    }
                   // return;
                }
            }
            if (address != null && !foundDevice){
                mCurrentACLAddress = address;
            }
        }
    }


    private int waitCount = 0;
    /**
     *  Connects to the GATT server hosted on the Bluetooth LE device.
     * @param address
     * @return
     */
    public boolean connectDevice(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mLastConnectedDeviceAddress != null && address.equals(mLastConnectedDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                Log.d(TAG, "connect fail , disconnect to create new connection.");
                disconnect("connect fail");
                //return false;//modified 20200624 for DeadObjectException
            }
        }

        //保证只能连接接收一个工牌的数据
        if (mLastConnectedDeviceAddress != null && !address.equals(mLastConnectedDeviceAddress) && mBluetoothGatt != null){
            try {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }catch (Exception e){}
        }

        BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(address);
        if (remoteDevice == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        BluetoothGatt bluetoothGatt = null;
        try {
            Log.v(TAG, "Trying to create a new connection.");
            bluetoothGatt = remoteDevice.connectGatt(this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        }catch (Exception e){
            mError = -1;
            Log.e(TAG, "connectGatt fail.");
            if (bluetoothGatt != null){
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
            return false;
        }

        if (bluetoothGatt == null){
            mError = -1;
            Log.e(TAG, "connectGatt fail. return false");
            LogUtil.saveLog("connectGatt fail. return false!!!");
            return false;
        }

        mBluetoothDeviceAddress = address;
        mError = 0;
        mConnectionState = STATE_CONNECTING;

        waitCount = 5;
        try {
            synchronized (mLock) {
                //Log.w(TAG, "wait connected");
                while ((mConnectionState == STATE_CONNECTING || mConnectionState == STATE_CONNECTED) && mError == 0 && waitCount > 0) {
                    Log.w(TAG, "wait waitCount: "+waitCount);
                    mLock.wait(1000);
                    Log.w(TAG, "wait notify or timeout errorCode: "+mError);
                    waitCount --;
                }
            }
            if (waitCount <= 0 || mError != 0 || mConnectionState != STATE_CONNECTED_AND_READY){
                Log.w(TAG, "connectDevice return false !   errorCode: "+mError+" , "+nameForState(mConnectionState));
                return false;
            }
        } catch (final InterruptedException e) {
            Log.e(TAG, "Sleeping interrupted", e);
            return false;
        }

        mLastConnectedDeviceAddress = mBluetoothDeviceAddress;
        Log.w(TAG, "connectDevice return true !   errorCode: "+mError+" : "+nameForState(mConnectionState));
        return true;
    }
	
	
	
	//移除所有已经绑定的设备
    private void removeAll(String currentAddress){
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        Log.d(TAG,"devices map size is " + bondedDevices.size());
        String deviceInforStr = mDevicesMap.get(currentAddress);
        if (deviceInforStr == null) return;
        for(BluetoothDevice device : bondedDevices) {
            if (device == null) continue;
            Log.d(TAG,"the device name is " + device.getName());
            if ( device.getAddress().equals(currentAddress)) continue;
            if (!mDevicesMap.containsKey(device.getAddress())) continue;
            try {
                Class btDeviceCls = BluetoothDevice.class;
                Method removeBond = btDeviceCls.getMethod("removeBond");
                String tempInfor = mDevicesMap.get(device.getAddress());
                Log.d(TAG,"the temp info is " + tempInfor);
                if (deviceInforStr.equals(DeviceType.DT_BS30) || deviceInforStr.equals(DeviceType.DT_CERES)){
                    removeBond.setAccessible(true);
                    removeBond.invoke(device);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


   
	
	
	
	
	

    /**
     * 连接成功，获取工牌信息
     */
    private void getDeviceInfo(){
        //send connected info.2020611
        BluetoothUtils.sendBatteryInfo(this,-1,mDeviceType);
        if ("".equals(mDeviceType)) BluetoothUtils.sendBatteryInfo(this,-1,DeviceType.DT_BS30);
        if (mHandler != null){
            Log.d(TAG,"send handler message");
            mHandler.removeMessages(MSG_WHAT_ENABLE_UART_NOTIFY);
            mHandler.sendEmptyMessageDelayed(MSG_WHAT_ENABLE_UART_NOTIFY, 300);

            mHandler.removeMessages(MSG_WHAT_ENABLE_BATTERY_NOTIFY);
            mHandler.sendEmptyMessageDelayed(MSG_WHAT_ENABLE_BATTERY_NOTIFY, 600);

            // init scan settings
            mHandler.removeMessages(MSG_WHAT_UPDATE_SCAN_SETTINGS);
            mHandler.sendEmptyMessageDelayed(MSG_WHAT_UPDATE_SCAN_SETTINGS, 1000);

            int anInt = Settings.System.getInt(getContentResolver(), "settings.enable.readbattery", 1);
            //get battery value
            if (!ENABLE_TEST && anInt == 1) {//now connected , bluetooth will send battery info , 20200430
                Log.d(TAG,"get battery level");
                mHandler.removeMessages(MSG_WHAT_BATTERY);
                mHandler.sendEmptyMessageDelayed(MSG_WHAT_BATTERY,
                        Settings.System.getInt(getContentResolver(), "settings.enable.readbattery.delay", 1500));
            }

            //get charge state
            /*mHandler.removeMessages(MSG_WHAT_CHARGE_STATE);
            mHandler.sendEmptyMessageDelayed(MSG_WHAT_CHARGE_STATE, 2000);*/
        }

        if (ENABLE_TEST){
            //get rssi
            if (mHandler != null){
                mHandler.removeMessages(MSG_WHAT_RSSI);
                mHandler.sendEmptyMessageDelayed(MSG_WHAT_RSSI, 1000);
            }
        }
    }

    private void enableUartNotification(){
        if (mBluetoothGatt == null)return;
        BluetoothGattService uartGattService = mBluetoothGatt.getService(UUIDManager.UART_SERVICE_UUID);// 获取到扫描服务的通道
        if (uartGattService != null) {
            //获取到Notify的Characteristic通道
            BluetoothGattCharacteristic notifyCharacteristic = uartGattService.getCharacteristic(UUIDManager.UART_NOTIFY_UUID);
            //注册Notify通知
            boolean stat = BluetoothUtils.enableNotification(mBluetoothGatt, true, notifyCharacteristic,
                    UUIDManager.UART_DESCRIPTOR_UUID);

            Log.v(TAG, "register Uart notification " + stat + " ");
            if (!stat){
                if (mHandler != null) {
                    mHandler.removeMessages(MSG_WHAT_ENABLE_UART_NOTIFY);
                    mHandler.sendEmptyMessageDelayed(MSG_WHAT_ENABLE_UART_NOTIFY, 400);
                }
            }
        }
    }

    private void  enableBatteryNotification(){
        if (mBluetoothGatt == null)return;
        BluetoothGattService batteryGattService = mBluetoothGatt.getService(UUIDManager.BATTERY_SERVICE_UUID);
        if (batteryGattService != null){
            //获取到Notify的Characteristic通道
            BluetoothGattCharacteristic notifyCharacteristic = batteryGattService.getCharacteristic(UUIDManager.BATTERY_LEVEL_NOTIFY_ADN_READ_UUID);
            //注册Notify通知
            boolean stat = BluetoothUtils.enableNotification(mBluetoothGatt, true, notifyCharacteristic,UUIDManager.BATTERY_DESCRIPTOR_UUID);
            Log.v(TAG, "register Battery notification " + stat + " ");
            if (!stat && mConnectionState == STATE_CONNECTED_AND_READY){
                if (mHandler != null) {
                    mHandler.removeMessages(MSG_WHAT_ENABLE_BATTERY_NOTIFY);
                    mHandler.sendEmptyMessageDelayed(MSG_WHAT_ENABLE_BATTERY_NOTIFY, 1000);
                }
            }
        }
    }

    private void initScanSettings(){
        Log.v(TAG, "initScanSettings : "+(mBluetoothGatt==null?"Disconnected":"Connected"));
        if (mBleController != null && mBluetoothGatt != null){
            StringBuffer stringBuffer = new StringBuffer();
            stringBuffer.append(Settings.System.getInt(getContentResolver(), Setting.BADGE_SOUND_ENABLE,1) != 0? "@GRBENA1;":"@GRBENA0;");
            stringBuffer.append(Settings.System.getInt(getContentResolver(), Setting.BADGE_VIBRATE_ENABLE,1) != 0? "GRVENA1;":"GRVENA0;");

            int iScanMode = 0;
            long lTimeOut = 50L;
            long lScanRepeatTimeout = 0L;
            Map<String, String> scanSettings = mScanManager.getScanSettings();
            if (scanSettings != null){
                String scanMode = scanSettings.get(ScanSettings.Global.SCAN_MODE);
                if (TextUtils.isDigitsOnly(scanMode)){
                    iScanMode = Integer.parseInt(scanMode);
                }
            }
            //Log.v(TAG, "initScanSettings ： iScanMode: "+iScanMode);
            if (iScanMode == 1){//连续扫描模式
                if (scanSettings != null){
                    String scanIntervalTime = scanSettings.get(ScanSettings.Global.SCAN_INTERVAL_TIME);//连续读码延时
                    if (TextUtils.isDigitsOnly(scanIntervalTime)){
                        lTimeOut = Long.parseLong(scanIntervalTime);
                        if (lTimeOut < 1 )lTimeOut = 1;
                        if (lTimeOut > 3600000 )lTimeOut = 3600000;
                    }
                }

                if (scanSettings != null){
                    String scanRepeatTimeout = scanSettings.get(ScanSettings.Global.SCAN_REPEAT_TIMEOUT);//重复条码间隔
                    if (TextUtils.isDigitsOnly(scanRepeatTimeout)){
                        lScanRepeatTimeout = Long.parseLong(scanRepeatTimeout);
                        if (lScanRepeatTimeout < 1 )lScanRepeatTimeout = 1;
                        if (lScanRepeatTimeout > 3600000 )lScanRepeatTimeout = 3600000;
                    }
                }

                stringBuffer.append("SCNMOD3;");//连续扫描模式
                stringBuffer.append("RRDENA1;");//开启相同条码延时
                stringBuffer.append(String.format("RRDDUR%d;",lScanRepeatTimeout));//相同条码延时
                stringBuffer.append("GRDENA1;");//开启读码成功延迟
                stringBuffer.append(String.format("GRDDUR%d;",lTimeOut));//读码成功延时

               // Log.v(TAG, "initScanSettings ： "+" 连续读码延时: "+lTimeOut+" 相同条码延时: "+lScanRepeatTimeout);
            }else if (iScanMode == 2){//按下读码至超时
                String scanTimeout = scanSettings.get(ScanSettings.Global.SCAN_TIME_OUT);
                if (TextUtils.isDigitsOnly(scanTimeout)){
                    lTimeOut = Long.parseLong(scanTimeout);
                    if (lTimeOut < 0 )lTimeOut = 0;
                    if (lTimeOut > 3600000 )lTimeOut = 3600000;
                }

                stringBuffer.append("SCNMOD4;");//按下读码至超时
                stringBuffer.append(String.format("ORTSET%d;",lTimeOut));//读码超时时间
                stringBuffer.append("GRDENA0;");//关闭读码成功延迟
                //Log.v(TAG, "initScanSettings ： "+" 读码超时时间: "+lTimeOut);
            }else {//触发扫码模式
                String scanTimeout = scanSettings.get(ScanSettings.Global.SCAN_TIME_OUT);
                if (TextUtils.isDigitsOnly(scanTimeout)){
                    lTimeOut = Long.parseLong(scanTimeout);
                    if (lTimeOut < 0 )lTimeOut = 0;
                    if (lTimeOut > 3600000 )lTimeOut = 3600000;
                }

                stringBuffer.append("SCNMOD0;");
                stringBuffer.append(String.format("ORTSET%d;",lTimeOut));//读码超时时间
                stringBuffer.append("GRDENA0;");//关闭读码成功延迟
            }

            //Log.v(TAG, "initScanSettings ： "+stringBuffer.toString());
            mBleController.writeBluetoothDataPacket(mBluetoothGatt,stringBuffer.toString());
        }
    }


    private String mDeviceType = DeviceType.DT_BS30;
	
	/**
     * 蓝牙读写回调
     */
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS){
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    Log.i(TAG,"Connected to GATT server");
                    mConnectionState = STATE_CONNECTED;
                    //连接成功
                    final boolean success = gatt.discoverServices();
                    Log.v(TAG, "Attempting to start service discovery... " + (success ? "succeed" : "failed"));

                    if (!success) {
                        mError = ERROR_MASK | 0x05;
                    } else {
                        Log.v(TAG, "succeed return ! ");
                        // Just return here, lock will be notified when service discovery finishes
                        return;
                    }
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.w(TAG, "Disconnected from GATT server");
                    mConnectionState = STATE_DISCONNECTED;
                }
            }else {
                Log.i(TAG,"onConnectionStateChange error "+status);
                mError = 0x8000 | status;
                if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    mConnectionState = STATE_DISCONNECTED;
                }
                disconnect("connection error "+status);
                LogUtil.saveLog("connection: "+BluetoothUtils.nameForBleState(status) +" "+status+"\n");
            }
            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }


        /**
         * 远程服务发现时，设定获取服务的通道,初始化蓝牙GATT协议
         * @param gatt
         * @param status
         */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mConnectionState = STATE_CONNECTED_AND_READY;
                Log.v(TAG, "Services discovered" );
				
				
				
				
				

                mUartGattService = gatt.getService(UUIDManager.UART_SERVICE_UUID);// 获取到服务的通道
                if (mUartGattService != null) {
                    mBluetoothGatt = gatt;
                    //获取到Notify的Characteristic通道
                    BluetoothGattCharacteristic notifyCharacteristic = mUartGattService.getCharacteristic(UUIDManager.UART_NOTIFY_UUID);
                    //注册Notify通知
                    boolean stat = BluetoothUtils.enableNotification(gatt, true, notifyCharacteristic,UUIDManager.BATTERY_DESCRIPTOR_UUID);
                    Log.v(TAG, "register Uart notification " + stat + " ");
                }
				
				if (mBleController != null){
                    boolean stat = mBleController.readDeviceInformation(gatt);
                    Log.v(TAG, "read model " + stat);
//                    if (!stat){
//                        mError = 0x4010;
//                        noBleDeviceDoDisconnect(gatt);
//                    }
                	
				}

              
            } else {
                mError = 0x4000 | status;
                Log.w(TAG, "onServicesDiscovered fail " + status);
            }
            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }

        /**
         * 处理接收到的数据（蓝牙设备主动发送的数据）
         * @param gatt
         * @param characteristic
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

            if (UUIDManager.UART_NOTIFY_UUID.toString().equals(characteristic.getUuid().toString())) {



                String rawHexString = BluetoothUtils.bytesToHexString(characteristic.getValue());
				
				if ( DeviceType.DT_BS30.equals(mDeviceType)   && mBleController != null) {
				    mBleController.sendScanResult(rawHexString, gatt);//发送数据
                    return;
                }


                long pre = System.currentTimeMillis();
				
                int packIndex = rawHexString.length() > 8 ? Integer.parseInt(rawHexString.substring(6,8),16) : 1;
                if (packIndex > 1 && mLastPackIndex == packIndex)
                    return;
                mLastPackIndex = packIndex;

                if (rawHexString != null && rawHexString.equals(mLastReceiveData))
                    return;


                Log.v(TAG, "onCharacteristicChanged  receivedata: hex: ["+
                        rawHexString +"]");

                String uhfResult = "";
                if (mBleController != null) {
                    if (mBleController.sendResponsePacket(gatt, rawHexString)){//应答
                         uhfResult =  mBleController.sendScanResult(rawHexString);//发送数据
                    }
                }


                Log.d(TAG,"uhf result is " + ((uhfResult != null  && uhfResult.length() >= 8) ? uhfResult.substring(0,8) : "none"));

                if (uhfResult != null ){
                    if ( uhfResult.startsWith("02FE"))
                        solveImuData(uhfResult);
                    else
                        solveUhfData(uhfResult);
                }

                Log.d(TAG,"uhf solve cause " + (System.currentTimeMillis() - pre) + " ms" );

            }else if (UUIDManager.BATTERY_LEVEL_NOTIFY_ADN_READ_UUID.toString().equals(characteristic.getUuid().toString())){
                String rawHexString = BluetoothUtils.bytesToHexString(characteristic.getValue());
                Log.v(TAG, "onCharacteristicChanged battery change: ["+ rawHexString + "]");
                mBleController.handleBatteryChanged(rawHexString);
            }
			
			else {
                Log.v(TAG, "onCharacteristicChanged "+ characteristic.getUuid());
            }
        }

        /**
         * 向蓝牙写入数据后的结果
         * @param gatt
         * @param characteristic
         * @param status
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) { //写出成功
                String rawHexString = BluetoothUtils.bytesToHexString(characteristic.getValue());
//                rawHexString = BluetoothUtils.hexStringToString(rawHexString);
                Log.v(TAG,"onCharacteristicWrite hex: [" + rawHexString +"]");
                //on write finish handle
				mBleController.onCharacteristicWriteFinishHandle(BluetoothUtils.bytesToHexString(characteristic.getValue()));
                if (rawHexString != null && rawHexString.substring(4,6).equals("22"))
                    handleSetParam("FF0422000004000002B76E");

            }else {
                Log.v(TAG,"onCharacteristicWrite failed " + status);
                LogUtil.saveLog("onCharacteristicWrite failed: "+status+"\n");
            }
        }




        /**
         * 调用读取READ通道后返回的数据回调
         * @param gatt
         * @param characteristic
         * @param status
         */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "onCharacteristicRead [" + BluetoothUtils.bytesToHexString(characteristic.getValue()) + "] " + characteristic.getUuid());

                //add by cms for callbakc config 20191227
                if (UUIDManager.BATTERY_LEVEL_NOTIFY_ADN_READ_UUID.toString().equals(characteristic.getUuid().toString())) {
                    //add by cms 20191225
                    mBleController.handleBatteryChanged(BluetoothUtils.bytesToHexString(characteristic.getValue()));
                } else if (UUIDManager.DEVICE_INFORMATION_MODEL_UUID.toString().equals(characteristic.getUuid().toString())) {


                    //mBleController.handleCharacteristicModelRead(BluetoothUtils.bytesToHexString(characteristic.getValue()));
                    byte[] value = characteristic.getValue();
                    if (value != null && value.length > 0) {

                        String deviceInformation = new String(value);
                        Log.d(TAG, "the device information is " + deviceInformation);

                        mDeviceType = deviceInformation;
                        mBleController.setDeviceType(mDeviceType);
                        mDevicesMap.put(mConnectAddress, deviceInformation);
                        mConnectionState = STATE_CONNECTED_AND_READY;
                        mBluetoothGatt = gatt;
                        removeAll(mConnectAddress);

//                        if (DeviceType.DT_BS30.equals(deviceInformation)) {
//                            Log.d(TAG, "read model succeed !  ");
//                            mConnectionState = STATE_CONNECTED_AND_READY;
//                            mBluetoothGatt = gatt;
//                        } else {
//                            mConnectionState = STATE_CLOSED;
//                            noBleDeviceDoDisconnect(gatt);
//                        }
                    } else {
                        mConnectionState = STATE_CLOSED;
                        noBleDeviceDoDisconnect(gatt);
                    }
                    // Notify waiting thread
                    synchronized (mLock) {
                        mLock.notifyAll();
                    }






                } else {
                    Log.e(TAG, "onCharacteristicRead error: " + status);
                }
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            if (ENABLE_TEST) {
                Log.i(TAG, "onReadRemoteRssi rssi: " + rssi);
                Intent intent = new Intent("nlscan.acation.getrssi");
                intent.putExtra("rssi", rssi);
                sendBroadcast(intent);
            }
        }
    };


    /**
     * 处理接收到的UHF数据
     */
    private void solveUhfData(String uhfData){

        if (uhfData.length() < 6){
            if (uhfData.equals("06")){
                mSetStack.add(uhfData);
            }
            return;

        }
        Log.d(TAG,"UHF received data [" + uhfData.substring(0,6) + "]");





        if (uhfData.substring(8,10).equals("29") || uhfData.substring(8,10).equals("AA")){
            mUhfList.add(uhfData.substring(4));//盘点数据
//            try {
//                mUhfCallback.onReceiveUhf(uhfData);//回调盘点结果数据
//            } catch (RemoteException e) {
//                e.printStackTrace();
//            }
        }
        else if (uhfData.substring(8,10).equals("FF") &&
                ("AA".equals(uhfData.substring(12,14))   || "29".equals(uhfData.substring(12,14)) ) )   {
            try {


                int relLen =   Integer.parseInt(uhfData.substring(0,4),16) * 2 -4 ;
                int readLen = 0;
                int index = 4;

                while (readLen < relLen){
                    int subLen = Integer.parseInt(uhfData.substring(index,index+4),16) * 2;
                    String ivnRel = uhfData.substring(index+4,index+4+ subLen);
                    Log.d(TAG,"the sub string is " + ivnRel);
                    mUhfList.add(ivnRel);
                    readLen += subLen;
                    index += 4 + subLen;
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            mSetStack.add(uhfData);


        }

        else{
            if (uhfData.substring(4,6).equals("FF")){
                mSetStack.add(uhfData.substring(4));//设置结果
            }
            else {
                mSetStack.add(uhfData);
            }

        }

    }


    //处理imu数据
    private void solveImuData(String imuData){
        if (imuData.length() <= 16) return;
        mImuList.add(imuData.substring(16));
    }

    /**
     * 出来写入参数后的结果
     */
    private void handleSetParam(String setResult){
        mSetStack.add(setResult);
    }


    /**
     *处理读取到的参数
     */
    private void handleReadParam(String readData){

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG,"service onDestroy");
        if (mReceiver != null)
        unregisterReceiver(mReceiver);
        disconnect("service destroy");
        if (mBleController != null){
            mBleController.release();
            mBleController = null;
        }
    }
    private List<String> filterAddressList = new ArrayList<>();
    private void noBleDeviceDoDisconnect(BluetoothGatt gatt){
        if (!TextUtils.isEmpty(mBluetoothDeviceAddress)){
            if (!filterAddressList.contains(mBluetoothDeviceAddress)){
                filterAddressList.add(mBluetoothDeviceAddress);
            }
        }
        if (gatt != null){
            gatt.disconnect();
        }
        Log.i(TAG,"No NL Device  do disconnect direct !");
    }
    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void disconnect(String logmsg) {
        Log.i(TAG,"[ "+logmsg+" ] do disconnect !");
		mIfConnect = false;
        try {
            mBleStateCallback.onReceiveState(STATE_DISCONNECT);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (mHandler != null){
            mHandler.removeMessages(MSG_WHAT_RSSI);
            mHandler.removeMessages(MSG_WHAT_CHARGE_STATE);
            mHandler.removeMessages(MSG_WHAT_BATTERY);
            mHandler.removeMessages(MSG_WHAT_UPDATE_SCAN_SETTINGS);
            mHandler.removeMessages(MSG_WHAT_ENABLE_BATTERY_NOTIFY);
            mHandler.removeMessages(MSG_WHAT_ENABLE_UART_NOTIFY);
            //mHandler.removeMessages(MSG_WHAT_ENABLE_BT);
        }

        //disconnected
        BluetoothUtils.sendBatteryInfo(this,-10,mDeviceType);
        BluetoothUtils.sendBatteryChargeStateInfo(this,0,mDeviceType);

        //don't send
        if (mBleController != null) {
            mBleController.setUpgradeMode(false);//reset mode
        }
        if (mCurrentACLAddress != null) {
            mCurrentACLAddress = null;
        }
        mBluetoothDeviceAddress = "";
        mConnectionState = STATE_CLOSED;
        LogUtil.saveLog("disconnect " +  (mBluetoothGatt != null));
        if (mBluetoothGatt == null) {
            return;
        }
        try {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }catch (Exception e){}
    }
}
