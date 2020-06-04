// IBleInterface.aidl
package com.nlscan.blecommservice;

// Declare any non-default types here with import statements
import com.nlscan.blecommservice.IBleScanCallback;
import com.nlscan.blecommservice.IBatteryChangeListener;
import com.nlscan.blecommservice.IScanConfigCallback;

interface IBleInterface {
    void setScanCallback(IBleScanCallback callback);
    boolean setScanConfig(IScanConfigCallback callback, String str);
    //battery about
    void addBatteryLevelChangeListener(IBatteryChangeListener callback);
    void removeBatteryLevelChangeListener(IBatteryChangeListener callback);
    //find badge ; set badge voice/ enable/dsiable
    void sendCommand(int cmd);

    //set uhf param
    int setUhfParam(String key,String value);

    //get uhf param
    String getUhfParam(String key);

    //get uhf data
    String [] getUhfData();

    //is the ble avilable
    boolean isBleAccess();
}
