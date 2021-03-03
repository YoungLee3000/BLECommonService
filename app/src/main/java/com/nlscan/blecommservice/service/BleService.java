package com.nlscan.blecommservice.service;

import android.app.Service;
import android.app.TaskInfo;
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
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.nlscan.blecommservice.IUHFCallback;
import com.nlscan.blecommservice.utils.BluetoothUtils;
import com.nlscan.blecommservice.utils.Command;
import com.nlscan.blecommservice.IBatteryChangeListener;
import com.nlscan.blecommservice.IBleInterface;
import com.nlscan.blecommservice.IBleScanCallback;
import com.nlscan.blecommservice.IScanConfigCallback;
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

public class BleService extends Service{
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

    private IUHFCallback mUhfCallback;

    private IBleInterface.Stub stub = new IBleInterface.Stub() {

        @Override
        public void setScanCallback(IBleScanCallback callback)  {
            Log.d(TAG, "setScanCallback "+ callback);
            if (initialize()) {
                if (mBleController != null){
                    mBleController.setScanCallback(callback);
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
                String dataPacket = BluetoothUtils.getWriteDataPacket(BluetoothUtils.isHexString(str)?str:BluetoothUtils.stringtoHex(str));
                if (callback != null)mBleController.setScanConfigOnceCallback(callback);//20191227 return config
                boolean state = mBleController.writeBluetoothData(mBluetoothGatt, dataPacket);
                return state;
            }
            return false;
        }

        @Override
        public void addBatteryLevelChangeListener(IBatteryChangeListener callback) throws RemoteException {
            if (mBleController != null) {
                mBleController.addBatteryListener(callback);
                if (mBleController != null) {
                    mBleController.readBluetoothBattery(mBluetoothGatt);
                    //get charge State
                    mHandler.removeMessages(MSG_WHAT_CHARGE_STATE);
                    mHandler.sendEmptyMessageDelayed(MSG_WHAT_CHARGE_STATE, 2000);
                }
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
                    String dataPacket = BluetoothUtils.getWriteDataPacket(BluetoothUtils.stringtoHex("#BEEPON4000F1000T20V;LEDONS1C1000D"));
                    mBleController.writeBluetoothData(mBluetoothGatt, dataPacket);
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
        public String sendUhfCommand(String command) throws RemoteException {
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
        public String getUhfTagData() throws RemoteException {
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
        public void clearUhfTagData() throws RemoteException {
            mUhfList.clear();
            mImuList.clear();
        }

        @Override
        public boolean isBleAccess() throws RemoteException {
//            return mCurrentACLAddress != null;
            return mIfConnect;
        }


        @Override
        public void setUhfCallback(IUHFCallback callback){
            mUhfCallback = callback;
        }



    };
    private Handler mHandler;

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

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);//远程设备已连接，已绑定设备可能未更新
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);//远程设备已断开连接
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);//绑定状态发送改变
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);//本机蓝牙连接状态变化
        //intentFilter.addAction("nlscan.bluetooth.action.CONNECTION_STATE_CHANGED");//custom
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);//蓝牙状态变化

        mHandler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == MSG_WHAT_BATTERY){
                    if (mBleController != null) {
                        mBleController.readBluetoothBattery(mBluetoothGatt);
                    }
                }else if (msg.what == MSG_WHAT_CHARGE_STATE){
                    if (mBleController != null) {
                        //get charge state
                        mBleController.getChargeState(mBluetoothGatt);
                    }

                }else if (msg.what == MSG_WHAT_DEVICE_GET){
                    if (mBleController != null) {
                        //get charge state
                        mBleController.readDeviceInformation(mBluetoothGatt);
                    }

                }
                else if (msg.what == MSG_WHAT_ENABLE_BT) {
                    if (mBluetoothAdapter != null) {
                        mBluetoothAdapter.enable();
                    }
                }else if (msg.what == MSG_WHAT_RSSI){
                    if (ENABLE_TEST) {
                        Log.i(TAG, "handleMessage read rssi " + mBluetoothGatt);

                        if (mBluetoothGatt != null) {
                            boolean result = mBluetoothGatt.readRemoteRssi();
                            if (result) {
                                sendEmptyMessageDelayed(MSG_WHAT_RSSI, 3000);

                                String packet = BluetoothUtils.getWriteDataPacket(BluetoothUtils.stringtoHex("@WLSRSS"));
                                Log.i(TAG, "handleMessage read bg packet:  " + packet);
                                mBleController.writeBluetoothData(mBluetoothGatt, packet);
                            }
                        }
                    }
                }

            }
        };
        registerReceiver(mReceiver,intentFilter);

        //for test , 20200214
        if (initialize()){
            try {
                findBleDeviceToConnect(null);
            }catch (Exception e){
            }
        }
    }


    /**
     * 监听蓝牙连接状态
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            try {
                Parcelable parcelable = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                BluetoothDevice device = null;
                if (parcelable != null && parcelable instanceof BluetoothDevice) {
                    device = (BluetoothDevice) parcelable;
                }
                if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                    int newConnState = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, 0);
                    if (device != null) {
                        if (newConnState == BluetoothAdapter.STATE_CONNECTED) {
                            /*Log.i(TAG, "ACTION_CONNECTION_STATE_CHANGED  STATE_CONNECTED: " + device.getName() +
                                    " " + device.getAddress() + " boundState: " + device.getBondState());
                            if (device.getBondState() == BluetoothDevice.BOND_BONDED &&  isBLEDevice(device.getAddress())) {
                                //findBleDeviceToConnect(device.getAddress());
                            }*/
