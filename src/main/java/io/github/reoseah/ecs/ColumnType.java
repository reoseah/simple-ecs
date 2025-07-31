package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;

import java.util.Arrays;
import java.util.function.IntFunction;

/// Provider method to handle component storage.
public interface ColumnType<S> {
    S createStorage(int capacity);

    S growStorage(S current, int newCapacity);

    void remove(S storage, int index);

    void replace(S storage, int from, int to);

    /// Move data from one column to another.
    ///
    /// This will be followed by either [#remove] or [#replace]
    /// with the position that was moved from, so it's not necessary to
    /// clean up inside this method.
    void transfer(S storage, int index, S destination, int destinationIndex);

    enum IntegerColumn implements ColumnType<int[]> {
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

        @Override
        public void replace(int[] storage, int from, int to) {
            storage[to] = storage[from];
            storage[from] = 0;
        }

        @Override
        public void transfer(int[] storage, int index, int[] destination, int destinationIndex) {
            destination[destinationIndex] = storage[index];
        }
    }

    enum LongColumn implements ColumnType<long[]> {
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

        @Override
        public void replace(long[] storage, int from, int to) {
            storage[to] = storage[from];
            storage[from] = 0;
        }

        @Override
        public void transfer(long[] storage, int index, long[] destination, int destinationIndex) {
            destination[destinationIndex] = storage[index];
        }
    }

    enum ObjectColumn implements ColumnType<Object[]> {
        INSTANCE;

        @Override
        public Object[] createStorage(int capacity) {
            return new Object[capacity];
        }

        @Override
        public Object[] growStorage(Object[] current, int newCapacity) {
            return Arrays.copyOf(current, newCapacity);
        }

        @Override
        public void remove(Object[] storage, int index) {
            storage[index] = null;
        }

        @Override
        public void replace(Object[] storage, int from, int to) {
            storage[to] = storage[from];
            storage[from] = null;
        }

        @Override
        public void transfer(Object[] storage, int index, Object[] destination, int destinationIndex) {
            destination[destinationIndex] = storage[index];
        }
    }

    /// Creates a column backed by a Java array, similar to [ObjectColumn], but
    /// allows to use different array type than `Object[]`. This can be useful
    /// in cases when you want to use the underlying array in other code.
    ///
    /// Note that Java doesn't erase array types, e.g. `String[]` is different
    /// from `Object[]` and they are not assignable to each other.
    ///
    /// ## Example
    /// ```java
    /// var intArrayColumn = new TypedObjectColumn(int[][]::new);
    ///
    /// var world = new World();
    /// int intArrayComponent = world.createComponent(intArrayColumn);
    ///
    /// int myEntity = world.addEntity(BitSets.of(intArrayComponent)).entity;
    ///
    /// world.runOnce(Queries.of(intArrayComponent),(archetypes, _w) -> {
    ///     for (var archetype : archetypes){
    ///         var column = archetype.getColumn(intArrayColumn);
    ///
    ///         assertEquals(int[][].class, column.getClass());
    ///         // `[[I` not equals `[[L/java/lang/Object;`
    ///         assertNotEquals(Object[][].class, column.getClass());
    ///
    ///         for (int i = 0; i < archetype.getCount(); i++){
    ///             myCodeUsingIntArray(((int[][]) column)[i]);
    ///}
    ///}
    ///});
    ///
    /// void myCodeUsingIntArray(int[] data){ ... }
    ///```
    class TypedObjectColumn<T> implements ColumnType<T[]> {
        private final IntFunction<T[]> arrayConstructor;

        public TypedObjectColumn(IntFunction<T[]> arrayConstructor) {
            this.arrayConstructor = arrayConstructor;
        }

        @Override
        public T[] createStorage(int capacity) {
            return this.arrayConstructor.apply(capacity);
        }

        @Override
        public T[] growStorage(T[] current, int newCapacity) {
            return Arrays.copyOf(current, newCapacity);
        }

        @Override
        public void remove(T[] storage, int index) {
            storage[index] = null;
        }

        @Override
        public void replace(T[] storage, int from, int to) {
            storage[to] = storage[from];
        }

        @Override
        public void transfer(T[] storage, int index, T[] destination, int destinationIndex) {
            destination[destinationIndex] = storage[index];
        }
    }

    enum BitSetColumn implements ColumnType<long[]> {
        INSTANCE;

        @Override
        public long[] createStorage(int capacity) {
            return new long[BitSets.getRequiredLength(capacity)];
        }

        @Override
        public long[] growStorage(long[] current, int newCapacity) {
            return Arrays.copyOf(current, BitSets.getRequiredLength(newCapacity));
        }

        @Override
        public void remove(long[] storage, int index) {
            BitSets.remove(storage, index);
        }

        @Override
        public void replace(long[] storage, int from, int to) {
            if (BitSets.contains(storage, from)) {
                BitSets.add(storage, from);
            } else {
                BitSets.remove(storage, from);
            }
            BitSets.remove(storage, to);
        }

        @Override
        public void transfer(long[] storage, int index, long[] destination, int destinationIndex) {
            if (BitSets.contains(storage, index)) {
                BitSets.add(destination, destinationIndex);
            } else {
                BitSets.remove(destination, destinationIndex);
            }
        }
    }
}
