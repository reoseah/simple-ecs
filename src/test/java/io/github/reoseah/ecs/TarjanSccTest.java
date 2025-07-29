package io.github.reoseah.ecs;

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
        var neighbors = new ArrayList<IntList>();
        neighbors.add(new IntArrayList()); // no neighbors

        var sccs = TarjanScc.getStronglyConnectedComponents(1, neighbors);

        assertEquals(1, sccs.size());
        assertEquals(1, sccs.get(0).length);
        assertEquals(0, sccs.get(0)[0]);
    }

    @Test
    void testSimpleCycle() {
        var neighbors = new ArrayList<IntList>();
        neighbors.add(new IntArrayList(new int[]{1}));    // 1 -> 2
        neighbors.add(new IntArrayList(new int[]{2}));    // 2 -> 3
        neighbors.add(new IntArrayList(new int[]{0}));    // 3 -> 1

        var sccs = TarjanScc.getStronglyConnectedComponents(3, neighbors);

        assertEquals(1, sccs.size());
        assertEquals(3, sccs.get(0).length);
        assertTrue(containsAll(sccs.get(0), 0, 1, 2));
    }

    @Test
    void testTwoSeparateComponents() {
        var neighbors = new ArrayList<IntList>();
        // first cycle
        neighbors.add(new IntArrayList(new int[]{1}));
        neighbors.add(new IntArrayList(new int[]{0}));
        // second cycle
        neighbors.add(new IntArrayList(new int[]{3}));
        neighbors.add(new IntArrayList(new int[]{2}));

        var sccs = TarjanScc.getStronglyConnectedComponents(4, neighbors);

        assertEquals(2, sccs.size());
        assertEquals(2, sccs.get(0).length);
        assertEquals(2, sccs.get(1).length);

        // Check that {1,2} and {3,4} are separate components
        assertTrue((containsAll(sccs.get(0), 0, 1) && containsAll(sccs.get(1), 2, 3))
                || (containsAll(sccs.get(0), 2, 3) && containsAll(sccs.get(1), 0, 1)));
    }

    @Test
    void testComplexGraph() {
        var neighbors = new ArrayList<IntList>();
        neighbors.add(new IntArrayList(new int[]{1}));
        neighbors.add(new IntArrayList(new int[]{2}));
        neighbors.add(new IntArrayList(new int[]{0}));
        neighbors.add(new IntArrayList(new int[]{1, 4}));
        neighbors.add(new IntArrayList(new int[]{3}));

        var sccs = TarjanScc.getStronglyConnectedComponents(5, neighbors);

        assertEquals(2, sccs.size());

        // Find the component containing nodes 4 and 5
        var scc45 = sccs.stream().filter(scc -> containsAll(scc, 3, 4)).findFirst().orElseThrow();

        // Find the component containing nodes 1, 2, and 3
        var scc123 = sccs.stream().filter(scc -> containsAll(scc, 0, 1, 2)).findFirst().orElseThrow();

        assertEquals(2, scc45.length);
        assertEquals(3, scc123.length);
    }

    @Test
    void testEmptyGraph() {
        var sccs = TarjanScc.getStronglyConnectedComponents(0, new ArrayList<>());

        assertTrue(sccs.isEmpty());
    }

    @Test
    void testLinearGraph() {
        var neighbors = new ArrayList<IntList>();
        neighbors.add(new IntArrayList(new int[]{1}));
        neighbors.add(new IntArrayList(new int[]{2}));
        neighbors.add(new IntArrayList());

        var sccs = TarjanScc.getStronglyConnectedComponents(3, neighbors);

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
