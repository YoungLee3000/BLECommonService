// IBleInterface.aidl
package com.nlscan.blecommservice;

// Declare any non-default types here with import statements
import com.nlscan.blecommservice.IBleScanCallback;
interface IBleInterface {

    void setScanCallback(IBleScanCallback callback);
    boolean setScanConfig(String json);
    String getDeviceInfo();
}
