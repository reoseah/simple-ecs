package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import io.github.reoseah.ecs.bitmanipulation.Queries;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExampleEcsTestCase {
    static World world = new World();
    static int positionComponent = world.createComponent(ExampleEcsTestCase.FloatPairColumn.INSTANCE);
    static int ageComponent = world.createComponent(ColumnType.IntegerColumn.INSTANCE);
    static Random random = new Random();

    @ParameterizedTest
    @CsvSource("100, 1000")
    void run(int entities, int updates) {
        var entityMask = BitSets.of(positionComponent, ageComponent);
        for (int i = 0; i < entities; i++) {
            world.spawn(entityMask);
        }

        var tick = world.createSchedule();
        tick.addSystem(Queries.of(positionComponent), ExampleEcsTestCase::positionSystem1);
        tick.addSystem(Queries.of(positionComponent), ExampleEcsTestCase::positionSystem2);
        tick.addSystem(Queries.of(positionComponent), ExampleEcsTestCase::positionSystem3);
        tick.addSystem(Queries.of(ageComponent), ExampleEcsTestCase::ageSystem);

        var modify = world.createSchedule();
        modify.addSystem(Queries.of(positionComponent, ageComponent), ExampleEcsTestCase::changeEntitiesSystem);

        for (int i = 0; i < updates; i++) {
            tick.run();
            if (i % 100 == 0) {
                modify.run();
            }
        }

        world.runOnce(Queries.of(positionComponent, ageComponent), (archetypes, world) -> {
            for (var archetype : archetypes) {
                assertEquals(entities, archetype.entityCount());

                var positions = (float[]) archetype.getColumn(positionComponent);
                var ages = (int[]) archetype.getColumn(ageComponent);

                for (int i = 0; i < archetype.entityCount(); i++) {
                    // this should hold as one system increments age by 1,
                    // another increments position X by 0.5, and other two
                    // systems cancel each other out
                    assertEquals(ages[i], positions[i * 2] * 2);
                }
            }
        });
        // this should hold because every time we remove an entity we add another one
        assertEquals(entities, world.getEntityCount());
    }

    static void positionSystem1(List<Archetype> archetypes, World world) {
        for (var archetype : archetypes) {
            var positions = (float[]) archetype.getColumn(positionComponent);
            for (int i = 0; i < archetype.entityCount(); i++) {
                positions[i * 2]++;
                positions[i * 2 + 1]--;
            }
        }
    }

    static void positionSystem2(List<Archetype> archetypes, World world) {
        for (var archetype : archetypes) {
            var positions = (float[]) archetype.getColumn(positionComponent);
            for (int i = 0; i < archetype.entityCount(); i++) {
                positions[i * 2]--;
                positions[i * 2 + 1]++;
            }
        }
    }

    static void positionSystem3(List<Archetype> archetypes, World world) {
        for (var archetype : archetypes) {
            var positions = (float[]) archetype.getColumn(positionComponent);
            for (int i = 0; i < archetype.entityCount(); i++) {
                positions[i * 2] += 0.5f;
                positions[i * 2 + 1] -= 0.5f;
            }
        }
    }

    static void ageSystem(List<Archetype> archetypes, World world) {
        for (var archetype : archetypes) {
            var ages = (int[]) archetype.getColumn(ageComponent);
            for (int i = 0; i < archetype.entityCount(); i++) {
                ages[i]++;
            }
        }
    }

    static void changeEntitiesSystem(List<Archetype> archetypes, World world) {
        world.removeEntity(random.nextInt(world.getEntityCount()));
        world.spawn(BitSets.of(positionComponent, ageComponent));
    }

    // TODO: provide such types for most common combinations of primitive types and their amounts
    enum FloatPairColumn implements ColumnType<float[]> {
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
        public void replace(float[] storage, int from, int to) {
            storage[to * 2] = storage[from * 2];
            storage[to * 2 + 1] = storage[from * 2 + 1];
            storage[from * 2] = 0;
            storage[from * 2 + 1] = 0;
        }

        @Override
        public void transfer(float[] storage, int index, float[] destination, int destinationIndex) {
            destination[destinationIndex * 2] = storage[index * 2];
            destination[destinationIndex * 2 + 1] = storage[index * 2 + 1];
        }
    }
}
