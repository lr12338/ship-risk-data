package com.hifleet.xxljob;

import com.alibaba.fastjson.JSON;
import com.hifleet.behaviorplatform.BehaviorPlatformFleetAuditService;
import com.hifleet.behaviorplatform.BehaviorRunManifest;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public final class BehaviorPlatformJobs {
    private final BehaviorPlatformFleetAuditService service;
    @Autowired public BehaviorPlatformJobs(BehaviorPlatformFleetAuditService service) { this.service = service; }

    @XxlJob("shipBehaviorProfilePreflightJobHandler")
    public ReturnT<String> preflight(String raw) {
        try { return new ReturnT<String>(ReturnT.SUCCESS_CODE, service.preflight(raw, false).toJSONString()); }
        catch (Exception ex) { log.error("behavior profile preflight failed", ex); return fail(ex); }
    }

    @XxlJob("shipBehaviorShadowV2FleetAuditJobHandler")
    public ReturnT<String> fleetAudit(String raw) {
        return audit(raw);
    }

    @XxlJob("shipBehaviorAuditJobHandler")
    public ReturnT<String> audit(String raw) {
        try {
            BehaviorRunManifest result = service.run(raw, false);
            return new ReturnT<String>(ReturnT.SUCCESS_CODE, JSON.toJSONString(result));
        } catch (Exception ex) { log.error("behavior fleet audit failed", ex); return fail(ex); }
    }

    @XxlJob("shipBehaviorFormalJobHandler")
    public ReturnT<String> formal(String raw) {
        return fail(new IllegalStateException("shipBehaviorFormalJobHandler remains disabled"));
    }

    private ReturnT<String> fail(Exception ex) {
        return new ReturnT<String>(ReturnT.FAIL_CODE,
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }
}
