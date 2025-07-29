package io.github.reoseah.ecs.schedule;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultithreadedScheduleTest {
    @Test
    void testParallelExecution() throws InterruptedException {
        try (var threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            var schedule = new MultithreadedSchedule(threadPool);

            List<String> output = Collections.synchronizedList(new ArrayList<>());

            // Add 3 systems with non-overlapping writes
            for (int i = 0; i < 3; i++) {
                int id = i;
                schedule.configure((_a, _w) -> {
                            System.out.println("Starting system " + id);
                            try {
                                Thread.sleep(50); // simulate work
                                output.add("System " + id);
                                System.out.println("Finishing system " + id);
                            } catch (InterruptedException ignored) {
                            }
                        })
                        .writes(i)
                        .apply();
            }

            long start = System.currentTimeMillis();
            schedule.run();
            long duration = System.currentTimeMillis() - start;

            // All ran successfully
            assertEquals(3, output.size());
            // They ran in parallel, so total time should be significantly less than 3 * 50ms
            assertTrue(duration < 150, "Ran in serial, took too long: " + duration);
        }
    }

    @Test
    void testConflictingSystemsRunSequentially() {
        try (var threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            var schedule = new MultithreadedSchedule(threadPool);

            List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < 2; i++) {
                schedule.configure((_1, _2) -> {
                            timestamps.add(System.nanoTime());
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ignored) {
                            }
                        }).writes(0) // both write to the same component
                        .apply();
            }

            schedule.run();

            assertEquals(2, timestamps.size());

            // second system should start only after first finishes
            long delta = Math.abs(timestamps.get(1) - timestamps.get(0));
            assertTrue(delta > 90_000_000L, "Conflicting systems probably ran in parallel (too quickly), delta = " + delta + "ns");
        }
    }

    @Test
    void testDependentSystemsNotRunInParallel() {
        try (var threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            var schedule = new MultithreadedSchedule(threadPool);

            List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());

            var system1 = schedule.configure((_1, _2) -> {
                        timestamps.add(System.nanoTime());
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                    })
                    .apply();
            schedule.configure((_1, _2) -> {
                        timestamps.add(System.nanoTime());
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                    })
                    .after(system1)
                    .apply();

            schedule.run();

            assertEquals(2, timestamps.size());

            // second system should start only after first finishes
            long delta = Math.abs(timestamps.get(1) - timestamps.get(0));
            assertTrue(delta > 90_000_000L, "Dependent systems probably ran in parallel (too quickly), delta = " + delta + "ns");
        }
    }
}
