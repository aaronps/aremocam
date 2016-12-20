package com.aaronps.aremocam;

/**
 * Created by krom on 12/19/16.
 */

public interface BufferManager {
    byte[] get();
    void release(byte[] buffer);
}
