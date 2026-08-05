package com.hifleet.behaviorplatform;

public enum WriteMode {
    NONE(0), FILE_AUDIT(1), SHADOW_DB(2), FORMAL_DB(3);
    private final int privilege;
    WriteMode(int privilege) { this.privilege = privilege; }
    int privilege() { return privilege; }
}
