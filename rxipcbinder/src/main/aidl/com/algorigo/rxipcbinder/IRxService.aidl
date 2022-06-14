// IRxService.aidl
package com.algorigo.rxipcbinder;

import com.algorigo.rxipcbinder.IRxServiceCallback;

// Declare any non-default types here with import statements

interface IRxService {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    void setCallback(int objectId, IRxServiceCallback callback);

    int createRxObject(int objectId, int type, in byte[] values);
    void releaseRxObject(int objectId, int observableId);
    int subscribe(int objectId, int observableId);
    void dispose(int objectId, int observableId);
}