package io.github.reoseah.ecs.bitmanipulation;

import it.unimi.dsi.fastutil.ints.IntCollection;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/// Contains utilities to operate on `long[]` representing lists of bits,
/// similar to [java.util.BitSet], but in a non-object-oriented style.
public class BitSets {
    public static final long[] EMPTY = {};

    public static long[] of(int value) {
        long[] bits = new long[(value / Long.SIZE) + 1];
        int index = (value / Long.SIZE);
        int bit = value % Long.SIZE;

        bits[index] |= 1L << bit;
        return bits;
    }

    public static long[] of(int... values) {
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

    public static long[] of(IntCollection values) {
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

    /// Count the number of enabled bits in the `bitset`.
    public static int count(long[] bitset) {
        int count = 0;
        for (long word : bitset) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /// Returns whether the `bit` is enabled in the `bitset`.
    public static boolean contains(long[] bitset, int bit) {
        int index = bit / Long.SIZE;
        int offset = bit % Long.SIZE;

        return (bitset[index] & (1L << offset)) != 0;
    }

    /// Enable `bit` in the `bitset`.
    public static void add(long[] bitset, int bit) {
        int index = bit / Long.SIZE;
        int offset = bit % Long.SIZE;

        bitset[index] |= 1L << offset;
    }

    /// Disable `bit` in the `bitset`.
    public static void remove(long[] bitset, int value) {
        int index = value / Long.SIZE;
        long bit = value % Long.SIZE;

        bitset[index] &= ~(1L << bit);
    }

    /// Invert the `bit` in the `bitset`.
    public static void toggle(long[] bitset, int bit) {
        int index = bit / Long.SIZE;
        int offset = bit % Long.SIZE;

        bitset[index] ^= 1L << offset;
    }

    /// Sets the `bit` to the `enabled` value.
    public static void set(long[] bitset, int bit, boolean enabled) {
        int index = bit / Long.SIZE;
        int offset = bit % Long.SIZE;

        if (enabled) {
            bitset[index] |= 1L << offset;
        } else {
            bitset[index] &= ~(1L << offset);
        }
    }

    public static long[] growAndAdd(long[] bitset, int bit) {
        int index = bit / Long.SIZE;
        int offset = bit % Long.SIZE;
        if (index >= bitset.length) {
            bitset = Arrays.copyOf(bitset, index + 1);
        }
        bitset[index] |= 1L << offset;
        return bitset;
    }

    /// Returns whether `other` is a subset of `bitset`.
    public static boolean isSubset(long[] bitset, long[] other) {
        if (bitset == other) {
            return true;
        }
        for (int i = 0; i < other.length; i++) {
            long otherWord = other[i];
            if (i >= bitset.length) {
                if (otherWord != 0) {
                    return false;
                }
                continue;
            }
            long word = bitset[i];
            if ((word & otherWord) != otherWord) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether two bitsets have no elements in common.
    public static boolean isDisjoint(long[] left, long[] right) {
        if (left == null || right == null) {
            return true;
        }
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            if ((left[i] & right[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    /// Returns union of two bitsets. Does not modify arguments but returns
    /// `left` if no changes are needed.
    public static long[] union(long[] left, long[] right) {
        if (left == right || right.length == 0) {
            return left;
        }

        long[] result = Arrays.copyOf(left, Math.max(left.length, right.length));
        for (int i = 0; i < right.length; i++) {
            result[i] |= right[i];
        }
        return result;
    }

    /// Returns union of the two bitsets. Modifies the larger of the
    /// parameters and returns it.
    public static long[] unionInPlace(long[] left, long[] right) {
        if (left == null) {
            return right;
        }
        if (left == right || right.length == 0) {
            return left;
        }
        if (left.length == 0) {
            return right;
        }
        if (left.length > right.length) {
            for (int i = 0; i < right.length; i++) {
                left[i] |= right[i];
            }
            return left;
        }
        for (int i = 0; i < left.length; i++) {
            right[i] |= left[i];
        }
        return right;
    }

    /// Returns difference aka subtraction of two bitsets. Does not modify arguments.
    public static long[] difference(long[] minuend, long[] subtrahend) {
        if (minuend == subtrahend) {
            return EMPTY;
        }
        long[] result = Arrays.copyOf(minuend, Math.max(minuend.length, subtrahend.length));
        for (int i = 0; i < Math.max(minuend.length, subtrahend.length); i++) {
            result[i] &= ~subtrahend[i];
        }
        return result;
    }

    /// Returns difference aka subtraction of two bitsets. Modifies the larger
    /// of the parameters and returns it.
    public static long[] differenceInPlace(long[] minuend, long[] subtrahend) {
        if (minuend == subtrahend || minuend.length == 0) {
            return EMPTY;
        }
        if (subtrahend.length == 0) {
            return minuend;
        }
        if (minuend.length > subtrahend.length) {
            for (int i = 0; i < subtrahend.length; i++) {
                minuend[i] &= ~subtrahend[i];
            }
            return minuend;
        }
        for (int i = 0; i < minuend.length; i++) {
            subtrahend[i] = minuend[i] & ~subtrahend[i];
        }
        for (int i = minuend.length; i < subtrahend.length; i++) {
            subtrahend[i] = 0;
        }
        return subtrahend;
    }

    public static long[] unionAndDifference(long[] bitset, long[] addend, long[] subtrahend) {
        if (bitset == addend) {
            return bitset;
        }
        long[] result = Arrays.copyOf(bitset, Math.max(bitset.length, addend.length));
        for (int i = 0; i < addend.length; i++) {
            result[i] |= addend[i];
        }
        for (int i = 0; i < Math.min(result.length, subtrahend.length); i++) {
            result[i] &= ~subtrahend[i];
        }
        return result;
    }

    public static long[] addAll(long @Nullable [] bitset, int... bits) {
        if (bits.length == 0) {
            return bitset;
        }
        int max = 0;
        for (int value : bits) {
            if (value > max) {
                max = value;
            }
        }
        int newLength = getRequiredLength(max);
        if (bitset == null) {
            bitset = new long[newLength];
        } else if (newLength > bitset.length) {
            bitset = Arrays.copyOf(bitset, newLength);
        }
        for (int bit : bits) {
            int index = (bit / Long.SIZE);
            int offset = bit % Long.SIZE;

            bitset[index] |= 1L << offset;
        }
        return bitset;
    }

    public static int nextSetBit(long[] bitset, int fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
        }
        int wordIndex = fromIndex / Long.SIZE;
        if (wordIndex >= bitset.length) {
            return -1;
        }

        long word = bitset[wordIndex] & (0xFFFF_FFFF_FFFF_FFFFL << fromIndex);

        while (true) {
            if (word != 0) {
                return (wordIndex * Long.SIZE) + Long.numberOfTrailingZeros(word);
            }
            wordIndex++;
            if (wordIndex == bitset.length) {
                return -1;
            }
            word = bitset[wordIndex];
        }
    }

    public static int getRequiredLength(int bit) {
        return (bit / Long.SIZE) + 1;
    }
}
