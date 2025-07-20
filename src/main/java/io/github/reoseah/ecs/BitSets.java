package io.github.reoseah.ecs;

import it.unimi.dsi.fastutil.Hash;

import java.util.Arrays;

/// Contains utilities to operate on `long[]` representing lists of bits,
/// similar to [java.util.BitSet], but in procedural style.
public class BitSets {
    public static long[] encode(int... values) {
        int max = 0;
        for (int value : values) {
            if (value > max) {
                max = value;
            }
        }
        long[] bits = new long[(max / Long.SIZE) + 1];
        for (int value : values) {
            int index = (value / Long.SIZE);
            int bit = value % Long.SIZE;

            bits[index] |= 1L << bit;
        }
        return bits;
    }

    public static boolean has(long[] bits, int value) {
        int index = value / Long.SIZE;
        int bit = value % Long.SIZE;

        return (bits[index] & (1L << bit)) != 0;
    }

    public static void set(long[] bits, int value) {
        int index = value / Long.SIZE;
        long bit = value % Long.SIZE;

        bits[index] |= 1L << bit;
    }

    public static void unset(long[] bits, int value) {
        int index = value / Long.SIZE;
        long bit = value % Long.SIZE;

        bits[index] &= ~(1L << bit);
    }

    public static void toggle(long[] bits, int value) {
        int index = value / Long.SIZE;
        long bit = value % Long.SIZE;

        bits[index] ^= 1L << bit;
    }

    public static boolean contains(long[] bits, long[] other) {
        if (bits == other) {
            return true;
        }
        for (int i = 0; i < other.length; i++) {
            long otherWord = other[i];
            if (i >= bits.length) {
                if (otherWord != 0) {
                    return false;
                }
                continue;
            }
            long bitsWord = bits[i];
            if ((bitsWord & otherWord) != otherWord) {
                return false;
            }
        }
        return true;
    }

    public static class HashStrategy implements Hash.Strategy<long[]> {
        @Override
        public int hashCode(long[] o) {
            return Arrays.hashCode(o);
        }

        @Override
        public boolean equals(long[] a, long[] b) {
            return Arrays.equals(a, b);
        }
    }
}
