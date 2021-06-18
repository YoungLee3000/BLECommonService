package com.nlscan.blecommservice.service;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;

import com.nlscan.blecommservice.utils.BluetoothUtils;
import com.nlscan.blecommservice.utils.LogUtil;

public class BleReceiver extends BroadcastReceiver{
    private static final String TAG = "NlsBleReceiver";
    private final Handler mHandler;
    private final Context mContext;

    public BleReceiver(Context context, Handler handler) {
        this.mHandler = handler;
        this.mContext = context;

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);//远程设备已连接，已绑定设备可能未更新
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);//远程设备已断开连接
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);//绑定状态发送改变
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);//本机蓝牙连接状态变化
        //intentFilter.addAction("nlscan.bluetooth.action.CONNECTION_STATE_CHANGED");//custom
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);//蓝牙状态变化

        intentFilter.addAction("nlscan.action.update.scan.settings");
        context.registerReceiver(this, intentFilter);
    }


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
                    }else if (newConnState == BluetoothAdapter.STATE_DISCONNECTED) {
                        Log.i(TAG, "ACTION_CONNECTION_STATE_CHANGED  STATE_DISCONNECTED: " + device.getName() +
                                " " + device.getAddress() + " boundState: " + device.getBondState());
                        //if (mBluetoothDeviceAddress != null && mBluetoothDeviceAddress.equals(device.getAddress())){
                            if (device.getBondState() == BluetoothDevice.BOND_BONDED){
                                //no to disconnect
                            }else {
                                //disconnect(); //modified 20200623
                            }
                        //}
                    }
                }

            }else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){//远程已连接，本机可能还未连接上
                if (device != null) {
                    String name = device.getName();
                    Log.i(TAG, "action_acl_conneted: [" + name + " " + device.getAddress()+"] boundState: "+device.getBondState());

                    if (device.getBondState() == BluetoothDevice.BOND_BONDED || device.getBondState() == BluetoothDevice.BOND_BONDING){//  本机蓝牙可能还在绑定中， BOND_BONDING
                        //if (device.getBondState() == BluetoothDevice.BOND_BONDED){
                        synchronized (this){
                            boolean bleDevice = BluetoothUtils.isBLEDevice(device.getAddress(),mContext);
                            Log.i(TAG, "start enter to find Bounded device. "+bleDevice);
                            if (bleDevice) {
                                Message msg = Message.obtain();
                                msg.what = BleService.MSG_WHAT_BT_ACL_CONNECTED;
                                msg.obj = device.getAddress();
                                mHandler.sendMessage(msg);
                            }
                        }
                    }
                }
            }else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){//在多设备和充电的情况下，不会调用 disconnect
                if (device != null) {
                    String name = device.getName();
                    Log.i(TAG, "action_acl_disconneted: " + name + " " + device.getAddress()+" "+device.getBondState());
                    Message msg = Message.obtain();
                    msg.what = BleService.MSG_WHAT_BT_ACL_DISCONNECTED;
                    msg.obj = device.getAddress();
                    msg.arg1 = device.getBondState();
                    mHandler.sendMessage(msg);
                }
            }else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)){//绑定状态变化
                if (device != null) {
                    int previewBoundState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, 0);
                    String name = device.getName();

                    Log.i(TAG, "ACTION_BOND_STATE_CHANGED: " + name + " " + device.getAddress());
                    Message msg = Message.obtain();
                    msg.what = BleService.MSG_WHAT_BT_BOND_STATE_CHANGED;
                    msg.obj = device.getAddress();
                    msg.arg1 = previewBoundState;
                    msg.arg2 = device.getBondState();
                    mHandler.sendMessage(msg);
                }
            }
            /*else if ("nlscan.bluetooth.action.CONNECTION_STATE_CHANGED".equals(action)){
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
            }*/
            else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0);
                Message msg = Message.obtain();
                msg.what = BleService.MSG_WHAT_BT_STATE_CHANGED;
                msg.arg1 = state;
                mHandler.sendMessage(msg);
                LogUtil.saveLog("BT_STATE_CHANGED "+BluetoothUtils.nameForState(state));
            }else if ("nlscan.action.update.scan.settings".equals(action)){
                Message msg = Message.obtain();
                msg.what = BleService.MSG_WHAT_UPDATE_SCAN_SETTINGS;
                mHandler.sendMessage(msg);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
