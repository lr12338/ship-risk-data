package com.hifleet.sts;

import com.hifleet.sts.domain.StsBackfillTaskRow;
import com.hifleet.sts.service.StsBackfillPlanner;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test — StsBackfillPlanner time slicing.
 *
 * Test G: Planner — exact deterministic <=7d slices, no gap, no overlap, exact endpoint.
 * Test: max range validation (5 years / 261 slices).
 */
public class StsBackfillPlannerTest {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    public void exactlySevenDaysOneSlice() {
        StsBackfillPlanner planner = new StsBackfillPlanner(
                "test-001",
                "2024-01-01 00:00:00",
                "2024-01-08 00:00:00",
                7);
        List<StsBackfillTaskRow> tasks = planner.planTasks();
        assertEquals(1, tasks.size());
        assertEquals("2024-01-01 00:00:00", StsBackfillPlanner.formatTime(tasks.get(0).getSliceFrom()));
        assertEquals("2024-01-08 00:00:00", StsBackfillPlanner.formatTime(tasks.get(0).getSliceTo()));
        assertEquals(0, tasks.get(0).getSliceSeq());
        assertEquals("test-001", tasks.get(0).getBatchNo());
    }

    @Test
    public void eightDaysTwoSlices() {
        StsBackfillPlanner planner = new StsBackfillPlanner(
                "test-002",
                "2024-01-01 00:00:00",
                "2024-01-09 00:00:00",
                7);
        List<StsBackfillTaskRow> tasks = planner.planTasks();
        assertEquals(2, tasks.size());
        assertEquals("2024-01-01 00:00:00", StsBackfillPlanner.formatTime(tasks.get(0).getSliceFrom()));
        assertEquals("2024-01-08 00:00:00", StsBackfillPlanner.formatTime(tasks.get(0).getSliceTo()));
        assertEquals("2024-01-08 00:00:00", StsBackfillPlanner.formatTime(tasks.get(1).getSliceFrom()));
        assertEquals("2024-01-09 00:00:00", StsBackfillPlanner.formatTime(tasks.get(1).getSliceTo()));
    }

    @Test
    public void oneMonthSevenDaySlices() {
        StsBackfillPlanner planner = new StsBackfillPlanner(
                "test-003",
                "2024-01-01 00:00:00",
                "2024-02-01 00:00:00",
                7);
        List<StsBackfillTaskRow> tasks = planner.planTasks();
        assertEquals(5, tasks.size());

        // No gaps, no overlaps, exact final endpoint
        for (int i = 0; i < tasks.size(); i++) {
            assertEquals(i, tasks.get(i).getSliceSeq());
            if (i > 0) {
                assertEquals(
                        StsBackfillPlanner.formatTime(tasks.get(i - 1).getSliceTo()),
                        StsBackfillPlanner.formatTime(tasks.get(i).getSliceFrom()));
            }
        }
        assertEquals("2024-01-01 00:00:00", StsBackfillPlanner.formatTime(tasks.get(0).getSliceFrom()));
        assertEquals("2024-02-01 00:00:00",
                StsBackfillPlanner.formatTime(tasks.get(tasks.size() - 1).getSliceTo()));
    }

    @Test
    public void oneYearSevenDaySlices() {
        StsBackfillPlanner planner = new StsBackfillPlanner(
                "test-004",
                "2024-01-01 00:00:00",
                "2025-01-01 00:00:00",
                7);
        List<StsBackfillTaskRow> tasks = planner.planTasks();
        // 366 days / 7 = 52.285 → 53 slices (leap year)
        assertEquals(53, tasks.size());
        assertEquals("2024-01-01 00:00:00", StsBackfillPlanner.formatTime(tasks.get(0).getSliceFrom()));
        assertEquals("2025-01-01 00:00:00",
                StsBackfillPlanner.formatTime(tasks.get(tasks.size() - 1).getSliceTo()));
    }

    @Test
    public void threeYearSevenDaySlices() {
        StsBackfillPlanner planner = new StsBackfillPlanner(
                "test-005",
                "2023-01-01 00:00:00",
                "2026-01-01 00:00:00",
                7);
        List<StsBackfillTaskRow> tasks = planner.planTasks();
        // ~1095 days / 7 = ~157 slices
        assertEquals(157, tasks.size());
        assertEquals("2023-01-01 00:00:00", StsBackfillPlanner.formatTime(tasks.get(0).getSliceFrom()));
        assertEquals("2026-01-01 00:00:00",
                StsBackfillPlanner.formatTime(tasks.get(tasks.size() - 1).getSliceTo()));
    }

