package com.hifleet.analysis.alongside.core.classifier;

import com.hifleet.analysis.alongside.core.model.VesselClass;
import com.hifleet.analysis.portcall.model.CleanAisPoint;

public interface VesselTypeClassifier {
    VesselClass classify(CleanAisPoint point);
}
