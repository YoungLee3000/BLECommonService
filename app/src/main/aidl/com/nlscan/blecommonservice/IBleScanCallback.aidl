// IScanCallback.aidl
package com.nlscan.blecommonservice;

// Declare any non-default types here with import statements

interface IBleScanCallback {
    void onReceiveResult(String result,int codeType, String rawHexString);
}
