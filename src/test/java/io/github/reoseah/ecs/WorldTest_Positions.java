package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import io.github.reoseah.ecs.bitmanipulation.Queries;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldTest_Positions {
    enum Vec2fComponentType implements ComponentType<float[]> {
        INSTANCE;

        @Override
        public float[] createStorage(int capacity) {
            return new float[capacity * 2];
        }

        @Override
        public float[] growStorage(float[] current, int newCapacity) {
            return Arrays.copyOf(current, newCapacity * 2);
        }

        @Override
        public void remove(float[] storage, int index) {
            storage[index * 2] = 0;
            storage[index * 2 + 1] = 0;
        }

        @Override
        public void move(float[] storage, int from, int to) {
            storage[to * 2] = storage[from * 2];
            storage[to * 2 + 1] = storage[from * 2 + 1];
            storage[from * 2] = 0;
            storage[from * 2 + 1] = 0;
        }
    }

    static World world = new World();
    static int positionComponent = world.createComponent(Vec2fComponentType.INSTANCE);
    static int ageComponent = world.createComponent(ComponentType.IntegerComponent.INSTANCE);
    static Random random = new Random();

    @ParameterizedTest
    @CsvSource("100, 1000")
    void run(int entities, int updates) {
        var entityMask = BitSets.encode(positionComponent, ageComponent);
        for (int i = 0; i < entities; i++) {
            world.createEntity(entityMask);
        }

        var tick = world.createSchedule();
        tick.addSystem(Queries.of(positionComponent), WorldTest_Positions::positionSystem1);
        tick.addSystem(Queries.of(positionComponent), WorldTest_Positions::positionSystem2);
        tick.addSystem(Queries.of(positionComponent), WorldTest_Positions::positionSystem3);
        tick.addSystem(Queries.of(ageComponent), WorldTest_Positions::ageSystem);

        var modify = world.createSchedule();
        modify.addSystem(Queries.of(positionComponent, ageComponent), WorldTest_Positions::changeEntitiesSystem);

        for (int i = 0; i < updates; i++) {
            tick.run();
            if (i % 100 == 0) {
                modify.run();
            }
        }

        world.runOnce(Queries.of(positionComponent, ageComponent), (archetypes, world) -> {
            for (var archetype : archetypes) {
                assertEquals(entities, archetype.getCount());

                var positions = (float[]) archetype.getColumn(positionComponent);
                var ages = (int[]) archetype.getColumn(ageComponent);

                for (int i = 0; i < archetype.getCount(); i++) {
                    assertEquals(ages[i], positions[i * 2] * 2);
                }
            }
        });
//        assertEquals(entities, world.getEntityCount());
    }

    static void positionSystem1(List<Archetype> archetypes, World world) {
        for (var archetype : archetypes) {
            var positions = (float[]) archetype.getColumn(positionComponent);
            for (int i = 0; i < archetype.getCount(); i++) {
                positions[i * 2]++;
                positions[i * 2 + 1]--;
            }
        }
    }

    static void positionSystem2(List<Archetype> archetypes, World world) {
        for (var archetype : archetypes) {
            var positions = (float[]) archetype.getColumn(positionComponent);
            for (int i = 0; i < archetype.getCount(); i++) {
                positions[i * 2]--;
                positions[i * 2 + 1]++;
            }
        }
    }

    static void positionSystem3(List<Archetype> archetypes, World world) {
        for (var archetype : archetypes) {
            var positions = (float[]) archetype.getColumn(positionComponent);
            for (int i = 0; i < archetype.getCount(); i++) {
                positions[i * 2] += 0.5f;
                positions[i * 2 + 1] -= 0.5f;
            }
        }
    }

    static void ageSystem(List<Archetype> archetypes, World world) {
        for (var archetype : archetypes) {
            var ages = (int[]) archetype.getColumn(ageComponent);
            for (int i = 0; i < archetype.getCount(); i++) {
                ages[i]++;
            }
        }
    }

    static void changeEntitiesSystem(List<Archetype> archetypes, World world) {
        // TODO: don't use map size for generating entity IDs, it only worked so far because I never deleted entities...
        world.removeEntity(random.nextInt(world.getEntityCount()));
        world.createEntity(BitSets.encode(positionComponent, ageComponent));
    }
}
