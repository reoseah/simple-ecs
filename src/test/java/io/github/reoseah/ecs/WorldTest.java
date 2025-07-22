package io.github.reoseah.ecs;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WorldTest {
    World world;
    int componentA;
    int componentB;
    long[] mask;

    @BeforeEach
    void createWorld() {
        world = new World();
        componentA = world.createComponent(ComponentType.IntegerComponent.INSTANCE);
        componentB = world.createComponent(ComponentType.LongComponent.INSTANCE);
        mask = BitSets.encode(componentA, componentB);
    }

    @Test
    void createDeleteEntities() {
        int entity1 = world.createEntity(mask) //
                .setInt(componentA, 1) //
                .setLong(componentB, 1) //
                .entity;

        world.createEntity(mask) //
                .setInt(componentA, 2) //
                .setLong(componentB, 2);

        assertEquals(2, world.entityMap.size());

        world.removeEntity(entity1);

        assertEquals(1, world.entityMap.size());
    }

    @Test
    void runSystem() {
        int entity1 = world.createEntity(mask) //
                .setInt(componentA, 10) //
                .setLong(componentB, 20) //
                .entity;

        int[] counter = {0};

        var schedule = world.createSchedule();
        schedule.addSystem(new SystemState.Builder((archetypes, _) -> {
            // we only added one entity
            assertEquals(1, archetypes.size());

            for (var archetype : archetypes) {
                var entities = archetype.entities;
                var columnA = (int[]) archetype.getColumn(componentA);
                var columnB = (long[]) archetype.getColumn(componentB);

                for (int i = 0; i < archetype.getCount(); i++) {
                    assertEquals(entity1, entities[i]);
                    assertEquals(10, columnA[i]);
                    assertEquals(20, columnB[i]);

                    counter[0]++;
                }
            }
        }).with(componentA, componentB));

        schedule.run();
        assertEquals(1, counter[0]);

        schedule.run();
        assertEquals(2, counter[0]);

        world.removeEntity(entity1);
        schedule.run();
        // no matching entity, the counter shouldn't get increased
        assertEquals(2, counter[0]);
    }
}
