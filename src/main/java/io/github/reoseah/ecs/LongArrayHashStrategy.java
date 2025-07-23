package io.github.reoseah.ecs;

import it.unimi.dsi.fastutil.Hash;

import java.util.Arrays;

/// Simple implementation of fastutil's HashStrategy. Use in hash maps keyed by
/// `long[]`, such as used by [BitSets] and [Queries].
public enum LongArrayHashStrategy implements Hash.Strategy<long[]> {
    INSTANCE;

    @Override
    public int hashCode(long[] o) {
        return Arrays.hashCode(o);
    }

    @Override
    public boolean equals(long[] a, long[] b) {
        return Arrays.equals(a, b);
    }
}
