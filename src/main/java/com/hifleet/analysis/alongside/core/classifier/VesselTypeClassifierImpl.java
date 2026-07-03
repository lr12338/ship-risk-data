package com.hifleet.analysis.alongside.core.classifier;

import com.hifleet.analysis.alongside.core.model.VesselClass;
import com.hifleet.analysis.alongside.core.model.VesselType;
import com.hifleet.analysis.portcall.model.CleanAisPoint;

import java.util.Collections;
import java.util.Set;

public class VesselTypeClassifierImpl implements VesselTypeClassifier {

    private static final long serialVersionUID = 1L;

    private final Set<VesselType> highPriorityShipTypes;
    private final Set<VesselType> normalServiceShipTypes;
    private final Set<VesselType> lowPriorityShipTypes;

    public VesselTypeClassifierImpl(Set<VesselType> highPriorityShipTypes,
                                    Set<VesselType> normalServiceShipTypes,
                                    Set<VesselType> lowPriorityShipTypes) {
        this.highPriorityShipTypes = highPriorityShipTypes == null
            ? Collections.<VesselType>emptySet() : highPriorityShipTypes;
        this.normalServiceShipTypes = normalServiceShipTypes == null
            ? Collections.<VesselType>emptySet() : normalServiceShipTypes;
        this.lowPriorityShipTypes = lowPriorityShipTypes == null
            ? Collections.<VesselType>emptySet() : lowPriorityShipTypes;
    }

    @Override
    public VesselClass classify(CleanAisPoint point) {
        VesselType type = resolveType(point);
        if (lowPriorityShipTypes.contains(type)) {
            return VesselClass.LOW_PRIORITY;
        }
        if (normalServiceShipTypes.contains(type)) {
            return VesselClass.NORMAL_SERVICE;
        }
        if (highPriorityShipTypes.contains(type)) {
            return type == VesselType.UNKNOWN ? VesselClass.UNKNOWN : VesselClass.HIGH_PRIORITY;
        }
        return VesselClass.UNKNOWN;
    }

    public VesselType resolveType(CleanAisPoint point) {
        if (point == null) {
            return VesselType.UNKNOWN;
        }
        VesselType aisType = resolveAisShipType(point.getShipType());
        if (aisType != VesselType.UNKNOWN) {
            return aisType;
        }
        if (point.getShipName() == null) {
            return VesselType.UNKNOWN;
        }
        String text = point.getShipName().toUpperCase();
        if (contains(text, "TUG")) {
            return VesselType.TUG;
        }
        if (contains(text, "PILOT")) {
            return VesselType.PILOT;
        }
        if (contains(text, "TANKER")) {
            return VesselType.TANKER;
        }
        if (contains(text, "CHEM")) {
            return VesselType.CHEMICAL_TANKER;
        }
        if (contains(text, "LNG")) {
            return VesselType.LNG;
        }
        if (contains(text, "LPG")) {
            return VesselType.LPG;
        }
        if (contains(text, "BUNKER")) {
            return VesselType.BUNKER;
        }
        if (contains(text, "SUPPLY")) {
            return VesselType.SUPPLY;
        }
        if (contains(text, "REEFER")) {
            return VesselType.REEFER;
        }
        if (contains(text, "FISH")) {
            return VesselType.FISHING;
        }
        if (contains(text, "CARGO")) {
            return VesselType.CARGO;
        }
        if (contains(text, "BULK")) {
            return VesselType.BULK_CARRIER;
        }
        if (contains(text, "CONTAINER")) {
            return VesselType.CONTAINER;
        }
        return VesselType.UNKNOWN;
    }

    private VesselType resolveAisShipType(Integer shipType) {
        if (shipType == null) {
            return VesselType.UNKNOWN;
        }
        int code = shipType.intValue();
        if (code == 30) {
            return VesselType.FISHING;
        }
        if (code == 31 || code == 52) {
            return VesselType.TUG;
        }
        if (code == 36) {
            return VesselType.SAILING;
        }
        if (code == 37) {
            return VesselType.PLEASURE_CRAFT;
        }
        if (code == 50) {
            return VesselType.PILOT;
        }
        if (code == 51) {
            return VesselType.SAR;
        }
        if (code == 53) {
            return VesselType.PORT_SERVICE;
        }
        if (code == 54) {
            return VesselType.CLEANING;
        }
        if (code == 55) {
            return VesselType.LAW_ENFORCEMENT;
        }
        if (code >= 60 && code <= 69) {
            return VesselType.PASSENGER;
        }
        if (code >= 70 && code <= 79) {
            return VesselType.CARGO;
        }
        if (code == 80) {
            return VesselType.TANKER;
        }
        if (code >= 81 && code <= 84) {
            return VesselType.CHEMICAL_TANKER;
        }
        if (code >= 85 && code <= 89) {
            return VesselType.TANKER;
        }
        return VesselType.UNKNOWN;
    }

    private boolean contains(String text, String token) {
        return text != null && text.contains(token);
    }
}
