package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class SimpleInheritanceTestCase {
    // TODO: probably needs change detection first
//    World world;
//    ExecutorService threadPool;
//    MultithreadedSchedule schedule;
//
//    /// int[]-backed component storing ID of a parent entity
//    int childOf;
//    /// int[][] component with IDs of children entities
//    int children;
//
//    int visibility;
//    int inheritedVisibility;
//
//    @BeforeEach
//    void setup() {
//        world = new World();
//        threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
//        schedule = new MultithreadedSchedule(world, threadPool);
//
//        childOf = world.createComponent(ColumnType.IntegerColumn.INSTANCE);
//        children = world.createComponent(ColumnType.ObjectColumn.INSTANCE);
//
//        visibility = world.createComponent(ColumnType.BitSetColumn.INSTANCE);
//        inheritedVisibility = world.createComponent(ColumnType.BitSetColumn.INSTANCE);
//    }
//
//    @AfterEach
//    void teardown() {
//        threadPool.shutdown();
//    }
//
//    @Test
//    void test() {
//        int parent = world.spawn(BitSets.of(children, visibility, inheritedVisibility)).setBit(visibility, false).entity;
//        int child1 = world.spawn(BitSets.of(childOf, visibility, inheritedVisibility)).setInt(childOf, parent).entity;
//        int child2 = world.spawn(BitSets.of(childOf, visibility, inheritedVisibility)).setInt(childOf, parent).entity;
//        world.accessEntity(parent).setObject(children, new int[]{child1, child2});
//    }
}
