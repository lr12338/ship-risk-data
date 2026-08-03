package com.hifleet.xxljob;

import com.hifleet.maritime.MaritimeSceneShadowAuditParams;
import com.hifleet.maritime.MaritimeSceneShadowAuditService;
import com.alibaba.fastjson.JSONObject;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MaritimeSceneShadowJobs {
    private final MaritimeSceneShadowAuditService auditService;

    @Autowired
    public MaritimeSceneShadowJobs(MaritimeSceneShadowAuditService auditService) {
        this.auditService = auditService;
    }

    @XxlJob("shipMaritimeScenePreflightJobHandler")
    public ReturnT<String> preflight(String rawParams) {
        try {
            MaritimeSceneShadowAuditParams params = MaritimeSceneShadowAuditParams.parse(rawParams);
            JSONObject result = auditService.preflight(params);
            return new ReturnT<String>(ReturnT.SUCCESS_CODE, result.toJSONString());
        } catch (Exception ex) {
            log.error("maritime scene shadow preflight failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }

    @XxlJob("shipMaritimeSceneShadowAuditJobHandler")
    public ReturnT<String> shadowAudit(String rawParams) {
        try {
            JSONObject result = auditService.run(rawParams);
            String status = result.getString("status");
            boolean success = "SHADOW_RUN_COMPLETED".equals(status);
            return new ReturnT<String>(success ? ReturnT.SUCCESS_CODE : ReturnT.FAIL_CODE,
                    result.toJSONString());
        } catch (Exception ex) {
            log.error("maritime scene shadow audit failed", ex);
            return new ReturnT<String>(ReturnT.FAIL_CODE, ex.getMessage());
        }
    }
}
