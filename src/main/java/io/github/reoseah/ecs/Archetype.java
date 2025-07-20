package io.github.reoseah.ecs;

import org.jetbrains.annotations.VisibleForTesting;

import java.util.Arrays;

public final class Archetype {
    private static final int DEFAULT_CAPACITY = 8;
    private static final float DEFAULT_GROWTH_FACTOR = 2F;

    private final World world;
    public final long[] componentMask;

    /// IDs of the components in the archetype.
    ///
    /// Generally, the opposite map is desired - i.e. from a component 'global'
    /// identifier get the 'local' index, for example, an archetype might handle
    /// three components with ids '10, 13, 43', and inside the archetype they
    /// would have indices '0, 1, 2' or permutation thereof - however, for small
    /// numbers of integers it is generally faster to iterate through an array
    /// than to use some kind of hash map or other structure.
    ///
    /// @see #getComponentIndex
    @VisibleForTesting
    final int[] componentMap;

    /// An array of arrays - the `Object` here can be `int[]`, `long[]` or other
    /// array type. The 'outer' index is component 'local' indexes, which can be
    /// figured out from [#componentMap] and the 'inner' array is indexed by
    /// 'local' entity indexes - from [#entityMap].
    ///
    /// This is the closest thing to a Structure-of-Arrays in Java I think, it
    /// is likely the fastest architecture for components storing primitive types
    /// like `int` or `long` and many entities with the same composition.
    private final Object[] componentData;

    /// IDs of entities in the archetype. The order should be parallel to the
    /// 'inner' arrays in [#componentData], iterate through both to have both
    /// entity ID, its 'dense' index and its components.
    private int[] entityMap;

    @VisibleForTesting
    int nextIndex;

    /// Essentially, the length of `this.entityMap`, here for readability...
    private int capacity;

    private int[] recycledIndices;
    @VisibleForTesting
    int recycledIndicesCount;

    public Archetype(World world, long[] componentMask) {
        this.world = world;

        this.componentMask = componentMask;

        int componentCount = 0;
        for (long word : componentMask) {
            componentCount += Long.bitCount(word);
        }
        this.componentMap = new int[componentCount];
        this.componentData = new Object[componentCount];

        this.capacity = DEFAULT_CAPACITY;
        this.nextIndex = 0;
        this.entityMap = new int[this.capacity];

        int componentIdx = 0;
        for (int i = 0; i < componentMask.length * 64; i++) {
            int arrayIdx = i / 64;
            int bitIdx = i % 64;

            if ((componentMask[arrayIdx] & (1L << bitIdx)) != 0) {
                this.componentMap[componentIdx] = i;
                // TODO: customize data representing a component, not just ints
                this.componentData[componentIdx] = world.getComponent(i).createStorage(capacity);
                componentIdx++;
            }
        }
        this.recycledIndices = new int[DEFAULT_CAPACITY];
        this.recycledIndicesCount = 0;
    }

    public Index add(int entity) {
        int index = -1;
        if (this.recycledIndicesCount > 0) {
            index = this.recycledIndices[this.recycledIndicesCount - 1];
            this.entityMap[index] = entity;
            this.recycledIndicesCount--;
        } else {
            if (this.nextIndex == this.capacity) {
                this.grow();
            }
            index = this.nextIndex;
            this.entityMap[index] = entity;
            this.nextIndex++;
        }

        return new Index(this, index);
    }

    private void grow() {
        int newCapacity = (int) (this.capacity * DEFAULT_GROWTH_FACTOR);
        this.capacity = newCapacity;

        this.entityMap = Arrays.copyOf(this.entityMap, newCapacity);

        for (int i = 0; i < this.componentMap.length; i++) {
            @SuppressWarnings("unchecked") var newData = ((Component<Object>) this.world.getComponent(this.componentMap[i])).growStorage(this.componentData[i], newCapacity);

            this.componentData[i] = newData;
        }
    }

