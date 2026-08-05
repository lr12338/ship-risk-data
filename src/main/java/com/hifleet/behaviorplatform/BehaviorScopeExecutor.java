package com.hifleet.behaviorplatform;

import com.alibaba.fastjson.JSONObject;

public interface BehaviorScopeExecutor {
    boolean supports(BehaviorTargetSourceType sourceType);
    JSONObject preflight(BehaviorScopeContext context);
    BehaviorScopeExecutionResult execute(BehaviorScopeContext context);
}
