package com.nlscan.blecommservice.activity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.Html;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;


import com.nlscan.blecommservice.R;
import com.nlscan.blecommservice.utils.HexUtil;


import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class SearchActivity extends Activity {


    //扫码广播相关
    private static final String ACTION_SCAN_RESULT = "nlscan.action.SCANNER_RESULT";
    private static final String ACTION_SCAN_RESULT_MAIL = "ACTION_BAR_SCAN";

    //
    private static final String TAG = "bleDemo";


    //handler 相关
    private MyHandler gMyHandler = new MyHandler(this);
    private static final int CHANGE_SUCCESS = 1;
    private static final int CHANGE_RETRY = 2;
    private static final int CHANGE_FAIL = 3;
    private static final int CHANGE_FIND = 4;
    private static final int CHANGE_RE_POWER = 5;
    private Timer myTimer = new Timer();
    private static final int TIMEOUT_VAL = 32000;







    //扫码接收广播
    private BroadcastReceiver mScanReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {

            final String scanResult_1=intent.getStringExtra("SCAN_BARCODE1");
            final String scanStatus=intent.getStringExtra("SCAN_STATE");

            if("ok".equals(scanStatus)){
//                connectTarget(scanResult_1);
                obtainAddress(scanResult_1);
            }

        }
    };

    //邮政码接收广播
    private BroadcastReceiver mScanReceiverMail = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            final String scanResult_1=intent.getStringExtra("EXTRA_SCAN_DATA");
            final String scanStatus=intent.getStringExtra("EXTRA_SCAN_STATE");

            if("ok".equals(scanStatus)){
//                connectTarget(scanResult_1);
                obtainAddress(scanResult_1);
            }

        }
    };


    //蓝牙广播
    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    private boolean mSetPin = false;
    private BroadcastReceiver mBleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            BluetoothDevice device =  intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

            String currentAddress = device != null ? device.getAddress() : "";
            String intentAddress = intent.getStringExtra("DeviceAddress");
            final String action = intent.getAction();

            if (device == null) return;
            int connectState = device.getBondState();
