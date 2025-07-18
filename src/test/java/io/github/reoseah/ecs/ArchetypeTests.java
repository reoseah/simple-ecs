package io.github.reoseah.ecs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchetypeTests {
    @Test
    void demoTestMethod() {
        var component1 = 0;
        var component2 = 1;

        var archetype = new Archetype(new int[]{(1 << component1) | (1 << component2)});

        var entity1 = 0;
        var entity2 = 1;

        archetype.add(entity1);
        archetype.add(entity2);

        assertEquals(0, archetype.get(entity1, component1));
    }
}