    @Test
    public void singleDaySlice() {
        StsBackfillPlanner planner = new StsBackfillPlanner(
                "test-006",
                "2024-06-15 00:00:00",
                "2024-06-16 00:00:00",
                1);
        List<StsBackfillTaskRow> tasks = planner.planTasks();
        assertEquals(1, tasks.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroSliceDays() {
        new StsBackfillPlanner("test", "2024-01-01 00:00:00", "2024-01-02 00:00:00", 0)
                .validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEightSliceDays() {
        new StsBackfillPlanner("test", "2024-01-01 00:00:00", "2024-01-10 00:00:00", 8)
                .validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFromAfterTo() {
        new StsBackfillPlanner("test", "2024-01-10 00:00:00", "2024-01-01 00:00:00", 7)
                .validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSameStartEnd() {
        new StsBackfillPlanner("test", "2024-01-01 00:00:00", "2024-01-01 00:00:00", 7)
                .validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsSixYearRange() {
        new StsBackfillPlanner("test",
                "2020-01-01 00:00:00", "2026-01-01 00:00:00", 7).validate();
    }

    @Test
    public void fiveYearRangeIsOk() {
        // Exactly 5 years: 2020-01-01 to 2025-01-01 = ~1826 days, ~261 slices
        StsBackfillPlanner planner = new StsBackfillPlanner("test",
                "2020-01-01 00:00:00", "2025-01-01 00:00:00", 7);
        planner.validate();
        assertTrue(planner.computeSliceCount() <= 261);
    }

    @Test
    public void deterministicOrdering() {
        StsBackfillPlanner p1 = new StsBackfillPlanner("x",
                "2024-01-01 00:00:00", "2024-03-01 00:00:00", 7);
        StsBackfillPlanner p2 = new StsBackfillPlanner("x",
                "2024-01-01 00:00:00", "2024-03-01 00:00:00", 7);
        List<StsBackfillTaskRow> t1 = p1.planTasks();
        List<StsBackfillTaskRow> t2 = p2.planTasks();
        assertEquals(t1.size(), t2.size());
        for (int i = 0; i < t1.size(); i++) {
            assertEquals(t1.get(i).getSliceSeq(), t2.get(i).getSliceSeq());
            assertEquals(
                    StsBackfillPlanner.formatTime(t1.get(i).getSliceFrom()),
                    StsBackfillPlanner.formatTime(t2.get(i).getSliceFrom()));
            assertEquals(
                    StsBackfillPlanner.formatTime(t1.get(i).getSliceTo()),
                    StsBackfillPlanner.formatTime(t2.get(i).getSliceTo()));
        }
    }

    @Test
    public void lastSliceClipsToEndpoint() {
        // 10 days with 7-day slices: slice0 = 7d, slice1 = 3d
        StsBackfillPlanner planner = new StsBackfillPlanner("test",
                "2024-01-01 00:00:00", "2024-01-11 00:00:00", 7);
        List<StsBackfillTaskRow> tasks = planner.planTasks();
        assertEquals(2, tasks.size());

        LocalDateTime slice1End = StsBackfillPlanner.toLocalDateTime(tasks.get(0).getSliceTo());
        LocalDateTime slice2Start = StsBackfillPlanner.toLocalDateTime(tasks.get(1).getSliceFrom());
        assertEquals(slice1End, slice2Start);

        // Last slice is shorter (3 days)
        long slice0Days = java.time.Duration.between(
                StsBackfillPlanner.toLocalDateTime(tasks.get(0).getSliceFrom()),
                StsBackfillPlanner.toLocalDateTime(tasks.get(0).getSliceTo())).toDays();
        long slice1Days = java.time.Duration.between(
                StsBackfillPlanner.toLocalDateTime(tasks.get(1).getSliceFrom()),
                StsBackfillPlanner.toLocalDateTime(tasks.get(1).getSliceTo())).toDays();
        assertEquals(7, slice0Days);
        assertEquals(3, slice1Days);
    }
}
