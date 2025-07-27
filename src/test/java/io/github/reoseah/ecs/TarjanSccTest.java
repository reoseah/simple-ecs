package io.github.reoseah.ecs;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TarjanSccTest {
    @Test
    void testSingleNode() {
        var indices = new Int2IntOpenHashMap();
        indices.put(1, 0);

        var neighbors = new ArrayList<IntList>();
        neighbors.add(new IntArrayList()); // no neighbors

        var sccs = TarjanScc.getStronglyConnectedComponents(indices, neighbors);

        assertEquals(1, sccs.size());
        assertEquals(1, sccs.get(0).length);
        assertEquals(1, sccs.get(0)[0]);
    }

    @Test
    void testSimpleCycle() {
        var indices = new Int2IntOpenHashMap();
        indices.put(1, 0);
        indices.put(2, 1);
        indices.put(3, 2);

        var neighbors = new ArrayList<IntList>();
        neighbors.add(new IntArrayList(new int[]{2}));    // 1 -> 2
        neighbors.add(new IntArrayList(new int[]{3}));    // 2 -> 3
        neighbors.add(new IntArrayList(new int[]{1}));    // 3 -> 1

        var sccs = TarjanScc.getStronglyConnectedComponents(indices, neighbors);

        assertEquals(1, sccs.size());
        assertEquals(3, sccs.get(0).length);
        assertTrue(containsAll(sccs.get(0), 1, 2, 3));
    }

    @Test
    void testTwoSeparateComponents() {
        var indices = new Int2IntOpenHashMap();
        indices.put(1, 0);
        indices.put(2, 1);
        indices.put(3, 2);
        indices.put(4, 3);

        var neighbors = new ArrayList<IntList>();
        neighbors.add(new IntArrayList(new int[]{2}));    // 1 -> 2
        neighbors.add(new IntArrayList(new int[]{1}));    // 2 -> 1
        neighbors.add(new IntArrayList(new int[]{4}));    // 3 -> 4
        neighbors.add(new IntArrayList(new int[]{3}));    // 4 -> 3

        var sccs = TarjanScc.getStronglyConnectedComponents(indices, neighbors);

        assertEquals(2, sccs.size());
        assertEquals(2, sccs.get(0).length);
        assertEquals(2, sccs.get(1).length);

        // Check that {1,2} and {3,4} are separate components
        assertTrue((containsAll(sccs.get(0), 1, 2) && containsAll(sccs.get(1), 3, 4))
                || (containsAll(sccs.get(0), 3, 4) && containsAll(sccs.get(1), 1, 2)));
    }

    @Test
    void testComplexGraph() {
        var indices = new Int2IntOpenHashMap();
        indices.put(1, 0);
        indices.put(2, 1);
        indices.put(3, 2);
        indices.put(4, 3);
        indices.put(5, 4);

        var neighbors = new ArrayList<IntList>();
        neighbors.add(new IntArrayList(new int[]{2}));    // 1 -> 2
        neighbors.add(new IntArrayList(new int[]{3}));    // 2 -> 3
        neighbors.add(new IntArrayList(new int[]{1}));    // 3 -> 1
        neighbors.add(new IntArrayList(new int[]{2, 5})); // 4 -> 2,5
        neighbors.add(new IntArrayList(new int[]{4}));    // 5 -> 4

        var sccs = TarjanScc.getStronglyConnectedComponents(indices, neighbors);

        assertEquals(2, sccs.size());

        // Find the component containing nodes 4 and 5
        var scc45 = sccs.stream().filter(scc -> containsAll(scc, 4, 5)).findFirst().orElseThrow();

        // Find the component containing nodes 1, 2, and 3
        var scc123 = sccs.stream().filter(scc -> containsAll(scc, 1, 2, 3)).findFirst().orElseThrow();

        assertEquals(2, scc45.length);
        assertEquals(3, scc123.length);
    }

    @Test
    void testEmptyGraph() {
        var sccs = TarjanScc.getStronglyConnectedComponents(new Int2IntOpenHashMap(), new ArrayList<>());

        assertTrue(sccs.isEmpty());
    }

    @Test
    void testLinearGraph() {
        var indices = new Int2IntOpenHashMap();
        indices.put(1, 0);
        indices.put(2, 1);
        indices.put(3, 2);

        var neighbors = new ArrayList<IntList>();
        neighbors.add(new IntArrayList(new int[]{2}));    // 1 -> 2
        neighbors.add(new IntArrayList(new int[]{3}));    // 2 -> 3
        neighbors.add(new IntArrayList());                // 3 has no outgoing edges

        var sccs = TarjanScc.getStronglyConnectedComponents(indices, neighbors);

        assertEquals(3, sccs.size());
        assertEquals(1, sccs.get(0).length);
        assertEquals(1, sccs.get(1).length);
        assertEquals(1, sccs.get(2).length);
    }


    private boolean containsAll(int[] array, int... values) {
        var listSet = new IntOpenHashSet(array);

        for (int value : values) {
            if (!listSet.contains(value)) {
                return false;
            }
        }
        return true;
    }
}
