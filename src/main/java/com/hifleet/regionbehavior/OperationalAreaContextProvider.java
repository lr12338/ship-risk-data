package com.hifleet.regionbehavior;

import com.hifleet.trajectory.TrajectoryPoint;

import java.util.List;

public interface OperationalAreaContextProvider {
    OperationalAreaContext classify(List<TrajectoryPoint> points);
}
