package com.hifleet.analysis.alongside.core.classifier;

import com.hifleet.analysis.alongside.core.model.PairPriority;
import com.hifleet.analysis.alongside.core.model.VesselProfile;
import com.hifleet.analysis.alongside.core.model.VesselType;

public class AlongsidePairPriorityClassifierImpl implements AlongsidePairPriorityClassifier {

    @Override
    public PairPriority classify(VesselProfile a, VesselProfile b) {
        if (a == null || b == null || a.getMmsi() == null || b.getMmsi() == null) {
            return PairPriority.EXCLUDE;
        }
        if (a.getMmsi().equals(b.getMmsi()) || isTestMmsi(a.getMmsi()) || isTestMmsi(b.getMmsi())) {
            return PairPriority.EXCLUDE;
        }
        VesselType ta = safeType(a);
        VesselType tb = safeType(b);
        if (is(ta, tb, VesselType.TANKER, VesselType.TANKER)
            || is(ta, tb, VesselType.TANKER, VesselType.BUNKER)
            || is(ta, tb, VesselType.CHEMICAL_TANKER, VesselType.CHEMICAL_TANKER)
            || isAny(ta, tb, VesselType.LNG, VesselType.SUPPLY)
            || isAny(ta, tb, VesselType.LPG, VesselType.SUPPLY)
            || isAny(ta, tb, VesselType.FISHING, VesselType.REEFER)
            || isAny(ta, tb, VesselType.FISHING, VesselType.FISH_CARRIER)
            || isAny(ta, tb, VesselType.FISHING, VesselType.SUPPLY)
            || isAny(ta, tb, VesselType.CARGO, VesselType.UNKNOWN)
            || isAny(ta, tb, VesselType.TANKER, VesselType.UNKNOWN)) {
            return PairPriority.HIGH;
        }
        if ((isCargoOrTanker(ta) && isNormalService(tb)) || (isCargoOrTanker(tb) && isNormalService(ta))
            || is(ta, tb, VesselType.PORT_SERVICE, VesselType.PORT_SERVICE)) {
            return PairPriority.DOWNGRADED;
        }
        if (isAny(ta, tb, VesselType.BULK_CARRIER, VesselType.CARGO)
            || isAny(ta, tb, VesselType.CARGO, VesselType.SUPPLY)
            || isAny(ta, tb, VesselType.WORKBOAT, VesselType.SUPPLY)
            || isAny(ta, tb, VesselType.PASSENGER, VesselType.SUPPLY)) {
            return PairPriority.MEDIUM;
        }
        if (ta == VesselType.UNKNOWN || tb == VesselType.UNKNOWN) {
            return PairPriority.MEDIUM;
        }
        return PairPriority.DOWNGRADED;
    }

    private boolean isTestMmsi(Long mmsi) {
        return mmsi.longValue() <= 0L || mmsi.longValue() == 999999999L;
    }

    private VesselType safeType(VesselProfile profile) {
        return profile.getVesselType() == null ? VesselType.UNKNOWN : profile.getVesselType();
    }

    private boolean is(VesselType a, VesselType b, VesselType x, VesselType y) {
        return a == x && b == y;
    }

    private boolean isAny(VesselType a, VesselType b, VesselType x, VesselType y) {
        return (a == x && b == y) || (a == y && b == x);
    }

    private boolean isCargoOrTanker(VesselType type) {
        return type == VesselType.CARGO || type == VesselType.TANKER || type == VesselType.CHEMICAL_TANKER;
    }

    private boolean isNormalService(VesselType type) {
        return type == VesselType.TUG || type == VesselType.PILOT || type == VesselType.LAW_ENFORCEMENT
            || type == VesselType.SAR || type == VesselType.PORT_SERVICE;
    }
}
