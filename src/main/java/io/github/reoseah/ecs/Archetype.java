package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;

import java.util.Arrays;

public class Archetype {
    private static final int DEFAULT_CAPACITY = 8;

    private final World world;
    public final int id;
    final long[] componentMask;
    final int[] components;

    /// Entities stored in this archetype, the order and size should match the
    /// data in [#columns].
    ///
    /// Reverse map is maintained globally in [World#entityMap].
    public int[] entities;
    /// The number of entities actually contained in [#entities] and [#columns].
    private int entityCount;

    /// An array of arrays - the `Object` here can be `int[]`, `long[]` or other
    /// type according to component's [ColumnType]. The inner array-like state
    /// should be parallel to [#entities].
    final Object[] columns;

    public Archetype(World world, int id, long[] componentMask) {
        this.world = world;
        this.id = id;
        this.componentMask = componentMask;

        int componentCount = BitSets.count(componentMask);
        this.components = new int[componentCount];
        this.columns = new Object[componentCount];

        this.entityCount = 0;
        this.entities = new int[DEFAULT_CAPACITY];

        for (int i = 0, componentIdx = 0; i < componentMask.length * Long.SIZE; i++) {
            int arrayIdx = i / Long.SIZE;
            int bitIdx = i % Long.SIZE;

            if ((componentMask[arrayIdx] & (1L << bitIdx)) != 0) {
                this.components[componentIdx] = i;
                this.columns[componentIdx] = world.getComponentType(i).createStorage(DEFAULT_CAPACITY);
                componentIdx++;
            }
        }
    }

    /// Returns number of entities inside this archetype.
    public int entityCount() {
        return this.entityCount;
    }

    /// Returns type-erased storage underlying the passed component.
    public Object getColumn(int component) {
        for (int i = 0; i < this.components.length; i++) {
            if (this.components[i] == component) {
                return this.columns[i];
            }
        }
        throw new IllegalArgumentException("Component " + component + " is not present in this archetype.");
    }

    /// Adds entity to this archetype and returns its position inside, aka
    /// 'dense index' or 'row', so that it can be registered to entity map in
    /// [World].
    int add(int entity) {
        if (this.entityCount == this.entities.length) {
            int newCapacity = this.entities.length * 2;

            this.entities = Arrays.copyOf(this.entities, newCapacity);
            for (int i = 0; i < this.components.length; i++) {
                @SuppressWarnings("unchecked") var column = this.world.getComponentType(this.components[i]).growStorage(this.columns[i], newCapacity);

                this.columns[i] = column;
            }
        }
        int pos = this.entityCount;
        this.entities[pos] = entity;
        this.entityCount++;
        return pos;
    }

    /// Removes the passed entity and position from this archetype and returns
    /// data to update the entity map maintained globally in [World]:
    /// - `-1` indicated entity was "popped" from the end of this archetype
    /// - non-negative integer is id of entity "swapped" to fill the
    ///   place previously used by the removed entity
    ///
    /// @implNote we don't check that `entity` and `pos` correspond to each
    ///     other, if a wrong pair is passed, the archetype will be in an
    ///     invalid state
    /// @see World#removeEntity
    @SuppressWarnings("unchecked")
    int remove(int entity, int pos) {
        int popped = --this.entityCount;
        if (this.entities[popped] == entity) {
            for (int i = 0; i < this.components.length; i++) {
                int component = this.components[i];
                var type = this.world.getComponentType(component);

                type.remove(this.columns[i], popped);
            }
            return -1;
        } else {
            this.entities[pos] = this.entities[popped];

            for (int i = 0; i < this.components.length; i++) {
                int component = this.components[i];
                var type = this.world.getComponentType(component);

                type.replace(this.columns[i], popped, pos);
            }
            return this.entities[pos];
        }
    }

    @Override
    public String toString() {
        return "Archetype{" +
                "id=" + id +
                ", componentMask=" + Arrays.toString(componentMask) +
                ", components=" + Arrays.toString(components) +
                ", entities=" + Arrays.toString(entities) +
                ", entityCount=" + entityCount +
                ", columns=" + Arrays.toString(columns) +
                '}';
    }
}
