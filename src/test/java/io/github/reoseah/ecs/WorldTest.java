package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import io.github.reoseah.ecs.bitmanipulation.Queries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class WorldTest {
    World world;
    int componentA;
    int componentB;
    long[] mask;

    @BeforeEach
    void createWorld() {
        world = new World();
        componentA = world.createComponent(ColumnType.IntegerColumn.INSTANCE);
        componentB = world.createComponent(ColumnType.LongColumn.INSTANCE);
        mask = BitSets.of(componentA, componentB);
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

        var schedule = world.createSchedule();
        schedule.addSystem(
                Queries.of(componentA, componentB),
                (archetypes, _) -> {
                    // we only added one entity
                    assertEquals(1, archetypes.size());

                    for (var archetype : archetypes) {
                        var entities = archetype.entities;
                        var columnA = (int[]) archetype.getColumn(componentA);
                        var columnB = (long[]) archetype.getColumn(componentB);

                        for (int i = 0; i < archetype.population(); i++) {
                            assertEquals(entity1, entities[i]);
                            assertEquals(10, columnA[i]);
                            assertEquals(20, columnB[i]);

                            counter[0]++;
                        }
                    }
                });

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
    void testInsertingComponents() {
        int entity1 = world.spawn(BitSets.EMPTY).entity;

        world.insertComponents(entity1, BitSets.of(componentA)).setInt(componentA, 10);
        world.insertComponents(entity1, BitSets.of(componentB)).setLong(componentB, 100);

        int[] counter = new int[] { 0 };

        world.runOnce(Queries.of(componentA, componentB), (archetypes, _w) -> {
            for (var archetype : archetypes) {
                for (int i = 0; i < archetype.population(); i++) {
                    counter[0]++;
                }
            }
        });
        assertEquals(1, counter[0]);


        world.removeComponents(entity1, BitSets.of(componentA));
        world.runOnce(Queries.of(componentA, componentB), (archetypes, _w) -> {
            for (var archetype : archetypes) {
                for (int i = 0; i < archetype.population(); i++) {
                    counter[0]++;
                }
            }
        });
        // we removed one of the components, so query shouldn't match
        assertEquals(1, counter[0]);
    }
}