//            if (ACTION_GATT_CONNECTED.equals(action)) {
//
//                if (   mDeviceAddress.equals(   intentAddress)){
////                    Toast.makeText(SearchActivity.this,"连接成功" ,Toast.LENGTH_SHORT).show();
////                    jumpTo();
//                     powerOn();
//
//                }
//
//            }
//            else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){
//                if (BluetoothDevice.BOND_BONDED == connectState){
//                    Log.d(TAG,"Bond changed success");
////                    Toast.makeText(SearchActivity.this,"配对成功" , Toast.LENGTH_SHORT).show();
////                    if (mBluetoothLeService != null)  mBluetoothLeService.connect(mDeviceAddress);
//
//                    if (   mDeviceAddress.equals(   currentAddress)  && !"".equals(currentAddress))
//                        bleConnect(mDeviceAddress);
//                }
//                else if (BluetoothDevice.BOND_NONE == connectState){
//                    Log.d(TAG,"refuse bonding");
//                    if (isDialogShow()) gMyHandler.sendEmptyMessage(CHANGE_RETRY);
//                }
//
//            }
          if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                try {
//                    if (mSetPin) return;

                    gMyHandler.removeMessages(CHANGE_FIND);

                    if ( ! mDeviceAddress.equals(   currentAddress)  || "".equals(currentAddress)) return;

                    Log.d(TAG,"no show the window request");
                    int pin=intent.getIntExtra("android.bluetooth.device.extra.PAIRING_KEY", 1234);
                    //the pin in case you need to accept for an specific pin
                    byte[] pinBytes;
                    String strName = device.getName();
                    int length = strName.length();
                    if (length < 7) return;

                    String tailCode = strName.substring(length-7);

                    pinBytes = getPinCode(tailCode).getBytes("UTF-8");
//                    pinBytes = ("123456").getBytes("UTF-8");

                    Log.d(TAG,"the PIN code is " + HexUtil.bytesToHexString(pinBytes));
                    device.setPin(pinBytes);
                    //setPairing confirmation if neeeded
                    boolean rel = device.setPairingConfirmation(true);

                    if (  rel ){
                        mSetPin = true;


//                        new Timer().schedule(new TimerTask() {
//                            @Override
//                            public void run() {
//                                removeAll(mDeviceAddress,false);
//                            }
//                        },1000);

                        powerOn();
                    }
                    abortBroadcast();
                } catch (Exception e) {
                    Log.e(TAG, "Error occurs when trying to auto pair");
                    e.printStackTrace();
                }
            }



        }
    };

    private static String getPinCode(String originCode){

        int length = originCode.length();
        if (length < 7) return "";

        String tailCode = originCode.substring(length-7);


        if (tailCode.substring(1,2).matches("[a-z|A-Z]") ){
            return tailCode.substring(0,1) + tailCode.substring(2);
        }
        else {
            return tailCode.substring(1);
        }



    }



    //蓝牙相关全局变量
    private Button btnScan;
    private TextView textView;
    private ProgressDialog mDialog;
    private Map<String,String> mSerialMac = new HashMap<>();//序列号地址键值对
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothGatt mBluetoothGatt;
    private BluetoothLeScanner mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();


    //蓝牙设备地址
    private String mScanSerial = "";//扫码得到的序列号
    private String mDeviceAddress = "";//寻到的设备地址
    private String mConnectDeviceAddress = "";//建立连接的地址



    //打开蓝牙后的回调
    private static final int FIND_DEVICE = 1;

    //扫码回调
    private static final int FLAG_SCAN_RETURN = 2;

    private static final int REQUEST_CODE_BITMAP = 5;


    //申请权限回调
    public static final int CAMERA_REQ_CODE = 111;



    public static final String DECODE_MODE = "decode_mode";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);



        //DEFINED_CODE为用户自定义，用于接收权限校验结果
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE},
                CAMERA_REQ_CODE);

        initGps();
        initActionBar();
        initBlue();
        initView();
        barcodeSet();



    }


    //实现“onRequestPermissionsResult”函数接收校验权限结果
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        //判断“requestCode”是否为申请权限时设置请求码CAMERA_REQ_CODE，然后校验权限开启状态

        if (permissions == null || grantResults == null) {
            return;
        }

        if (grantResults.length < 2 || grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        //Default View Mode
        if (requestCode == CAMERA_REQ_CODE) {
            //调用扫码接口，构建扫码能力，需您实现

            btnScan.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    readCode();
                }
            });
        }
        //Customized View Mode





    }



    @Override
    protected void onPause() {
        super.onPause();

        unRegister();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        register();

    }

    @Override
    protected void onDestroy() {
        cancelDialog();
        mBluetoothLeScanner.stopScan(mLeScanCallback);
        super.onDestroy();

    }

    /**
     * 返回图标功能
     * @param item
     * @return
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:

                this.finish(); // back button
                return true;
        }
        return super.onOptionsItemSelected(item);
    }


    /**
     * 初始化标题
     */
    private void initActionBar() {
        getActionBar().setDisplayShowHomeEnabled(false);
        getActionBar().setDisplayShowCustomEnabled(true);
        getActionBar().setCustomView(R.layout.action_bar);
        ((TextView) findViewById(R.id.tv_title)).setText(getTitle());

        ImageView leftHome = (ImageView) findViewById(R.id.img_home);
        leftHome.setVisibility(View.VISIBLE);
        leftHome.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }


    //开启GPS
    private void initGps(){
        if (!isOPen(this)){
            new AlertDialog.Builder(SearchActivity.this)
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setTitle("开启定位")
                    .setMessage("开启定位以便搜索BLE蓝牙")
                    .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                            startActivityForResult(intent,887);
                            dialogInterface.dismiss();
                        }
                    })
                    .show();

        }

    }

    //GPS是否开启
    private static final boolean isOPen(final Context context) {
        LocationManager locationManager
                = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        // 通过GPS卫星定位，定位级别可以精确到街（通过24颗卫星定位，在室外和空旷的地方定位准确、速度快）
        boolean gps = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        // 通过WLAN或移动网络(3G/2G)确定的位置（也称作AGPS，辅助GPS定位。主要用于在室内或遮盖物（建筑群或茂密的深林等）密集的地方定位）
        boolean network = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (gps|| network) {
            return true;
        }

        return false;
    }



    //初始化视图
    private void initView(){
        btnScan = (Button)  findViewById(R.id.btn_scan);



        textView = (TextView) findViewById(R.id.text_tip);
        textView.setText(Html.fromHtml( getString(R.string.scan_connect_tips)));
    }

    //设置扫码配置
    private void barcodeSet(){
        Intent intentConfig = new Intent("ACTION_BAR_SCANCFG");
        intentConfig.putExtra("EXTRA_SCAN_MODE", 3);//广播输出
        intentConfig.putExtra("EXTRA_OUTPUT_EDITOR_ACTION_ENABLE", 0);//不输出软键盘
        sendBroadcast(intentConfig);
    }

    //初始化蓝牙搜索
    private void initBlue(){

        if (!mBluetoothAdapter.isEnabled()) {
            // 弹出对话框提示用户是否打开
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, FIND_DEVICE);
        }
        else{
//            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
////            mBluetoothAdapter.startDiscovery();
            mBluetoothLeScanner.startScan(mLeScanCallback);
//            mBluetoothAdapter.startLeScan(mLeScanCallback);

            Log.d(TAG,"start the discovery");
        }

    }




    //解析扫描数据
    private void obtainAddress(String serial){



        if(isDialogShow()) return;



        showLoadingWindow(getString(R.string.scan_connect_dialog));
        myTimer.cancel();
        myTimer = new Timer();
        myTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                cancelDialog();
                gMyHandler.sendEmptyMessage(CHANGE_FAIL);
            }
        },TIMEOUT_VAL);


        mScanSerial = serial.toUpperCase();


        Log.d(TAG,"scan address is " + mScanSerial);

        reFind();


    }


    /**
     * 判断设备是否找到
     */
    private void reFind(){



        String scanAddress = mSerialMac.get(mScanSerial);

        mSerialMac.remove(mScanSerial);

//        mSetPin = true;

        //找到了设备
        if (scanAddress != null   && ! "".equals(scanAddress)){
            connectTarget(scanAddress);
        }
        //未找到设备
        else {



            //当前扫码的地址是否已经连接
            if (ifCurrentConnect(mScanSerial)){
                    powerOn();
            }
            else{
                    reFindCmd();
            }




        }

    }


    //发送重连指令
    private void reFindCmd(){
        if (!isDialogShow()) return;
//                    mBluetoothLeScanner.stopScan(mLeScanCallback);
//            removeAll();
            Log.d(TAG,"remove bond");
//                    mBluetoothLeScanner.startScan(mLeScanCallback);
            gMyHandler.sendEmptyMessageDelayed(CHANGE_FIND,1000);

    }


    //开始尝试上电
    private void powerOn(){

        if(!isDialogShow()) return;
        myTimer.cancel();

        gMyHandler.sendEmptyMessage(CHANGE_SUCCESS);






    }



    /**
     *
     * @param address 目标设备地址
     * @param ifRemove true表示清除改地址，false表示清除改地址以外的所有设备
     */
    private void removeAll(String address,boolean ifRemove){
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : bondedDevices) {
            if (device==null) continue;
            if (ifRemove){
                if ( !device.getAddress().equals(address)) continue;
            }
            else{
                if (device.getAddress().equals(address)) continue;
            }

            try {
                Class btDeviceCls = BluetoothDevice.class;
                Method removeBond = btDeviceCls.getMethod("removeBond");
                if (device !=null && device.getName().startsWith("SR")){
                    removeBond.setAccessible(true);
                    removeBond.invoke(device);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    //判断当前地址的设备是否已经连接
    private boolean ifCurrentConnect(String scanName){
        Set<BluetoothDevice> bondedDevices = mBluetoothAdapter.getBondedDevices();
        for(BluetoothDevice device : bondedDevices) {
            if (device != null){
                String name = device.getName();
                if (name !=null && name.toUpperCase().equals(scanName) && getConnectState(device) ) return  true;
            }
        }

        return false;
    }


    //判断设备是否连接
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








    //连接指定设备
    private void connectTarget(String address){


//        removeAll();


        mDeviceAddress = address;


        reConnect();

    }


    /**
     * 重新连接设备
     */
    private void reConnect(){

        if (mDeviceAddress != null && !"".equals(mDeviceAddress)){
            BluetoothDevice targetDevice = mBluetoothAdapter.getRemoteDevice(mDeviceAddress);
            Log.d(TAG,"device is reconnecting !");




            if ( !targetDevice.createBond()){

                try {
                    Class btDeviceCls = BluetoothDevice.class;
                    Method removeBond = btDeviceCls.getMethod("removeBond");
                    removeBond.setAccessible(true);
                    removeBond.invoke(targetDevice);
                } catch (Exception e) {
                    e.printStackTrace();
                }


                if (isDialogShow()){
                    gMyHandler.sendEmptyMessageDelayed(CHANGE_FIND,1000);
                }
            }
            else{
                if (isDialogShow()){
//                    mSetPin = false;
                    mDialog.setMessage("建立配对中,请确保蓝牙灯处于红色闪烁状态");
                    gMyHandler.sendEmptyMessageDelayed(CHANGE_FIND,10000);

                }
            }
        }


    }




    //连接指定地址
    public boolean bleConnect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if ( mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {

                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, myGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mConnectDeviceAddress = address;

        return true;
    }


    //蓝牙Gatt回调
    private final BluetoothGattCallback myGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d(TAG,"connect success");
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Intent intent = new Intent(ACTION_GATT_CONNECTED);
                intent.putExtra("DeviceAddress",mConnectDeviceAddress);
                sendBroadcast(intent);
            }
        }
    };


    /**
     * 搜索设备回调
     */
    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord scanRecord = result.getScanRecord();

            BluetoothDevice device = result.getDevice();
            if (device != null){
                Log.d(TAG,"the device address " + device.getAddress() );
                Log.d(TAG,"the device name " + device.getName() );
                String name = device.getName();
                if (name != null)
                    mSerialMac.put(device.getName().toUpperCase(),device.getAddress());
            }




        }
    };


    /**
     * 根据广播信息获取厂家信息
     * @param record
     * @return
     */
    private String getManuInfo(String record){
        int recordLen = record.length();
        int beginIndex = 0;

        String resultInfo = "";
        while (beginIndex <= recordLen -4){
            int typeLen = Integer.parseInt(record.substring(beginIndex,beginIndex+2),16) * 2;
            if (typeLen >= 60) break;
            if (  "ff".equals( record.substring(beginIndex+2,beginIndex+4) )){
                resultInfo = record.substring(beginIndex + 4 , beginIndex + 2 + typeLen);
                break;
            }
            beginIndex = beginIndex + 2 + typeLen;
        }

        return resultInfo;
    }




    //跳转到指定页面
    private void jumpTo(){
        mBluetoothLeScanner.stopScan(mLeScanCallback);

        finish();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onActivityResult(int requestCode, int resultCode,  Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode){
            case FIND_DEVICE:

                mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
                mBluetoothLeScanner.startScan(mLeScanCallback);
                break;
            case FLAG_SCAN_RETURN:
                if (data != null) {
                    Bundle bundle = data.getExtras();
                    String scanResult = bundle.getString("result");
                    Log.d(TAG,"the result is" + scanResult);
                    obtainAddress(scanResult);
                }
                break;
            default:
                break;
        }
    }

    /**
     * 关闭进度条
     */
    protected void  cancelDialog(){
        if (mDialog != null){
            mDialog.dismiss();
        }
    }


    private boolean isDialogShow(){
        return mDialog != null && mDialog.isShowing();
    }

    /**
     * 显示进度条
     * @param message
     */
    protected void showLoadingWindow(String message)
    {


        if(isDialogShow())
            return ;

        mDialog = new ProgressDialog(this) ;
        mDialog.setProgressStyle(ProgressDialog.BUTTON_NEUTRAL);// 设置进度条的形式为圆形转动的进度条
        mDialog.setCancelable(true);// 设置是否可以通过点击Back键取消
        mDialog.setCanceledOnTouchOutside(true);// 设置在点击Dialog外是否取消Dialog进度条
        // 设置提示的title的图标，默认是没有的，如果没有设置title的话只设置Icon是不会显示图标的

        mDialog.setMessage(message );

        mDialog.show();

        View v = mDialog.getWindow().getDecorView();
        setDialogText(v);
    }



    //遍历整个View中的textview，然后设置其字体大小
    private void setDialogText(View v){
        if(v instanceof ViewGroup){
            ViewGroup parent=(ViewGroup)v;
            int count=parent.getChildCount();
            for(int i=0;i<count;i++){
                View child=parent.getChildAt(i);
                setDialogText(child);
            }
        }else if(v instanceof TextView){
            ((TextView)v).setTextSize(22);
        }
    }



    //扫码
    private void readCode(){

        mBluetoothLeScanner.startScan(mLeScanCallback);

        //邮政广播
        Intent intent = new Intent("ACTION_BAR_TRIGSCAN");
        intent.putExtra("timeout", 3);//单位为秒，值为int类型，且不超过9秒
        sendBroadcast(intent);

        //普通广播
        Intent intent1 = new Intent("nlscan.action.SCANNER_TRIG");
        intent1.putExtra("SCAN_TIMEOUT", 3);//单位为秒，值为int类型，且不超过9秒
        sendBroadcast(intent1);//content.


        //-------------zxing扫码--------------------
//        Intent intent = new Intent(this, CaptureActivity.class);
//        startActivityForResult(intent, FLAG_SCAN_RETURN);


        //---------------huawei扫码
//        ScanUtil.startScan(this, REQUEST_CODE_SCAN_ONE,
//                new HmsScanAnalyzerOptions.Creator().create());



    }

    //注册广播
    private void register(){
        //蓝牙广播
        IntentFilter blueFilter = new IntentFilter();


        blueFilter.addAction(ACTION_GATT_CONNECTED);

        blueFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        blueFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        blueFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        blueFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);

        registerReceiver(mBleReceiver,blueFilter);

        //扫码广播
        IntentFilter scanFilter = new IntentFilter(ACTION_SCAN_RESULT);
        registerReceiver(mScanReceiver,scanFilter);

        //邮政广播
        IntentFilter scanFilterMail = new IntentFilter(ACTION_SCAN_RESULT_MAIL);
        registerReceiver(mScanReceiverMail,scanFilterMail);


    }


    //取消注册
    private void unRegister(){
        try {
            unregisterReceiver(mBleReceiver);
            unregisterReceiver(mScanReceiver);
            unregisterReceiver(mScanReceiverMail);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }




    /**
     * 静态Handler
     */
    static class MyHandler extends Handler {

        private SoftReference<SearchActivity> mySoftReference;

        public MyHandler(SearchActivity mainActivity) {
            this.mySoftReference = new SoftReference<>(mainActivity);
        }

        @Override
        public void handleMessage(Message msg){
            final SearchActivity mainActivity = mySoftReference.get();
            String str = (String) msg.obj;
            switch (msg.what) {

                case CHANGE_SUCCESS:
                    mainActivity.cancelDialog();
                    Toast.makeText(mainActivity,"连接成功", Toast.LENGTH_SHORT).show();
                    mainActivity.jumpTo();
                    break;
                case CHANGE_RETRY:
//                    mainActivity.cancelDialog();
//                    Toast.makeText(mainActivity,str,Toast.LENGTH_SHORT).show();
//                    Toast.makeText(mainActivity,"重新建立配对中,请按住配对键...", Toast.LENGTH_SHORT).show();
                    mainActivity.mDialog.setMessage("配对失败,请长按读卡器BT键至红灯闪烁...");
                    mainActivity.reConnect();
                    break;
                case CHANGE_FAIL:
//                    Toast.makeText(mainActivity,"连接失败，请再次扫码", Toast.LENGTH_SHORT).show();
                    break;
                case CHANGE_FIND:
//                    Toast.makeText(mainActivity,"重新搜索设备中...", Toast.LENGTH_SHORT).show();
                    mainActivity.mDialog.setMessage("寻找设备中,请确保该设备已开机并且蓝牙灯处于红色闪烁状态");
                    mainActivity.reFind();
                    break;
                case CHANGE_RE_POWER:
                    mainActivity.cancelDialog();
                    Toast.makeText(mainActivity,"上电失败，请重新上电", Toast.LENGTH_SHORT).show();
                    mainActivity.jumpTo();
                    break;
            }

        }
    }






}
