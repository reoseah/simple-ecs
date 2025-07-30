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

    public static int count(long[] bits) {
        int count = 0;
        for (long word : bits) {
            count += Long.bitCount(word);
        }
        return count;
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

    /// Create a new bit set with both the existing bits and the passed values.
    public static long[] copyWith(long[] bits, int... values) {
        int max = 0;
        for (int value : values) {
            if (value > max) {
                max = value;
            }
        }
        long[] result = Arrays.copyOf(bits, Math.max(bits.length, (max / Long.SIZE) + 1));
        for (int value : values) {
            int index = (value / Long.SIZE);
            int bit = value % Long.SIZE;

            result[index] |= 1L << bit;
        }
        return result;
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

    public static long[] merge(long @Nullable [] mask, long @Nullable [] additions) {
        if (mask == additions || additions == null || additions.length == 0) {
            return mask;
        }
        if (mask == null) {
            return Arrays.copyOf(additions, additions.length);
        }

        long[] result = Arrays.copyOf(mask, Math.max(mask.length, additions.length));
        for (int i = 0; i < additions.length; i++) {
            result[i] |= additions[i];
        }
        return result;
    }

    public static long[] mergeValues(long @Nullable [] mask, int... values) {
        if (values.length == 0) {
            return mask;
        }
        int max = 0;
        for (int value : values) {
            if (value > max) {
                max = value;
            }
        }
        int newLength = (max / Long.SIZE) + 1;
        if (mask == null) {
            mask = new long[newLength];
        } else if (newLength > mask.length) {
            mask = Arrays.copyOf(mask, newLength);
        }
        for (int value : values) {
            int index = (value / Long.SIZE);
            int bit = value % Long.SIZE;

            mask[index] |= 1L << bit;
        }
        return mask;
    }

    public static long[] subtract(long[] mask1, long[] mask2) {
        if (mask1 == mask2) {
            return EMPTY;
        }
        long[] result = Arrays.copyOf(mask1, mask1.length);
        for (int i = 0; i < Math.min(mask1.length, mask2.length); i++) {
            result[i] &= ~mask2[i];
        }
        return result;
    }

    public static long[] addAndSubtract(long[] mask1, long[] toAdd, long[] toSubtract) {
        if (mask1 == toAdd) {
            return mask1;
        }
        long[] result = Arrays.copyOf(mask1, Math.max(mask1.length, toAdd.length));
        for (int i = 0; i < toAdd.length; i++) {
            result[i] |= toAdd[i];
        }
        for (int i = 0; i < Math.min(result.length, toSubtract.length); i++) {
            result[i] &= ~toSubtract[i];
        }
        return result;
    }

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

    public static int nextSetBit(long[] bits, int fromIndex) {
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
        }
        int wordIndex = fromIndex / Long.SIZE;
        if (wordIndex >= bits.length) {
            return -1;
        }

        long word = bits[wordIndex] & (0xFFFF_FFFF_FFFF_FFFFL << fromIndex);

        while (true) {
            if (word != 0) {
                return (wordIndex * Long.SIZE) + Long.numberOfTrailingZeros(word);
            }
            wordIndex++;
            if (wordIndex == bits.length) {
                return -1;
            }
            word = bits[wordIndex];
        }
    }

    public static int getRequiredLength(int value) {
        return (value / Long.SIZE) + 1;
    }
}
