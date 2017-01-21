package com.company;

/**
 * Created by adam on 20/01/17.
 */
public class AvTracker {
    // Just a stub for now but will be used to encapsulate the mapping of Audio to AV and timestamp
    long startTime;
    public AvTracker() {
        startTime = System.currentTimeMillis();
    }

    public long getAvMilliseconds() {
        return System.currentTimeMillis() - startTime;
    }
}
