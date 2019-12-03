package com.nlscan.blecommservice;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

public class BleService extends Service{
    private static final String TAG = "BleService";

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private BluetoothGattService gattService;
    private BluetoothGatt mBluetoothGatt;
    private String mBluetoothDeviceAddress;

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
    protected final static int STATE_DISCONNECTING = -4;
    protected final static int STATE_CLOSED = -5;
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (mBluetoothGatt != null && mConnectionState==STATE_CONNECTED){
                mBluetoothGatt.readRemoteRssi();
                mHandler.sendEmptyMessageDelayed(0, 1000);
            }
        }
    };
    private IBleScanCallback mScanCallback;
    private IBleInterface.Stub stub = new IBleInterface.Stub() {
        @Override
        public void setScanCallback(IBleScanCallback callback) throws RemoteException {
            Log.d(TAG, "setScanCallback "+callback);
            mScanCallback = callback;
            if (initialize()) {
                //Auto find ble device to connect
                //first to find
                try {
                    findBleDeviceToConnect();
                }catch (Exception e){
                }

            }
        }

        @Override
        public boolean setScanConfig(String json) throws RemoteException {
            return writeBluetoothData(json);
        }

        @Override
        public String getDeviceInfo(){
            readBluetoothData();
            return null;
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

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);//远程设备已连接，已绑定设备可能未更新
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);//远程设备已断开连接
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);//本机蓝牙连接状态变化
        registerReceiver(mReceiver,intentFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG,"onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //Log.i(TAG, "onReceive: "+ action);
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
                            Log.i(TAG, "ACTION_CONNECTION_STATE_CHANGED  STATE_CONNECTED: " + device.getName() +
                                    " " + device.getAddress() + " boundState: " + device.getBondState());
                            if (device.getBondState() == BluetoothDevice.BOND_BONDED &&  isBLEDevice(device.getAddress()))
                                findBleDeviceToConnect();
                        }else if (newConnState == BluetoothAdapter.STATE_DISCONNECTED) {
                            Log.i(TAG, "ACTION_CONNECTION_STATE_CHANGED  STATE_DISCONNECTED: " + device.getName() +
                                    " " + device.getAddress() + " boundState: " + device.getBondState());
                            if (mBluetoothDeviceAddress != null && mBluetoothDeviceAddress.equals(device.getAddress())){
                                close();
                            }
                        }
                    }

                }else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){//远程已连接，本机可能还未连接上
                    if (device != null) {
                        String name = device.getName();
                        Log.i(TAG, "action_acl_conneted: " + name + " " + device.getAddress()+" boundState: "+device.getBondState());
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED ){
                            //if (isBLEDevice(device.getAddress())) findBleDeviceToConnect(device.getAddress());
                        }
                    }
                }else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
                    if (device != null) {
                        String name = device.getName();
                        Log.i(TAG, "action_acl_disconneted: " + name + " " + device.getAddress());
//                        if (mBluetoothDeviceAddress != null && mBluetoothDeviceAddress.equals(device.getAddress())){
//                            close();
//                        }
                    }
                }
            }catch (Exception e){
            }
        }
    };

    private boolean initialize(){
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    public void findBleDeviceToConnect(){
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            Set<BluetoothDevice> connectedDevicesList = mBluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : connectedDevicesList) {
                boolean connectState = getConnectState(device);
                Log.i(TAG, "getConnectedDevice: " + device.getAddress() + " " + device.getName() + "  连接状态： " + connectState);
                if (connectState){
                    if (isBLEDevice(device.getAddress())) { //PERIPHERAL_KEYBOARD 0x540 外围键盘设备
                        Log.i(TAG, "find connected BLE device: " + device.getAddress()+" Now to Connect");
                        boolean stat = connectDevice(device.getAddress());
                        if (stat && mBluetoothGatt != null){
                            if (isClientCompatible(mBluetoothGatt)){
                                Log.i(TAG, "find succeed!!  break "+device.getAddress() + " " + device.getName());
                                mBluetoothGatt.readRemoteRssi();
                                break;
                            }else {
                                // not scan device , to close.
                                close();
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isBLEDevice(String address){
        if (mBluetoothAdapter != null && address != null) {
            BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(address);
            BluetoothClass bluetoothClass = remoteDevice.getBluetoothClass();
            if (remoteDevice != null
                    && remoteDevice.getType() == BluetoothDevice.DEVICE_TYPE_LE
                    && bluetoothClass != null
                    && bluetoothClass.getMajorDeviceClass() == 1280) { //PERIPHERAL_KEYBOARD 0x540 外围键盘设备
                return true;
            }
        }
        return false;
    }
    private boolean getConnectState(BluetoothDevice device){
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


        BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(address);
        if (remoteDevice == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = remoteDevice.connectGatt(this, false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        Log.d(TAG, "Trying to create a new connection.");



        mBluetoothDeviceAddress = address;
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
                    Log.i(TAG, "Attempting to start service discovery... " + (success ? "succeed" : "failed"));
                    //sendMessage("开始搜索服务");
                    if (!success) {
                        mError = ERROR_MASK | 0x05;
                    } else {
                        // Just return here, lock will be notified when service discovery finishes
                        return;
                    }
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    Log.i(TAG, "Disconnected from GATT server");
                    mConnectionState = STATE_DISCONNECTED;

                    //sendMessage("连接已断开！！！");
                }
            }else {
                mError = 0x8000 | status;
                if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    mConnectionState = STATE_DISCONNECTED;
                }
            }
            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                mConnectionState = STATE_CONNECTED_AND_READY;
                Log.i(TAG, "Services discovered" );
                //sendMessage("连接成功");

                gattService = gatt.getService(UUIDManager.SERVICE_UUID);// 获取到服务的通道
                if (gattService == null) return;
                mBluetoothGatt = gatt;
                //获取到Notify的Characteristic通道
                BluetoothGattCharacteristic notifyCharacteristic = gattService.getCharacteristic(UUIDManager.NOTIFY_UUID);
                //注册Notify通知
                boolean stat = BluetoothUtils.enableNotification(gatt, true, notifyCharacteristic);

                Log.i(TAG, "register notification " + stat + " ");
            } else {
                mError = 0x4000 | status;
                Log.i(TAG, "onServicesDiscovered fail " + status);
            }
            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }
        /**
         *
         * @param gatt
         * @param characteristic
         */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);

            if (UUIDManager.NOTIFY_UUID.toString().equals(characteristic.getUuid().toString())) {
                //如果推送的是十六进制的数据的写法
                byte[] bytes = BluetoothUtils.splitBytes(characteristic.getValue(), 24);

                sendMessage(BluetoothUtils.bytesToHexString(bytes));//回调数据

                Log.d(TAG, " receivedata1: [" + new String(bytes) + "]  hex: ["+BluetoothUtils.bytesToHexString(characteristic.getValue())+"]");

                writeBluetoothData("5DCC000101000001F1D1");//应答

            }else {
                Log.d(TAG, "onCharacteristicChanged "+ characteristic.getUuid());
            }
        }

        /**
         * @param gatt
         * @param characteristic
         * @param status
         */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (status == BluetoothGatt.GATT_SUCCESS) { //写出成功 接下来  该去读取蓝牙设备的数据了
                //这里的READUUID 应该是READ通道的UUID 不过我这边的设备没有用到READ通道  所以我这里就注释了 具体使用 视情况而定
                //BluetoothGattCharacteristic readCharact = gattService.getCharacteristic(UUIDManager.UART_TX_CHARACTERISTIC_UUID);
                //boolean b = gatt.readCharacteristic(readCharact);
                String value = characteristic.getStringValue(0);

                //sendMessage("写入: "+value);//回调数据
                Log.d(TAG,"onCharacteristicWrite hex: [" + BluetoothUtils.bytesToHexString(characteristic.getValue()) +"]  uuid: "+characteristic.getUuid());
            }else {
                Log.d(TAG,"onCharacteristicWrite failed" + status);
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
                String data = characteristic.getStringValue(0); // 将字节转化为String字符串

                 //sendMessage("读取: "+data);//回调数据
                Log.d(TAG, "onCharacteristicRead " + data + " [" +BluetoothUtils.bytesToHexString(characteristic.getValue())+"] " + characteristic.getUuid());

            }else {
                Log.e(TAG,"Characteristic read error: " + status);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            Log.e(TAG,"onReadRemoteRssi read rssi: " +rssi);
        }
    };

    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;
        List<BluetoothGattService> bluetoothGattServices = mBluetoothGatt.getServices();
        Log.d(TAG," initBLE: "+bluetoothGattServices.size()+" ");
        for (BluetoothGattService gattService : bluetoothGattServices) {
            Log.d(TAG," BluetoothGattService "+gattService+" Characteristics.size: "+gattService.getCharacteristics().size()+" uuid: "+gattService.getUuid().toString());
            for (BluetoothGattCharacteristic bluetoothGattCharacteristic : gattService.getCharacteristics()) {
                String uuid = bluetoothGattCharacteristic.getUuid().toString();
                boolean b = mBluetoothGatt.readCharacteristic(bluetoothGattCharacteristic);
                int properties = bluetoothGattCharacteristic.getProperties();
                Log.d(TAG," ===================Characteristic uuid: "+uuid+" "+b+" properties: "+properties+" value: can read: "+((properties
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
     * 向 BLE 终端写入数据
     * @param data 16进制
     * @return
     */
    public boolean writeBluetoothData(String data){
        if (mBluetoothGatt == null || data == null)return false;
        BluetoothGattService service = mBluetoothGatt.getService(UUIDManager.SERVICE_UUID);
        if (service == null)return false;
        BluetoothGattCharacteristic writeCharact = service.getCharacteristic(UUIDManager.UART_TX_CHARACTERISTIC_UUID);
        if (writeCharact == null)return false;
        mBluetoothGatt.setCharacteristicNotification(writeCharact, true); // 设置监听
        // 当数据传递到蓝牙之后
        // 会回调BluetoothGattCallback里面的write方法
        writeCharact.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        byte[] hexBytes = BluetoothUtils.getHexBytes(data);
        writeCharact.setValue(hexBytes);
        return mBluetoothGatt.writeCharacteristic(writeCharact);
    }

    public boolean readBluetoothData(){
        if (mBluetoothGatt == null)return false;
        BluetoothGattService service = mBluetoothGatt.getService(UUIDManager.BATTERY_SERVICE_UUID);
        if (service == null)return false;
        BluetoothGattCharacteristic readCharact = service.getCharacteristic(UUIDManager.BATTERY_LEVEL_UUID);

        mBluetoothGatt.setCharacteristicNotification(readCharact, true); // 设置监听
        if (readCharact == null)return false;

        //getSupportedGattServices();
        return mBluetoothGatt.readCharacteristic(readCharact);
    }

    private void sendMessage(String msg){
        Log.i(TAG, "sendMessage: "+msg+"  "+mScanCallback);
        if (mScanCallback != null){
            try {
                mScanCallback.onReceiveResult(msg);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG,"service onDestroy");
        if (mReceiver != null)
        unregisterReceiver(mReceiver);
        close();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        Log.i(TAG,"disconnect ble , release");
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
