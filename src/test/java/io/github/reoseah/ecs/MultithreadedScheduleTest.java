package io.github.reoseah.ecs;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

public class MultithreadedScheduleTest {
    World world = new World();

    @Test
    void testParallelExecution() throws InterruptedException {
        try (var threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            var schedule = new MultithreadedSchedule(world, threadPool);

            List<String> output = Collections.synchronizedList(new ArrayList<>());

            // Add 3 systems with non-overlapping writes
            for (int i = 0; i < 3; i++) {
                int id = i;
                schedule.configure((_a, _w) -> {
                            try {
                                Thread.sleep(50); // simulate work
                                output.add("System " + id);
                            } catch (InterruptedException ignored) {
                            }
                        })
                        .writes(i)
                        .apply();
            }

            long start = System.currentTimeMillis();
            schedule.run();
            long duration = System.currentTimeMillis() - start;

            assertEquals(3, output.size());
            assertTrue(duration < 150, "Non-conflicting systems probably ran in sequence (too slow): " + duration + "ms");
        }
    }

    @Test
    void testConflictingSystemsRunSequentially() {
        try (var threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            var schedule = new MultithreadedSchedule(world, threadPool);

            List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < 2; i++) {
                schedule.configure((_1, _2) -> {
                            timestamps.add(System.currentTimeMillis());
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ignored) {
                            }
                        }).writes(0) // both write to the same component
                        .apply();
            }

            schedule.run();

            assertEquals(2, timestamps.size());

            long delta = Math.abs(timestamps.get(1) - timestamps.get(0));
            assertTrue(delta > 90, "Conflicting systems probably ran in parallel (too quickly): " + delta + "ms");
        }
    }

    @Test
    void testDependentSystemsRunSequentially() {
        try (var threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            var schedule = new MultithreadedSchedule(world, threadPool);

            List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());

            var system1 = schedule.configure((_1, _2) -> {
                        timestamps.add(System.currentTimeMillis());
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                    })
                    .apply();
            schedule.configure((_1, _2) -> {
                        timestamps.add(System.currentTimeMillis());
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                    })
                    .after(system1)
                    .apply();

            schedule.run();

            assertEquals(2, timestamps.size());

            long delta = Math.abs(timestamps.get(1) - timestamps.get(0));
            assertTrue(delta > 90, "Dependent systems probably ran in parallel (too quickly):" + delta + "ns");
        }
    }

    @Test
    void testThrowsOnDependencyCycle() {
        try (var threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            var schedule = new MultithreadedSchedule(world, threadPool);

            // Create three systems with a circular dependency: 0 -> 1 -> 2 -> 0
            var system0 = schedule.configure((_1, _2) -> {
                    })
                    .apply();

            var system1 = schedule.configure((_1, _2) -> {
                    })
                    .after(system0)
                    .apply();

            var system2 = schedule.configure((_1, _2) -> {
                    })
                    .after(system1)
                    .apply();

            schedule.configure((_1, _2) -> {
                    })
                    .after(system2)
                    .before(system0)
                    .apply();

            assertThrows(IllegalStateException.class, schedule::run);
        }
    }

    @Test
    void testSystemThrowingAnException() {
        try (var threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            var schedule = new MultithreadedSchedule(world, threadPool);
            schedule.configure(MultithreadedScheduleTest::throwingSystem)
                    .apply();

            System.out.println("Should log the exception:");
            schedule.run();
        }
    }

    static void throwingSystem(List<Archetype> archetypes, World world) {
        throw new RuntimeException("Test exception thrown from a system");
    }
}
