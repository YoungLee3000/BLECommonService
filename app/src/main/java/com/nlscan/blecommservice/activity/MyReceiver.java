package com.nlscan.blecommservice.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.nlscan.blecommservice.utils.LogUtil;

public class MyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO: This method is called when the BroadcastReceiver is receiving
        // an Intent broadcast.
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)){
            LogUtil.saveLog("手表开机");
        }else if (Intent.ACTION_SHUTDOWN.equals(action)){
            LogUtil.saveLog("手表关机");
        }

    }
}
