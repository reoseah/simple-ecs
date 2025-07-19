package io.github.reoseah.ecs;

public class BitSets {
    public static int[] makeBitSet (int ...values) {
        int max = 0;
        for (int value : values) {
            if (value > max) {
                max = value;
            }
        }
        int[] bits = new int[Math.ceilDiv(max, 32)];
        for (int value : values) {
            int index = value / 32;
            int bit = value % 32;

            bits[index] |= 1 << bit;
        }
        return bits;
    }
}