//                            mIfConnect = true;
                        }else if (newConnState == BluetoothAdapter.STATE_DISCONNECTED) {
                            Log.i(TAG, "ACTION_CONNECTION_STATE_CHANGED  STATE_DISCONNECTED: " + device.getName() +
                                    " " + device.getAddress() + " boundState: " + device.getBondState());
                            if (mBluetoothDeviceAddress != null && mBluetoothDeviceAddress.equals(device.getAddress())){
                                if (device.getBondState() == BluetoothDevice.BOND_BONDED){
                                    //no to disconnect
                                }else {
                                    disconnect();
                                }
                            }

                            Log.d(TAG,"ble bluetooth disconnect");
//                            mIfConnect = false;
                        }
                    }

                }else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){//远程已连接，本机可能还未连接上
                    if (device != null) {
                        String name = device.getName();
                        Log.i(TAG, "action_acl_conneted: [" + name + " " + device.getAddress()+"] boundState: "+device.getBondState());

                        if (device.getBondState() == BluetoothDevice.BOND_BONDED || device.getBondState() == BluetoothDevice.BOND_BONDING){//  本机蓝牙可能还在绑定中， BOND_BONDING
                            synchronized (this){
                                boolean bleDevice = BluetoothUtils.isBLEDevice(device.getAddress(),BleService.this);
                                Log.i(TAG, "start enter to find Bounded device. "+bleDevice);
                                if (bleDevice) {
                                    findBleDeviceToConnect(device.getAddress());
                                }
                            }
                        }
                    }
                }else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){//在多设备和充电的情况下，不会调用 disconnect
                    if (device != null) {
                        String name = device.getName();
                        Log.i(TAG, "action_acl_disconneted: " + name + " " + device.getAddress()+" "+device.getBondState());
                        if (mBluetoothDeviceAddress != null && mBluetoothDeviceAddress.equals(device.getAddress())){
                            if (device.getBondState() == BluetoothDevice.BOND_BONDED){
                                //no to disconnect, because less to use settings do disconnect.
                            }else {
                                disconnect();
                            }
                        }
                        if (mCurrentACLAddress != null && mCurrentACLAddress.equals(device.getAddress())){
                            mCurrentACLAddress = null;
                        }
                    }
                }else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){//绑定状态变化
                    if (device != null) {
                        int previewBoundState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, 0);
                        String name = device.getName();
                        Log.i(TAG, "ACTION_BOND_STATE_CHANGED: " + name + " " + device.getAddress()+" boundState: "+device.getBondState()+" preview: "+previewBoundState+" "+mCurrentACLAddress);
                        if (((mCurrentACLAddress != null && mCurrentACLAddress.equals(device.getAddress()))
                                || (mCurrentACLAddress == null && (mConnectionState!= STATE_CONNECTED && mConnectionState!= STATE_CONNECTED_AND_READY)))
                                && device.getBondState()==BluetoothDevice.BOND_BONDED && previewBoundState == BluetoothDevice.BOND_BONDING){
                            findBleDeviceToConnect(device.getAddress());
                        }
                    }
                }/*else if ("nlscan.bluetooth.action.CONNECTION_STATE_CHANGED".equals(action)){
                    int newConnState = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, 0);
                    if (device != null) {
                        if (newConnState == BluetoothAdapter.STATE_CONNECTED) {
                            //Log.i(TAG, "NLSCAN CONNECTION_STATE_CHANGED   STATE_CONNECTED " + device.getName() + " " + device.getAddress());

                        }else if (newConnState == BluetoothAdapter.STATE_DISCONNECTED) {
                            Log.i(TAG, "NLSCAN CONNECTION_STATE_CHANGED   STATE_DISCONNECTED " + device.getName() + " " + device.getAddress() );
                            if (mBluetoothDeviceAddress != null && mBluetoothDeviceAddress.equals(device.getAddress())){
                                disconnect();
                            }
                        }
                    }
                }*/else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                    //if (device != null){
                        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                        Log.i(TAG, "ACTION_STATE_CHANGED   STATE " + state+" mConnectionState: "+mConnectionState);
                        if (state == BluetoothAdapter.STATE_OFF && (mConnectionState == STATE_CONNECTED||mConnectionState == STATE_CONNECTED_AND_READY)){
                            disconnect();
                        }
                        //快速开关蓝牙
                        if (state == BluetoothAdapter.STATE_ON && mConnectionState == STATE_CLOSED){
                            findBleDeviceToConnect(null);
                        }
                    //}
                }
            }catch (Exception e){
            }
        }
    };


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
        }
        return true;
    }

    /**
     *  find BLE device
     */
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

                if (BluetoothUtils.isBLEDevice(device.getAddress(), BleService.this)
                        && (address == null || (address != null && device.getAddress().equals(address)))) { //PERIPHERAL_KEYBOARD 0x540 外围键盘设备
                    boolean connectState = BluetoothUtils.getConnectState(device);
                    Log.i(TAG, "getConnectedDevice: " + device.getAddress() + " " + device.getName() + "  state： " + connectState + " address: " + address);
                    if (connectState) {
                        Log.i(TAG, "find connected BLE device: " + device.getAddress() + " Now to Connect " + mBluetoothGatt);
                        disconnect();//disconnect first  modified 2020623
                        //mHandler.postDelayed(new Runnable() {
                        //    @Override
                        //   public void run() {
                        Log.i(TAG, "start connect device");
                        boolean stat = connectDevice(device.getAddress());
                        if (stat && mBluetoothGatt != null) {
                            if (mBleController != null && mBleController.isClientCompatible(mBluetoothGatt)) {
                                foundDevice = true;
                                Log.i(TAG, "Connected to LE succeed  [" + device.getAddress() + " " + device.getName() + "]");
                                mIfConnect = true;
                                mConnectAddress = device.getAddress();
                                //connect succeed , get battery info

//                                boolean rel = mBleController.readDeviceInformation(mBluetoothGatt);
//
//                                Log.d(TAG,"the read infor result is " + rel);
                                getBatteryInfo();

                            } else {
                                // not scan device , to disconnect.
                                disconnect();
                            }
                        } else if (!stat && mBluetoothGatt == null) {
                            Log.i(TAG, "connectGatt fail , reOpen bt   [" + device.getAddress() + " " + device.getName() + "]");
                            //findBleDeviceToConnect(address);
                            //2020628 , add for DeadObjectException , reOpen BT
                            mBluetoothAdapter.disable();//disable bt first .
                            mHandler.removeMessages(MSG_WHAT_ENABLE_BT);
                            mHandler.sendEmptyMessageDelayed(MSG_WHAT_ENABLE_BT, 50);//enable.
                        }
                        //Log.i(TAG, "end connect device");
                        //}
                        //},200);
                    }
                    //Log.i(TAG, "return");
                    return;
                }
            }
            if (address != null && !foundDevice){
                mCurrentACLAddress = address;
            }
        }

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
                if (deviceInforStr.equals(tempInfor)){
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
    private void getBatteryInfo(){
        //send connected info.2020611
        BluetoothUtils.sendBatteryInfo(this,-1);

        //get battery value
        if (mHandler != null){
            if (!ENABLE_TEST) {//now connected , bluetooth will send battery info , 20200430
                mHandler.removeMessages(MSG_WHAT_BATTERY);
                mHandler.sendEmptyMessageDelayed(MSG_WHAT_BATTERY, 1200);
                Log.d(TAG,"read the device information2");

                //测试工具时去除
                mHandler.removeMessages(MSG_WHAT_DEVICE_GET);
                mHandler.sendEmptyMessageDelayed(MSG_WHAT_DEVICE_GET, 800);

                mHandler.removeMessages(MSG_WHAT_DEVICE_GET,1200);
                mHandler.sendEmptyMessageDelayed(MSG_WHAT_DEVICE_GET, 1600);
                //测试工具时去除
            }
            mHandler.removeMessages(MSG_WHAT_CHARGE_STATE);
            mHandler.sendEmptyMessageDelayed(MSG_WHAT_CHARGE_STATE, 2000);
        }
        if (ENABLE_TEST){
            //get rssi
            if (mHandler != null){
                mHandler.removeMessages(MSG_WHAT_RSSI);
                mHandler.sendEmptyMessageDelayed(MSG_WHAT_RSSI, 1000);
            }
        }
    }


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
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        //保证只能连接接收一个工牌的数据
        if (mBluetoothDeviceAddress != null && !address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null){
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
        mBluetoothGatt = remoteDevice.connectGatt(this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        Log.v(TAG, "Trying to create a new connection.");

                       mBluetoothDeviceAddress = address;
        mLastConnectedDeviceAddress = mBluetoothDeviceAddress;
        mError = 0;
        mConnectionState = STATE_CONNECTING;

        try {
            synchronized (mLock) {
                while ((mConnectionState == STATE_CONNECTING || mConnectionState == STATE_CONNECTED) && mError == 0)
                    mLock.wait();
            }
        } catch (final InterruptedException e) {
            Log.e(TAG, "Sleeping interrupted", e);
        }
        return true;
    }


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
                disconnect();
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

                mUartGattService = gatt.getService(UUIDManager.SERVICE_UUID);// 获取到服务的通道
                if (mUartGattService != null) {
                    mBluetoothGatt = gatt;
                    //获取到Notify的Characteristic通道
                    BluetoothGattCharacteristic notifyCharacteristic = mUartGattService.getCharacteristic(UUIDManager.NOTIFY_UUID);
                    //注册Notify通知
                    boolean stat = BluetoothUtils.enableNotification(gatt, true, notifyCharacteristic);
                    Log.v(TAG, "register Uart notification " + stat + " ");
                }

                // modified for battery notify in uart , 20191227
                /*BluetoothGattService batteryServiceGatt = gatt.getService(UUIDManager.BATTERY_SERVICE_UUID);// 获取到服务的通道
                if (batteryServiceGatt != null) {
                    //获取到Notify的Characteristic通道
                    BluetoothGattCharacteristic notifyCharacteristic = batteryServiceGatt.getCharacteristic(UUIDManager.BATTERY_LEVEL_UUID);
                    //注册Notify通知
                    boolean stat = BluetoothUtils.enableNotification(gatt, true, notifyCharacteristic);
                    Log.i(TAG, "register Battery notification " + stat + " ");
                }*/
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

            if (UUIDManager.NOTIFY_UUID.toString().equals(characteristic.getUuid().toString())) {


                long pre = System.currentTimeMillis();
                String rawHexString = BluetoothUtils.bytesToHexString(characteristic.getValue());
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

            }else {
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

                if (rawHexString != null && rawHexString.substring(4,6).equals("22"))
                    handleSetParam("FF0422000004000002B76E");

            }else {
                Log.v(TAG,"onCharacteristicWrite failed " + status);
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
                Log.d(TAG, "onCharacteristicRead [" +BluetoothUtils.bytesToHexString(characteristic.getValue())+"] " + characteristic.getUuid());

                //add by cms for callbakc config 20191227
                if (UUIDManager.BATTERY_LEVEL_UUID.toString().equals(characteristic.getUuid().toString())) {
                    //add by cms 20191225
                    mBleController.handleCharacteristicRead(BluetoothUtils.bytesToHexString(characteristic.getValue()));
                }
                else if (UUIDManager.DEVICE_INFORMATION_MODEL_UUID.toString().equals(characteristic.getUuid().toString())){
                    String deviceInformation = BluetoothUtils.bytesToHexString(characteristic.getValue());
                    Log.d(TAG,"the device information is " + deviceInformation);
                    String deviceInforStr = BluetoothUtils.hexStringToString(deviceInformation);
                    Log.d(TAG,"the device information str is " + deviceInforStr);
                    mDevicesMap.put(mConnectAddress,deviceInforStr);

                    removeAll(mConnectAddress);
                }
            }else {
                Log.e(TAG,"onCharacteristicRead error: " + status);
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


                int relLen =   Integer.parseInt(uhfData.substring(0,4),16) * 2 ;
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
        disconnect();
        if (mBleController != null){
            mBleController.release();
            mBleController = null;
        }
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void disconnect() {
        mIfConnect = false;
        Log.i(TAG,"disconnect ble , release");
        if (mHandler != null){
            mHandler.removeMessages(MSG_WHAT_RSSI);
            mHandler.removeMessages(MSG_WHAT_CHARGE_STATE);
            //mHandler.removeMessages(MSG_WHAT_BATTERY);
        }

        //disconnected
        BluetoothUtils.sendBatteryInfo(this,-10);
        BluetoothUtils.sendBatteryChargeStateInfo(this,0);

        //don't send
        if (mBleController != null) {
            //mBleController.handleCharacteristicRead("00");
        }
        if (mCurrentACLAddress != null) {
            mCurrentACLAddress = null;
        }
        mBluetoothDeviceAddress = "";
        mConnectionState = STATE_CLOSED;
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.disconnect();
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }
}
