package com.hifleet.behaviorplatform;

public final class BehaviorPermissionResolver {
    private BehaviorPermissionResolver() { }
    public static SceneMode resolveScene(SceneMode profile, SceneMode override) {
        if (profile == null) throw new IllegalArgumentException("profile scene mode is required");
        if (override == null) return profile;
        if (override.privilege() > profile.privilege())
            throw new IllegalArgumentException("task scene override cannot upgrade profile permission");
        return override;
    }
    public static WriteMode resolveWrite(WriteMode profile, WriteMode override, boolean dryRun) {
        if (profile == null) throw new IllegalArgumentException("profile write mode is required");
        if (dryRun) return WriteMode.NONE;
        if (override == null) return profile;
        if (override.privilege() > profile.privilege())
            throw new IllegalArgumentException("task write override cannot upgrade profile permission");
        return override;
    }
}
