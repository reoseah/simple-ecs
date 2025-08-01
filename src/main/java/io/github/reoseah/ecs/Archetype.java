package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;

import java.util.Arrays;

@SuppressWarnings("unchecked")
public class Archetype {
    private static final int DEFAULT_CAPACITY = 8;

    public final int id;
    final long[] componentMask;
    final int[] components;
    @SuppressWarnings("rawtypes")
    final ColumnType[] columnTypes;

    /// Entities stored in this archetype, the order and size should match the
    /// data in [#columns]. This is like a reverse map from `row` to entity ID:
    ///
    /// ```java
    /// for (var row = 0; row < archetype.entityCount(); row++){
    ///     var entity = archetype.entities[row];
    ///     // ...
    ///}
    ///```
    ///
    /// A "reverse" mapping is maintained globally in [World#entities].
    public int[] entities;

    /// The number of entities actually contained in [#entities] and [#columns].
    private int entityCount;

    /// An array of arrays - the `Object` here can be `int[]`, `long[]` or other
    /// type according to component's [ColumnType]. The inner array-like state
    /// is expected to be parallel to [#entities] (and each other).
    final Object[] columns;

    Archetype(World world, int id, long[] componentMask) {
        this.id = id;
        this.componentMask = componentMask;

        int componentCount = BitSets.count(componentMask);
        this.components = new int[componentCount];
        this.columns = new Object[componentCount];
        this.columnTypes = new ColumnType[componentCount];

        this.entityCount = 0;
        this.entities = new int[DEFAULT_CAPACITY];

        int i = 0;
        for (int component = BitSets.nextSetBit(componentMask, 0); component != -1; component = BitSets.nextSetBit(componentMask, component + 1)) {
            this.components[i] = component;
            this.columnTypes[i] = world.componentColumnType(component);
            this.columns[i] = this.columnTypes[i].createStorage(DEFAULT_CAPACITY);
            i++;
        }
    }

    /// Returns a number of entities inside this archetype.
    public int entityCount() {
        return this.entityCount;
    }

    /// Returns storage used to store `component`.
    public Object getColumn(int component) {
        for (int i = 0; i < this.components.length; i++) {
            if (this.components[i] == component) {
                return this.columns[i];
            }
        }
        throw new IllegalArgumentException("Component " + component + " is not present in this archetype.");
    }

    int add(int entity) {
        if (this.entityCount == this.entities.length) {
            int newCapacity = this.entities.length * 2;

            this.entities = Arrays.copyOf(this.entities, newCapacity);
            for (int i = 0; i < this.components.length; i++) {
                this.columns[i] = this.columnTypes[i].growStorage(this.columns[i], newCapacity);
            }
        }
        int row = this.entityCount;
        this.entities[row] = entity;
        this.entityCount++;
        return row;
    }

    /// Removes the passed entity and position from this archetype and returns
    /// data to update the entity map maintained globally in [World]:
    /// - `-1` indicated entity was "popped" from the end of this archetype
    /// - non-negative integer is id of entity "swapped" to fill the
    ///   place previously used by the removed entity
    ///
    /// @see World#removeEntity
    int remove(int entity, int row) {
        int popped = --this.entityCount;
        if (this.entities[popped] == entity) {
            // clear values because they can be objects and we don't want them to stay strongly referenced
            for (int i = 0; i < this.components.length; i++) {
                this.columnTypes[i].remove(this.columns[i], popped);
            }
            return -1;
        } else {
            this.entities[row] = this.entities[popped];

            for (int i = 0; i < this.components.length; i++) {
                this.columnTypes[i].replace(this.columns[i], popped, row);
            }
            return this.entities[row];
        }
    }
}
