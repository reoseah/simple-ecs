package io.github.reoseah.ecs;

import java.util.Arrays;

public interface Component<S> {
    S createStorage(int capacity);

    S growStorage(S current, int newCapacity);

    void remove(S storage, int index);

    enum IntegerComponent implements Component<int[]> {
        INSTANCE;

        @Override
        public int[] createStorage(int capacity) {
            return new int[capacity];
        }

        @Override
        public int[] growStorage(int[] current, int newCapacity) {
            return Arrays.copyOf(current, newCapacity);
        }

        @Override
        public void remove(int[] storage, int index) {
            storage[index] = 0;
        }
    }

    enum LongComponent implements Component<long[]> {
        INSTANCE;

        @Override
        public long[] createStorage(int capacity) {
            return new long[capacity];
        }

        @Override
        public long[] growStorage(long[] current, int newCapacity) {
            return Arrays.copyOf(current, newCapacity);
        }

        @Override
        public void remove(long[] storage, int index) {
            storage[index] = 0;
        }
    }
}
