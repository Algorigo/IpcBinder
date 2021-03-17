// IRxServiceCallback.aidl
package com.algorigo.rxipcbinder;

// Declare any non-default types here with import statements

interface IRxServiceCallback {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void callbackMessage(int subscribeId, int message, String classes, in byte[] data);
}