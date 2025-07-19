package io.github.reoseah.ecs;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Arrays;

public final class Archetype {
    private static final int DEFAULT_CAPACITY = 8;
    private static final float DEFAULT_GROWTH_FACTOR = 2F;

    private final World world;
    private final int[] componentMask;

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
    /// like `int` or `long`.
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

    public Archetype(World world, int[] componentMask) {
        this.world = world;

        this.componentMask = componentMask;

        int componentCount = 0;
        for (int maskFragment : componentMask) {
            componentCount += Integer.bitCount(maskFragment);
        }
        this.componentMap = new int[componentCount];
        this.componentData = new Object[componentCount];

        this.capacity = DEFAULT_CAPACITY;
        this.nextIndex = 0;
        this.entityMap = new int[this.capacity];

        int componentIdx = 0;
        for (int i = 0; i < componentMask.length * 32; i++) {
            int arrayIdx = i / 32;
            int bitIdx = i % 32;

            if ((componentMask[arrayIdx] & (1 << bitIdx)) != 0) {
                this.componentMap[componentIdx] = i;
                // TODO: customize data representing a component, not just ints
                this.componentData[componentIdx] = world.getComponent(i).createStorage(capacity);
                componentIdx++;
            }
        }
        this.recycledIndices = new int[DEFAULT_CAPACITY];
        this.recycledIndicesCount = 0;
    }

    public EntityAccess add(int entity) {
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

        return new EntityAccess(this, index);
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
                this.entityMap[i] = -1;

                if (this.recycledIndices.length == this.recycledIndicesCount) {
                    int newCapacity = (int) (this.recycledIndices.length * DEFAULT_GROWTH_FACTOR);
                    this.recycledIndices = Arrays.copyOf(this.recycledIndices, newCapacity);
                }

                this.recycledIndices[this.recycledIndicesCount] = entity;
                this.recycledIndicesCount++;
            }
        }
        throw new IllegalArgumentException("Entity " + entity + " is not present in this archetype.");
    }

    public EntityAccess access(int entity) {
        for (int i = 0; i < this.nextIndex; i++) {
            if (this.entityMap[i] == entity) {
                return new EntityAccess(this, i);
            }
        }
        throw new IllegalArgumentException("Entity " + entity + " is not present in this archetype.");
    }

    @ApiStatus.Internal
    public int getComponentIndex(int component) {
        for (int i = 0; i < this.componentMap.length; i++) {
            if (this.componentMap[i] == component) {
                return i;
            }
        }
        throw new IllegalArgumentException("Component " + component + " is not present in this archetype.");
    }

    /// A helper object to read and write to an entity. Most methods can be chained.
    public static final class EntityAccess {
        private final Archetype archetype;
        private final int index;

        public EntityAccess(Archetype archetype, int index) {
            this.archetype = archetype;
            this.index = index;
        }

        /// Assumes the component is backed by `int[]`, otherwise will throw an error.
        /// otherwise likely to throw an error.
        ///
        /// @return self, for chaining calls
        /// @throws ClassCastException if the underlying data is not `int[]`
        public EntityAccess setInt(int component, int value) {
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
        public EntityAccess setLong(int component, long value) {
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
