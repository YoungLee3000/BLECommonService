// IBleStateCallback.aidl
package com.nlscan.blecommservice;

// Declare any non-default types here with import statements

interface IBleStateCallback {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void onReceiveState(String state);
}
