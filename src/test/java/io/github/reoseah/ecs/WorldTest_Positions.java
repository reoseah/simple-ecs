package io.github.reoseah.ecs;

import org.junit.jupiter.api.Test;

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

    public static World world = new World();
    public static int positionComponent = world.createComponent(Vec2fComponentType.INSTANCE);

    @Test
    void run() {
        var entityMask = BitSets.encode(positionComponent);
        for (int i = 0; i < 100; i++) {
            world.createEntity(entityMask);
        }

        var tick = world.createSchedule();
        tick.addSystem(new SystemState.Builder(WorldTest_Positions::positionSystem1).with(positionComponent));
        tick.addSystem(new SystemState.Builder(WorldTest_Positions::positionSystem2).with(positionComponent));
        tick.addSystem(new SystemState.Builder(WorldTest_Positions::positionSystem3).with(positionComponent));

        var modify = world.createSchedule();
        modify.addSystem(new SystemState.Builder(WorldTest_Positions::changeEntitiesSystem).with(positionComponent));

        for (int i = 0; i < 1000; i++) {
            tick.run();
            if (i % 100 == 0) {
                modify.run();
            }
        }

//        for (var archetype : world.archetypeMap.values()) {
//            var positions = (float[]) archetype.getColumn(positionComponent);
//            for (int i = 0; i < archetype.getCount(); i++) {
//                assert positions[i * 2] == 0;
//                assert positions[i * 2 + 1] == 0;
//            }
//        }
//        world.runOnce(new SystemState.Builder((archetypes, world) -> {
//            for (var archetype : archetypes) {
//                var positions = (float[]) archetype.getColumn(positionComponent);
//                for (int i = 0; i < archetype.getCount(); i++) {
//                    System.out.println(positions[i * 2] + ", " + positions[i * 2 + 1] + " (" + i + ")");
//                }
//            }
//        }).with(positionComponent));
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

    static Random random = new Random();

    static void changeEntitiesSystem(List<Archetype> archetypes, World world) {
        world.removeEntity(random.nextInt(world.getEntityCount()));
        world.createEntity(BitSets.encode(positionComponent));
    }
}
