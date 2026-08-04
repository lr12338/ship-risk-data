package com.hifleet.regionbehavior;

import java.util.Map;

public interface ShipDimensionProvider {
    Map<String, Double> lengths(String ownMmsi, String targetMmsi);
}
