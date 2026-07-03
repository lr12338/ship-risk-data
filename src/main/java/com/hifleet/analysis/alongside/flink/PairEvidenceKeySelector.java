package com.hifleet.analysis.alongside.flink;

import com.hifleet.analysis.alongside.core.model.PairProximityEvidence;
import org.apache.flink.api.java.functions.KeySelector;

public class PairEvidenceKeySelector implements KeySelector<PairProximityEvidence, String> {

    private static final long serialVersionUID = 1L;

    @Override
    public String getKey(PairProximityEvidence value) {
        return value.getPairId();
    }
}
