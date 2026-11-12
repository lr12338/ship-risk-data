package com.hifleet.sts.service;

import com.hifleet.sts.domain.StsBackfillTaskRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class StsBackfillPlanner {

    public static final int MAX_PARENT_YEARS = 5;
    public static final int MAX_PLANNED_SLICES = 261;
    public static final int MIN_SLICE_DAYS = 1;
    public static final int MAX_SLICE_DAYS = 7;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId UTC = ZoneId.of("UTC");

    private final String batchNo;
    private final LocalDateTime from;
    private final LocalDateTime to;
    private final int sliceDays;

    public StsBackfillPlanner(String batchNo, String fromStr, String toStr, int sliceDays) {
        this.batchNo = batchNo;
        this.from = LocalDateTime.parse(fromStr, TIME_FMT);
        this.to = LocalDateTime.parse(toStr, TIME_FMT);
        this.sliceDays = sliceDays;
    }

    public void validate() {
        if (sliceDays < MIN_SLICE_DAYS || sliceDays > MAX_SLICE_DAYS) {
            throw new IllegalArgumentException(
                    "sliceDays must be " + MIN_SLICE_DAYS + "-" + MAX_SLICE_DAYS
                            + ", got: " + sliceDays);
        }
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        long days = java.time.Duration.between(from, to).toDays();
        if (days <= 0) {
            throw new IllegalArgumentException("time span must be > 0 days");
        }
        long years = days / 365;
        if (years > MAX_PARENT_YEARS) {
            throw new IllegalArgumentException(
                    "parent time span exceeds " + MAX_PARENT_YEARS + " years: " + days + " days");
        }
        int sliceCount = computeSliceCount();
        if (sliceCount > MAX_PLANNED_SLICES) {
            throw new IllegalArgumentException(
                    "planned slice count exceeds max of " + MAX_PLANNED_SLICES
                            + ": " + sliceCount);
        }
    }

    public int computeSliceCount() {
        long totalSeconds = java.time.Duration.between(from, to).getSeconds();
        long sliceSeconds = (long) sliceDays * 24 * 3600;
        return (int) ((totalSeconds + sliceSeconds - 1) / sliceSeconds);
    }

    public List<StsBackfillTaskRow> planTasks() {
        validate();
        List<StsBackfillTaskRow> tasks = new ArrayList<StsBackfillTaskRow>();
        LocalDateTime current = from;
        int seq = 0;
        while (current.isBefore(to)) {
            LocalDateTime end = current.plusDays(sliceDays);
            if (end.isAfter(to)) {
                end = to;
            }
            StsBackfillTaskRow task = new StsBackfillTaskRow();
            task.setBatchNo(batchNo);
            task.setSliceSeq(seq);
            task.setSliceFrom(toDate(current));
            task.setSliceTo(toDate(end));
            task.setStatus("PENDING");
            tasks.add(task);
            current = end;
            seq++;
        }
        return tasks;
    }

    public LocalDateTime getFrom() { return from; }
    public LocalDateTime getTo() { return to; }
    public int getSliceDays() { return sliceDays; }

    private static Date toDate(LocalDateTime dt) {
        return Date.from(dt.atZone(UTC).toInstant());
    }

    public static String formatTime(Date date) {
        return date.toInstant().atZone(UTC).toLocalDateTime().format(TIME_FMT);
    }

    public static LocalDateTime toLocalDateTime(Date date) {
        return date.toInstant().atZone(UTC).toLocalDateTime();
    }
}
