package com.hifleet.maritime;

public final class SceneRuntimeInfo {
    private final boolean enabled;
    private final String mode;
    private final String runtimePolicyVersion;
    public SceneRuntimeInfo(boolean enabled, String mode, String runtimePolicyVersion) {
        this.enabled = enabled;
        this.mode = mode;
        this.runtimePolicyVersion = runtimePolicyVersion;
    }
    public boolean isEnabled() { return enabled; }
    public String getMode() { return mode; }
    public String getRuntimePolicyVersion() { return runtimePolicyVersion; }
}
