package com.hifleet.analysis.alongside.core.classifier;

import com.hifleet.analysis.alongside.core.model.PairPriority;
import com.hifleet.analysis.alongside.core.model.VesselProfile;

public interface AlongsidePairPriorityClassifier {
    PairPriority classify(VesselProfile a, VesselProfile b);
}
