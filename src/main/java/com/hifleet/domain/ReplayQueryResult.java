package com.hifleet.domain;

import com.hifleet.trajectory.TrajectoryPoint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * getRecentAis 区域回放查询结果和质量统计。
 */
public class ReplayQueryResult {

    private boolean success;
    private boolean timeout;
    private boolean truncated;
    private boolean largeResult;
    private int rawVesselCount;
    private int rawPointCount;
    private int validPointCount;
    private int parseErrorCount;
    private int outsideRequestedTimePointCount;
    private int httpStatus;
    private long elapsedMs;
    private String errorMessage;
    private boolean retryableFailure;
    private String responseStatus;
    private String responseMessage;
    private ReplayQueryRequest request;
    private Map<String, List<TrajectoryPoint>> tracksByMmsi = new HashMap<String, List<TrajectoryPoint>>();

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isTimeout() {
        return timeout;
    }

    public void setTimeout(boolean timeout) {
        this.timeout = timeout;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public boolean isLargeResult() {
        return largeResult;
    }

    public void setLargeResult(boolean largeResult) {
        this.largeResult = largeResult;
    }

    public int getRawVesselCount() {
        return rawVesselCount;
    }

    public void setRawVesselCount(int rawVesselCount) {
        this.rawVesselCount = rawVesselCount;
    }

    public int getRawPointCount() {
        return rawPointCount;
    }

    public void setRawPointCount(int rawPointCount) {
        this.rawPointCount = rawPointCount;
    }

    public int getValidPointCount() {
        return validPointCount;
    }

    public void setValidPointCount(int validPointCount) {
        this.validPointCount = validPointCount;
    }

    public int getParseErrorCount() {
        return parseErrorCount;
    }

    public void setParseErrorCount(int parseErrorCount) {
        this.parseErrorCount = parseErrorCount;
    }

    public int getInvalidPointCount() {
        return parseErrorCount;
    }

    public int getOutsideRequestedTimePointCount() {
        return outsideRequestedTimePointCount;
    }

    public void setOutsideRequestedTimePointCount(int outsideRequestedTimePointCount) {
        this.outsideRequestedTimePointCount = outsideRequestedTimePointCount;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public boolean isRetryableFailure() { return retryableFailure; }
    public void setRetryableFailure(boolean retryableFailure) { this.retryableFailure = retryableFailure; }

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    public ReplayQueryRequest getRequest() {
        return request;
    }

    public void setRequest(ReplayQueryRequest request) {
        this.request = request;
    }

    public Map<String, List<TrajectoryPoint>> getTracksByMmsi() {
        return tracksByMmsi;
    }

    public void setTracksByMmsi(Map<String, List<TrajectoryPoint>> tracksByMmsi) {
        this.tracksByMmsi = tracksByMmsi;
    }

    public void add(ReplayQueryResult other) {
        if (other == null) {
            return;
        }
        this.rawVesselCount += other.rawVesselCount;
        this.rawPointCount += other.rawPointCount;
        this.validPointCount += other.validPointCount;
        this.parseErrorCount += other.parseErrorCount;
        this.outsideRequestedTimePointCount += other.outsideRequestedTimePointCount;
        this.elapsedMs += other.elapsedMs;
        this.truncated = this.truncated || other.truncated;
        this.largeResult = this.largeResult || other.largeResult;
        this.timeout = this.timeout || other.timeout;
        this.success = this.success || other.success;
        if (other.errorMessage != null) {
            this.errorMessage = other.errorMessage;
        }
        this.retryableFailure = this.retryableFailure || other.retryableFailure;
        if (other.responseStatus != null) {
            this.responseStatus = other.responseStatus;
        }
        if (other.responseMessage != null) {
            this.responseMessage = other.responseMessage;
        }
    }
}
