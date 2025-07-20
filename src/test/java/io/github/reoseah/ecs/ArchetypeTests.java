package io.github.reoseah.ecs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchetypeTests {
    @Test
    void testArchetypeMethods() {
        var world = new World();

        int componentA = world.register(Component.IntegerComponent.INSTANCE);
        int componentB = world.register(Component.LongComponent.INSTANCE);

        var archetype = new Archetype(world, BitSets.encode(componentA, componentB));

        int entity1 = world.createEmptyEntity();
        int entity2 = world.createEmptyEntity();

        archetype.add(entity1).setInt(componentA, 100).setLong(componentB, 1L << 33);
        archetype.add(entity2).setInt(componentA, 43).setLong(componentB, 2000);

        assertEquals(2, archetype.nextIndex);
        assertEquals(100, archetype.access(entity1).getInt(componentA));
    }

    @Test
    void testArchetypeGrowing() {
        var world = new World();

        int componentA = world.register(Component.IntegerComponent.INSTANCE);
        int componentB = world.register(Component.LongComponent.INSTANCE);

        var archetype = new Archetype(world, BitSets.encode(componentA, componentB));

        for (int i = 0; i < 10_000; i++) {
            int entity = world.createEmptyEntity();

            archetype.add(entity);
        }

        assertEquals(10_000, archetype.nextIndex);
    }
}
