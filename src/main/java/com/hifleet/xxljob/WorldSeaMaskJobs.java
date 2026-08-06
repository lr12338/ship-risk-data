package com.hifleet.xxljob;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.hifleet.worldsea.WorldSeaMaskService;
import com.hifleet.worldsea.WorldSeaMaskSnapshot;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorldSeaMaskJobs {
    @Autowired private WorldSeaMaskService worldSeaMaskService;

    @XxlJob("shipBehaviorWorldSeaMaskPreflightJobHandler")
    public ReturnT<String> preflight(String raw) {
        try {
            JSONObject params = JSON.parseObject(raw);
            if (params == null || !"WORLD_SEA_MASK_PREFLIGHT".equals(params.getString("mode"))) {
                throw new IllegalArgumentException("mode must be WORLD_SEA_MASK_PREFLIGHT");
            }
            WorldSeaMaskSnapshot value = worldSeaMaskService.requireSnapshot();
            JSONObject result = new JSONObject(true);
            result.put("status", "PASS");
            result.put("tableExists", true);
            result.put("featureCount", value.getFeatureCount());
            result.put("polygonCount", value.getPolygonCount());
            result.put("multiPolygonCount", value.getMultiPolygonCount());
            result.put("vertexCount", value.getVertexCount());
            result.put("sourceFileSha256", value.getSourceFileSha256());
            result.put("geometryFingerprint", value.getGeometryFingerprint());
            result.put("datasetVersion", value.getDatasetVersion());
            result.put("loadedAt", value.getLoadedAt());
            result.put("loadElapsedMs", value.getLoadElapsedMs());
            result.put("coordinateAssumption", value.getCoordinateAssumption());
            result.put("indexReady", true);
            result.put("sampleAreaNames", value.getSampleAreaNames());
            result.put("databaseWrites", 0);
            return new ReturnT<String>(ReturnT.SUCCESS_CODE, result.toJSONString());
        } catch (Exception ex) {
            return new ReturnT<String>(ReturnT.FAIL_CODE, "WORLD_SEA_MASK_UNAVAILABLE: " + ex.getMessage());
        }
    }
}
