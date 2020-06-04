package com.nlscan.blecommservice.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.nlscan.blecommservice.utils.Command;
import com.nlscan.blecommservice.IBatteryChangeListener;
import com.nlscan.blecommservice.IBleInterface;
import com.nlscan.blecommservice.IBleScanCallback;
import com.nlscan.blecommservice.IScanConfigCallback;
import com.nlscan.blecommservice.R;
import com.nlscan.blecommservice.utils.BluetoothUtils;
import com.nlscan.blecommservice.utils.CodeType;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity {

    private static final String TAG = "BleService.Client";
    private TextView mTextView,mScanResultText,mScanRawResultText,mShowBattery,mShowRssi,mShowBGRssi;
    private Handler handler;
    private BleServiceConnection mConnection = null;
    private BroadcastReceiver mBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "onCreate: ====================!!");

        Intent service = new Intent("android.nlscan.intent.action.START_BLE_SERVICE");
        service.setPackage("com.nlscan.blecommservice");
        mConnection = new BleServiceConnection();
        bindService(service,mConnection, Context.BIND_AUTO_CREATE);


        handler = new Handler(){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                mScanResultText.setText(((String) msg.obj));
            }
        };
        mTextView = findViewById(R.id.device_info);
        mScanResultText =  findViewById(R.id.scan_result);
        mShowRssi =  findViewById(R.id.show_rssi);
        mShowBGRssi =  findViewById(R.id.show_rssi_bg);
        mScanRawResultText =  findViewById(R.id.scan_result_raw);
        mShowBattery = findViewById(R.id.show_battery);

        mBroadcastReceiver = new Broadcast();
        IntentFilter filter = new IntentFilter("nlscan.acation.getrssi");
        filter.addAction("nlscan.acation.getbgrssi");
        registerReceiver(mBroadcastReceiver,filter);
    }

    class Broadcast extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("nlscan.acation.getbgrssi")){
                String rssi = intent.getStringExtra("rssi");
                mShowBGRssi.setText("BG: "+rssi);
            }else {
                int rssi = intent.getIntExtra("rssi", -1);
                mShowRssi.setText("PDA: "+rssi);
            }

        }
    }
    private IBleInterface mBleInterface;

    private class  BleServiceConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleInterface = IBleInterface.Stub.asInterface(service);
            try {
                mBleInterface.setScanCallback(scanCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
                Log.d(TAG, "onServiceConnected"+e.getMessage());
            }

            if (mBleInterface != null && !addBatteryLevelListener){
                addBatteryLevelListener = true;
                try {
                    mBleInterface.addBatteryLevelChangeListener(batteryChangeListener);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            Log.d(TAG, "onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBleInterface = null;
        }
    };

    private IBleScanCallback.Stub scanCallback = new IBleScanCallback.Stub() {
        @Override
        public void onReceiveResult(String result, int codeType, String rawHexData) throws RemoteException {
            onHandleMessage(result, codeType, rawHexData);
        }
    };

    IBatteryChangeListener.Stub batteryChangeListener = new IBatteryChangeListener.Stub() {
        @Override
        public void onBatteryChangeListener(final int level) throws RemoteException {
            //Log.i(TAG,"onBatteryChangeListener  "+" "+ level);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mShowBattery.setText("电量: "+level+"%");
                    //Toast.makeText(MainActivity.this, ""+level, Toast.LENGTH_SHORT).show();
                }
            });
        }
    };

    private boolean addBatteryLevelListener =false;
    @Override
    protected void onResume() {
        super.onResume();
        if (mBleInterface != null && !addBatteryLevelListener){
            addBatteryLevelListener = true;
            try {
                mBleInterface.addBatteryLevelChangeListener(batteryChangeListener);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBleInterface != null  && addBatteryLevelListener){
            try {
                addBatteryLevelListener = false;
                mBleInterface.removeBatteryLevelChangeListener(batteryChangeListener);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void onWriteClick(View view) {
        if (mBleInterface != null) {
            boolean writeState = false;
            try {
                String str = ((EditText) findViewById(R.id.editText)).getText().toString();

                if (!TextUtils.isEmpty(str)){
                    writeState = mBleInterface.setScanConfig(new IScanConfigCallback.Stub() {
                        @Override
                        public void onConfigCallback(final String str) throws RemoteException {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(MainActivity.this, ""+str, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }, str);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (!writeState){
                Toast.makeText(MainActivity.this, "error", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void onReadClick(View view) {
        sendCommand(Command.CMD_GET_BATTERY_LEVEL);
    }

    public void onTriggerClick(View view) {
        try {

            Intent intent = new Intent("nlscan.intent.action.startAutoConnect");
            intent.putExtra("EnterScan",true);
            startActivity(intent);
        }catch (Exception e){};
    }

    public void onContinueClick(View view) {
        if (mBleInterface != null) {
            try {
                //mBleInterface.setScanConfig(null, "@SCNMOD3;GRVENA1;GRBENA1");
                //mBleInterface.setScanConfig(scanConfigCallback, "@SCNMOD*");
                mBleInterface.setScanConfig(scanConfigCallback, "@GRVENA*;SCNMOD*;GRBENA*");
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }
    }

    private IScanConfigCallback.Stub scanConfigCallback = new IScanConfigCallback.Stub() {
        @Override
        public void onConfigCallback(String str) throws RemoteException {
            Log.i(TAG, "onConfigCallback  " + str);
        }
    };

    private void sendCommand(int cmd){
        if (mBleInterface != null){
            try {
                mBleInterface.sendCommand(cmd);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    // TaskStart:686:R0A_POFFHIS=10 CPM_RSR=00000001
    // TaskStart:707:At=8340 Power up
    public void onHandleMessage(final String data, final int codeType, final String rawHexData) {
        Log.i(TAG, Thread.currentThread()+"  onReceiverdata: "+data+" codeType: "+codeType+" "+rawHexData);
        if (data != null){
            final String text = "码制: 手表 [" + codeType + "(" + CodeType.getCodeTypeString(codeType) + ")]  工牌: [ " + CodeType.getCodeTypeFromCommon(codeType) + "(" + CodeType.getCodeTypeString(CodeType.getCodeTypeFromCommon(codeType))+")]\n"+ BluetoothUtils.fromHexString(data);
            handler.removeMessages(0);
            Message obtain = Message.obtain();
            obtain.what = 0;
            obtain.obj = text;
            handler.sendMessageDelayed(obtain,200);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    mScanResultText.setText("");
                    mTextView.setText(data);
                    mScanRawResultText.setText(rawHexData);
                    Toast.makeText(MainActivity.this, ""+text, Toast.LENGTH_SHORT).show();
                    File file = new File("/sdcard/scandata.txt");
                    try {
                        if (!file.exists())file.createNewFile();
                        // 打开一个写文件器，构造函数中的第二个参数true表示以追加形式写文件
                        FileWriter writer = new FileWriter(file, true);
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd-hh:mm:ss:SS");
                        Date date = new Date(System.currentTimeMillis());
                        String time = simpleDateFormat.format(date);
                        String content = time+"   码制："+CodeType.getCodeTypeFromCommon(codeType)+" 扫描结果： "+BluetoothUtils.fromHexString(data)+"\n";
                        writer.write(content);
                        writer.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy");
        unregisterReceiver(mBroadcastReceiver);
        //mBleBinder.close();
        //unregisterReceiver(mReceiver);
        if (mBleInterface != null) {
            try {
                mBleInterface.setScanCallback(null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        if (mConnection != null)
            unbindService(mConnection);
    }
}
