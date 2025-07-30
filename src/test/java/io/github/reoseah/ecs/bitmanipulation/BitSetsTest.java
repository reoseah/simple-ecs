package io.github.reoseah.ecs.bitmanipulation;

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

    @Test
    void testNextSetBit() {
        long[] bits = BitSets.of(0, 5, 63, 70);

        assertEquals(0, BitSets.nextSetBit(bits, 0));   // First set bit
        assertEquals(5, BitSets.nextSetBit(bits, 1));   // Next set bit
        assertEquals(63, BitSets.nextSetBit(bits, 6));  // Beyond currently checked
        assertEquals(70, BitSets.nextSetBit(bits, 64)); // In another word
        assertEquals(-1, BitSets.nextSetBit(bits, 71)); // No further set bits
    }

    @Test
    void testNextSetBitEdgeCases() {
        long[] emptyBits = new long[0];
        assertEquals(-1, BitSets.nextSetBit(emptyBits, 0)); // Empty bits array

        long[] singleWord = {0b10010}; // Bits set at 1 and 4
        assertEquals(1, BitSets.nextSetBit(singleWord, 0));
        assertEquals(4, BitSets.nextSetBit(singleWord, 2));
        assertEquals(-1, BitSets.nextSetBit(singleWord, 5));

        Exception exception = assertThrows(IndexOutOfBoundsException.class, () ->
                BitSets.nextSetBit(singleWord, -1));
        assertEquals("fromIndex = -1", exception.getMessage()); // Negative index
    }
}
