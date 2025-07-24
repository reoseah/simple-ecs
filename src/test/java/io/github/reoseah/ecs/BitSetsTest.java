package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BitSetsTest {
    @Test
    void testEncodeWithVarargs() {
        long[] bits = BitSets.of(0, 1, 63, 512);

        assertTrue(BitSets.has(bits, 0));
        assertTrue(BitSets.has(bits, 1));
        assertTrue(BitSets.has(bits, 63));
        assertTrue(BitSets.has(bits, 512));
        assertFalse(BitSets.has(bits, 2));
        assertEquals(4, BitSets.count(bits));
    }

    @Test
    void testEncodeWithCollection() {
        IntArraySet set = new IntArraySet();
        set.add(0);
        set.add(1);
        set.add(63);
        set.add(512);

        long[] bits = BitSets.of(set);

        assertTrue(BitSets.has(bits, 0));
        assertTrue(BitSets.has(bits, 1));
        assertTrue(BitSets.has(bits, 63));
        assertTrue(BitSets.has(bits, 512));
        assertFalse(BitSets.has(bits, 2));
        assertEquals(4, BitSets.count(bits));
    }

    @Test
    void testSetUnsetToggle() {
        long[] bits = new long[1];

        BitSets.set(bits, 5);
        assertTrue(BitSets.has(bits, 5));

        BitSets.unset(bits, 5);
        assertFalse(BitSets.has(bits, 5));

        BitSets.toggle(bits, 5);
        assertTrue(BitSets.has(bits, 5));

        BitSets.toggle(bits, 5);
        assertFalse(BitSets.has(bits, 5));
    }

    @Test
    void testContains() {
        long[] larger = BitSets.of(0, 1, 2, 3, 4);
        long[] smaller = BitSets.of(1, 3);
        long[] different = BitSets.of(1, 5);

        assertTrue(BitSets.contains(larger, smaller));
        assertFalse(BitSets.contains(smaller, larger));
        assertFalse(BitSets.contains(larger, different));
    }
}
