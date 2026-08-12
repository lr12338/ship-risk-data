package com.hifleet.service;

import com.hifleet.domain.ReplayQueryRequest;
import com.hifleet.trajectory.TrajectoryPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, deduplicated replay plan used by both metrics and execution. */
public final class AlongsideReplayQueryPlan {
    private final List<PlannedQuery> queries;

    AlongsideReplayQueryPlan(List<PlannedQuery> queries) {
        this.queries = Collections.unmodifiableList(new ArrayList<PlannedQuery>(queries));
    }

    public List<PlannedQuery> getQueries() { return queries; }
    public int size() { return queries.size(); }

    public static final class PlannedQuery {
        private final String queryKey;
        private final ReplayQueryRequest request;
        private final List<QueryContext> contexts = new ArrayList<QueryContext>();

        PlannedQuery(String queryKey, ReplayQueryRequest request) {
            this.queryKey = queryKey;
            this.request = request;
        }

        void addContext(ShipAlongsideDetector.LowSpeedWindow window,
                        List<TrajectoryPoint> ownWindowPoints) {
            contexts.add(new QueryContext(window, ownWindowPoints));
        }

        public String getQueryKey() { return queryKey; }
        public ReplayQueryRequest getRequest() { return request; }
        public List<QueryContext> getContexts() { return Collections.unmodifiableList(contexts); }
    }

    public static final class QueryContext {
        private final ShipAlongsideDetector.LowSpeedWindow window;
        private final List<TrajectoryPoint> ownWindowPoints;

        QueryContext(ShipAlongsideDetector.LowSpeedWindow window, List<TrajectoryPoint> ownWindowPoints) {
            this.window = window;
            this.ownWindowPoints = Collections.unmodifiableList(
                    new ArrayList<TrajectoryPoint>(ownWindowPoints));
        }

        public ShipAlongsideDetector.LowSpeedWindow getWindow() { return window; }
        public List<TrajectoryPoint> getOwnWindowPoints() { return ownWindowPoints; }
    }
}
