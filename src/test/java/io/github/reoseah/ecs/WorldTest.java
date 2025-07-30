package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class WorldTest {
    World world;
    int componentA;
    int componentB;
    long[] mask;

    ExecutorService threadPool;

    @BeforeEach
    void createWorld() {
        world = new World();
        componentA = world.createComponent(ColumnType.IntegerColumn.INSTANCE);
        componentB = world.createComponent(ColumnType.LongColumn.INSTANCE);
        mask = BitSets.of(componentA, componentB);
        threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @AfterEach
    void shutdownThreadPool() {
        threadPool.shutdown();
    }

    @Test
    void testRemoveEntity() {
        int entity1 = world.spawn(mask) //
                .setInt(componentA, 1) //
                .setLong(componentB, 1) //
                .entity;

        world.spawn(mask) //
                .setInt(componentA, 2) //
                .setLong(componentB, 2);

        assertEquals(2, world.getEntityCount());

        world.removeEntity(entity1);

        assertEquals(1, world.getEntityCount());
        assertNull(world.accessEntity(entity1));
    }

    @Test
    void testSchedule() {
        int entity1 = world.spawn(mask) //
                .setInt(componentA, 10) //
                .setLong(componentB, 20) //
                .entity;

        int[] counter = {0};

        var schedule = world.createSchedule(threadPool);
        schedule.configure((archetypes, _) -> {
                    // we only added one entity
                    assertEquals(1, archetypes.size());

                    for (var archetype : archetypes) {
                        var entities = archetype.entities;
                        var columnA = (int[]) archetype.getColumn(componentA);
                        var columnB = (long[]) archetype.getColumn(componentB);

                        for (int i = 0; i < archetype.entityCount(); i++) {
                            assertEquals(entity1, entities[i]);
                            assertEquals(10, columnA[i]);
                            assertEquals(20, columnB[i]);

                            counter[0]++;
                        }
                    }
                }) //
                .writes(componentA, componentB) //
                .apply();

        schedule.run();
        assertEquals(1, counter[0]);

        schedule.run();
        assertEquals(2, counter[0]);

        world.removeEntity(entity1);
        schedule.run();
        // no matching entity, the counter shouldn't get increased
        assertEquals(2, counter[0]);
    }

    @Test
    void testModifyingComponents() {
        int entity1 = world.spawn(BitSets.EMPTY).entity;

        world.insertComponents(entity1, BitSets.of(componentA)).setInt(componentA, 10);
        world.insertComponents(entity1, BitSets.of(componentB)).setLong(componentB, 100);

        assertEntityMatchCount(world, BitSets.of(componentA), 1);
        assertEntityMatchCount(world, BitSets.of(componentB), 1);
        assertEntityMatchCount(world, BitSets.of(componentA, componentB), 1);

        world.removeComponents(entity1, BitSets.of(componentA));

        // we removed one of the components, so queries with A shouldn't match
        assertEntityMatchCount(world, BitSets.of(componentA), 0);
        assertEntityMatchCount(world, BitSets.of(componentB), 1);
        assertEntityMatchCount(world, BitSets.of(componentA, componentB), 0);

        world.modifyComponents(entity1, BitSets.of(componentB), BitSets.of(componentA));

        assertEntityMatchCount(world, BitSets.of(componentA), 0);
        assertEntityMatchCount(world, BitSets.of(componentB), 1);
        assertEntityMatchCount(world, BitSets.of(componentA, componentB), 0);
    }

    void assertEntityMatchCount(World world, long[] query, int count) {
        int[] counter = {0};

        world.runOnce(query, (archetypes, _w) -> {
            for (var archetype : archetypes) {
                counter[0] += archetype.entityCount();
            }
        });

        assertEquals(count, counter[0]);
    }
}
