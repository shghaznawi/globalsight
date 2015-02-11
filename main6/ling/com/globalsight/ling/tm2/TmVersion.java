package com.globalsight.ling.tm2;

/**
 * Segment TM version to use for a given company. 
 */
public enum TmVersion {
    UNKNOWN(0), // Sentinel value; should never be used
    TM2(2),
    TM3(3);
    
    private int value;
    
    TmVersion(int value) {
        this.value = value;
    }
    public int getValue() {
        return value;
    }
    public static TmVersion fromValue(int value) {
        for (TmVersion v : values()) {
            if (v.getValue() == value) {
                return v;
            }
        }
        return UNKNOWN;
    }
}