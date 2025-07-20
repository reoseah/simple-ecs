package io.github.reoseah.ecs;

import org.junit.jupiter.api.Test;

class WorldTests {
    // from https://github.com/noctjs/ecs-benchmark
    @Test
    void packed5() {
        var world = new World();

        int componentA = world.register(Component.IntegerComponent.INSTANCE);
        int componentB = world.register(Component.IntegerComponent.INSTANCE);
        int componentC = world.register(Component.IntegerComponent.INSTANCE);
        int componentD = world.register(Component.IntegerComponent.INSTANCE);
        int componentE = world.register(Component.IntegerComponent.INSTANCE);

        var mask = BitSets.encode(componentA, componentB, componentC, componentD, componentE);

        for (int i = 0; i < 1000; i++) {
            world.createEntity(mask) //
                    .setInt(componentA, 1) //
                    .setInt(componentB, 1) //
                    .setInt(componentC, 1) //
                    .setInt(componentD, 1) //
                    .setInt(componentE, 1);
        }

        int[] updates = {0};

        world.createSystem(BitSets.encode(componentA), (archetypes -> {
            for (var archetype : archetypes) {
                int[] column = archetype.getIntColumn(componentA);
                // TODO: iterate actual entities only...
                for (int i = 0; i < column.length; i++) {
                    column[i] *= 2;
                    updates[0]++;
                }
            }
        }));
        world.createSystem(BitSets.encode(componentB), (archetypes -> {
            for (var archetype : archetypes) {
                int[] column = archetype.getIntColumn(componentB);
                for (int i = 0; i < column.length; i++) {
                    column[i] *= 2;
                    updates[0]++;
                }
            }
        }));
        world.createSystem(BitSets.encode(componentC), (archetypes -> {
            for (var archetype : archetypes) {
                int[] column = archetype.getIntColumn(componentC);
                for (int i = 0; i < column.length; i++) {
                    column[i] *= 2;
                    updates[0]++;
                }
            }
        }));
        world.createSystem(BitSets.encode(componentD), (archetypes -> {
            for (var archetype : archetypes) {
                int[] column = archetype.getIntColumn(componentD);
                for (int i = 0; i < column.length; i++) {
                    column[i] *= 2;
                    updates[0]++;
                }
            }
        }));
        world.createSystem(BitSets.encode(componentE), (archetypes -> {
            for (var archetype : archetypes) {
                int[] column = archetype.getIntColumn(componentE);
                for (int i = 0; i < column.length; i++) {
                    column[i] *= 2;
                    updates[0]++;
                }
            }
        }));

        world.execute();
//        assertEquals(5000, updates[0]);
    }
}
