package com.hifleet.behaviorplatform;

public enum SceneMode {
    OFF(0), SHADOW(1), FILTER(2);
    private final int privilege;
    SceneMode(int privilege) { this.privilege = privilege; }
    int privilege() { return privilege; }
}
