package com.nlscan.blecommservice.activity;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {

    private static final String TAG = "BleService.Client";
    private TextView mTextView,mScanResultText,mScanRawResultText,mShowBattery,mShowRssi,mShowBGRssi,
                    tvCount;

    private Button btnPowerUp,btnPowerDown,btnInv,btnReset,btnClear;

    private RecyclerView rvUhf;
    private List<String> dataList = new ArrayList<>();
    MyRVAdapter myRVAdapter;

    private Handler handler;
    private Handler myHandler;
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
                switch (msg.what){
                    case 1:
                        Toast.makeText(MainActivity.this,"回复出厂成功",Toast.LENGTH_SHORT).show();
                        mScanResultText.setText("回复出厂成功");
                        mScanResultText.setTextColor(Color.GREEN);
                        break;
                    case 2:
                        Toast.makeText(MainActivity.this,"回复出厂失败",Toast.LENGTH_SHORT).show();
                        mScanResultText.setText("回复出厂失败");
                        mScanResultText.setTextColor(Color.RED);
                        break;
                    default:
                        break;
                }
            }
        };
        mTextView = findViewById(R.id.device_info);
        mScanResultText =  findViewById(R.id.scan_result);
        mShowRssi =  findViewById(R.id.show_rssi);
        mShowBGRssi =  findViewById(R.id.show_rssi_bg);
        mScanRawResultText =  findViewById(R.id.scan_result_raw);
        mShowBattery = findViewById(R.id.show_battery);
        tvCount = findViewById(R.id.tv_count);

        btnPowerUp = findViewById(R.id.btn_power_on);
        btnPowerDown = findViewById(R.id.btn_power_down);
        btnInv = findViewById(R.id.btn_ivn);
        btnReset = findViewById(R.id.btn_reset);
        btnClear = findViewById(R.id.btn_clear);


        rvUhf = findViewById(R.id.rv_uhf);
        myRVAdapter = new MyRVAdapter(this,dataList);
        rvUhf.setAdapter(myRVAdapter);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false);
        rvUhf.setLayoutManager(linearLayoutManager);

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dataList.clear();
                myRVAdapter.notifyDataSetChanged();
                tvCount.setText("");
                mScanResultText.setText("");
            }
        });

        btnPowerUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                String sendCommand = "FF00031D0C";
                String resultCode = "failed";

                try {
                    resultCode = mBleInterface.sendUhfCommand(sendCommand);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }

                if ("failed".equals(resultCode) ){
                    Toast.makeText(MainActivity.this,"上电失败",Toast.LENGTH_SHORT).show();
                    mScanResultText.setText("上电失败");
                    mScanResultText.setTextColor(Color.RED);
                    return;
                }


                if (!resultCode.substring(6,10).equals("0000")){
                    Toast.makeText(MainActivity.this,"上电失败",Toast.LENGTH_SHORT).show();
                    mScanResultText.setText("上电失败");
                    mScanResultText.setTextColor(Color.RED);
                }
                else {
                    try {
                        mBleInterface.sendUhfCommand("7EFE001930320000000AFF05220000000032088D08FF032900BF004B22");

                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(MainActivity.this,"上电成功",Toast.LENGTH_SHORT).show();
                    mScanResultText.setText("上电成功");
                    mScanResultText.setTextColor(Color.GREEN);
                }




            }
        });


        btnPowerDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mBleInterface.sendUhfCommand("7EFE00023130");
                    mBleInterface.sendUhfCommand("FF00FC0130");

                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                mScanResultText.setText("下电成功");
                mScanResultText.setTextColor(Color.GREEN);
                Toast.makeText(MainActivity.this,"下电成功",Toast.LENGTH_SHORT).show();
            }
        });

        btnInv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                writeUhfTest("7EFE001930320000000AFF05220000000032088D08FF032900BF004B22");
                writeUhfTest("7EFE00023131");
            }
        });

        btnReset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            mBleInterface.setScanConfig(new IScanConfigCallback.Stub() {
                                @Override
                                public void onConfigCallback(final String str) throws RemoteException {

                                        }}, "@FACDEF");
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                },100);



                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {

                        Log.d(TAG, "interface service init");
                        try {
                            mBleInterface.setScanConfig(new IScanConfigCallback.Stub() {
                                @Override
                                public void onConfigCallback(final String str) throws RemoteException {

                                }}, "@WLSCLP");
                            handler.sendEmptyMessage(1);
                        } catch (RemoteException e) {
                            handler.sendEmptyMessage(2);
                            e.printStackTrace();
                        }

                    }
                },1500);
            }
        });



        mBroadcastReceiver = new Broadcast();
        IntentFilter filter = new IntentFilter("nlscan.acation.getrssi");
        filter.addAction("nlscan.acation.getbgrssi");
        registerReceiver(mBroadcastReceiver,filter);


        myHandler = new Handler();
        myHandler.post(new Runnable() {
            @Override
            public void run()
            {

                if (mBleInterface!=null){
                    String uhfRel = null;
                    try {
                        uhfRel = mBleInterface.getUhfTagData();
                        if (uhfRel!=null && !"".equals(uhfRel)){
                            String[] strList = uhfRel.split(";");
                            for (String str : strList){
                                dataList.add(str);
                                myRVAdapter.notifyDataSetChanged();
                                rvUhf.scrollToPosition(dataList.size()-1);
                            }
                            tvCount.setText(""+dataList.size());
                        }

                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                }


                myHandler.postDelayed(this, 100);
            }
        });


    }


    private void writeUhfTest(String str){
        String writeState = "failed";
        try {
            writeState = mBleInterface.sendUhfCommand(str);

        } catch (RemoteException e) {
            e.printStackTrace();
        }
        if (writeState.equals("failed")){
            mScanResultText.setText("执行失败");
            mScanResultText.setTextColor(Color.RED);
            Toast.makeText(MainActivity.this, "执行失败", Toast.LENGTH_SHORT).show();
        }


        else{
            mScanResultText.setText("执行成功");
            mScanResultText.setTextColor(Color.GREEN);
            Toast.makeText(MainActivity.this, "执行成功", Toast.LENGTH_SHORT).show();
            mScanResultText.setText(writeState);
        }

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
                Log.d(TAG," the blue access " + mBleInterface.isBleAccess());
                String str = ((EditText) findViewById(R.id.editText)).getText().toString();


                if (!TextUtils.isEmpty(str)){

//                    if (!str.startsWith("7EFF")) str = BluetoothUtils.stringtoHex(str);
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
//                    writeState = mBleInterface.sendUhfCommand(str);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            if (writeState == false){
                Toast.makeText(MainActivity.this, "error", Toast.LENGTH_SHORT).show();
            }
            else {
//                mScanResultText.setText(writeState);
            }

        }
    }

    public void onReadClick(View view) {
        sendCommand(Command.CMD_GET_BATTERY_LEVEL);
    }

    public void onTriggerClick(View view) {
        try {


            startActivity(new Intent(MainActivity.this,SearchActivity.class));
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
            final String text = "码制: 手表 [" + codeType + "(" + CodeType.getCodeTypeString(codeType) + ")]  工牌: [ " + CodeType.getCodeTypeFromCommon(codeType) + "(" + CodeType.getCodeTypeString(CodeType.getCodeTypeFromCommon(codeType))+")]\n"+ data;
            handler.removeMessages(0);
            Message obtain = Message.obtain();
            obtain.what = 0;
            obtain.obj = data;
            handler.sendMessageDelayed(obtain,200);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    mScanResultText.setText("");
//                    mTextView.setText(data);
//                    mScanRawResultText.setText(rawHexData);
//                    Toast.makeText(MainActivity.this, ""+text, Toast.LENGTH_SHORT).show();
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