    public void remove(int entity) {
        for (int i = 0; i < this.nextIndex; i++) {
            if (this.entityMap[i] == entity) {
                this.removeIndex(i);

                return;
            }
        }
        throw new IllegalArgumentException("Entity " + entity + " is not present in this archetype.");
    }

    public void removeIndex(int index) {
        this.entityMap[index] = -1;

        for (int i = 0; i < this.componentMap.length; i++) {
            @SuppressWarnings("unchecked")
            var componentType = (Component<Object>) world.getComponent(this.componentMap[i]);
            componentType.remove(this.componentData[i], index);
        }

        if (this.recycledIndices.length == this.recycledIndicesCount) {
            int newCapacity = (int) (this.recycledIndices.length * DEFAULT_GROWTH_FACTOR);
            this.recycledIndices = Arrays.copyOf(this.recycledIndices, newCapacity);
        }

        this.recycledIndices[this.recycledIndicesCount] = index;
        this.recycledIndicesCount++;
    }

    /// Return an object to access entity at the given index.
    /// Rather inefficient - underlying structure not optimal for arbitrary access.
    public Index access(int entity) {
        for (int i = 0; i < this.nextIndex; i++) {
            if (this.entityMap[i] == entity) {
                return new Index(this, i);
            }
        }
        throw new IllegalArgumentException("Entity " + entity + " is not present in this archetype.");
    }


    public int getComponentIndex(int component) {
        for (int i = 0; i < this.componentMap.length; i++) {
            if (this.componentMap[i] == component) {
                return i;
            }
        }
        throw new IllegalArgumentException("Component " + component + " is not present in this archetype.");
    }

    public Object getColumn(int component) {
        return this.componentData[this.getComponentIndex(component)];
    }

    /// Assumes the component is backed by `int[]`, otherwise will throw an error.
    ///
    /// @throws ClassCastException if the underlying data is not `int[]`
    public int[] getIntColumn(int component) {
        return (int[]) this.componentData[this.getComponentIndex(component)];
    }

    /// Assumes the component is backed by `long[]`, otherwise will throw an error.
    ///
    /// @throws ClassCastException if the underlying data is not `long[]`
    public long[] getLongColumn(int component) {
        return (long[]) this.componentData[this.getComponentIndex(component)];
    }

    /// A helper object to read and write to an entity. Most methods can be chained.
    public static final class Index {
        private final Archetype archetype;
        private final int index;

        public Index(Archetype archetype, int index) {
            this.archetype = archetype;
            this.index = index;
        }

        /// Assumes the component is backed by `int[]`, otherwise will throw an error.
        ///
        /// @return self, for chaining calls
        /// @throws ClassCastException if the underlying data is not `int[]`
        public Index setInt(int component, int value) {
            var data = this.archetype.componentData[this.archetype.getComponentIndex((component))];
            ((int[]) data)[this.index] = value;

            return this;
        }

        /// Assumes the component is backed by `int[]`, otherwise will throw an error.
        ///
        /// @return component value (which for `int` defaults to `0` if not set)
        /// @throws ClassCastException if the underlying data is not `int[]`
        public int getInt(int component) {
            var data = this.archetype.componentData[this.archetype.getComponentIndex((component))];

            return ((int[]) data)[this.index];
        }

        /// Assumes the component is backed by `long[]`, otherwise will throw an error.
        ///
        /// @return self, for chaining calls
        /// @throws ClassCastException if the underlying data is not `long[]`
        public Index setLong(int component, long value) {
            var data = this.archetype.componentData[this.archetype.getComponentIndex((component))];
            ((long[]) data)[this.index] = value;

            return this;
        }

        /// Assumes the component is backed by `long[]`, otherwise will throw an error.
        ///
        /// @return component value (which for `long` defaults to `0` if not set)
        /// @throws ClassCastException if the underlying data is not `long[]`
        public long getLong(int component) {
            var data = this.archetype.componentData[this.archetype.getComponentIndex((component))];

            return ((long[]) data)[this.index];
        }
    }
}
