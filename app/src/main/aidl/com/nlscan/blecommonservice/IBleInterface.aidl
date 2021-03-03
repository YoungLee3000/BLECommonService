// IBleInterface.aidl
package com.nlscan.blecommonservice;

// Declare any non-default types here with import statements
import com.nlscan.blecommonservice.IUHFCallback;
import com.nlscan.blecommonservice.IBleScanCallback;
import com.nlscan.blecommonservice.IBatteryChangeListener;
import com.nlscan.blecommonservice.IScanConfigCallback;

interface IBleInterface {
    void setScanCallback(IBleScanCallback callback);
    boolean setScanConfig(IScanConfigCallback callback, String str);
    //battery about
    void addBatteryLevelChangeListener(IBatteryChangeListener callback);
    void removeBatteryLevelChangeListener(IBatteryChangeListener callback);
    //find badge ; set badge voice/ enable/dsiable
    void sendCommand(int cmd);

    //send uhf command
    String sendUhfCommand(String command);

    //get uhf tag data
    String getUhfTagData();

    //clear the uhf tag data
    void clearUhfTagData();

    //is the ble avilable
    boolean isBleAccess();

    //set uhf data callback
    oneway void setUhfCallback(IUHFCallback callback);

}
