package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;

public class Archetype {
    private static final int DEFAULT_CAPACITY = 8;

    private final World world;
    public final int id;
    public final long[] componentMask;
    private final int[] components;

    /// Entities stored in this archetype, the order and size should match the
    /// data in [#columns].
    ///
    /// Reverse map is maintained globally in [World#entityMap].
    public int[] entities;
    /// The number of entities actually contained in [#entities] and [#columns].
    private int count;
    /// An array of arrays - the `Object` here can be `int[]`, `long[]` or other
    /// type according to component's [ComponentType].
    private final Object[] columns;

    public Archetype(World world, int id, long[] componentMask) {
        this.world = world;
        this.id = id;
        this.componentMask = componentMask;

        int componentCount = BitSets.count(componentMask);
        this.components = new int[componentCount];
        this.columns = new Object[componentCount];

        this.count = 0;
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

    /// @return position of the entity inside the archetype, aka 'dense index' or 'row'
    public int add(int entity) {
        if (this.count == this.entities.length) {
            this.grow();
        }
        int pos = this.count;
        this.entities[pos] = entity;
        this.count++;
        return pos;
    }

    private void grow() {
        int newCapacity = this.entities.length * 2;

        this.entities = Arrays.copyOf(this.entities, newCapacity);
        for (int i = 0; i < this.components.length; i++) {
            @SuppressWarnings("unchecked") var column = ((ComponentType<Object>) this.world.getComponentType(this.components[i])).growStorage(this.columns[i], newCapacity);

            this.columns[i] = column;
        }
    }

    /// @return ID of an entity that was 'swapped' to replace the removed one,
    ///     or `-1` if the removed entity was the last one and no 'swapping' was needed
    /// @implNote we don't check that `this.entities[pos] == entity`, if a wrong pair is passed,
    ///     the archetype could be in an invalid state
    /// @see World#removeEntity
    @ApiStatus.Internal
    public int remove(int entity, int pos) {
        int popped = --this.count;
        if (this.entities[popped] == entity) {
            for (int i = 0; i < this.components.length; i++) {
                int component = this.components[i];
                @SuppressWarnings("unchecked") var type = (ComponentType<Object>) this.world.getComponentType(component);

                type.remove(this.columns[i], popped);
            }
            return -1;
        } else {
            this.entities[pos] = this.entities[popped];

            for (int i = 0; i < this.components.length; i++) {
                int component = this.components[i];
                @SuppressWarnings("unchecked") var type = (ComponentType<Object>) this.world.getComponentType(component);

                type.move(this.columns[i], popped, pos);
            }
            return this.entities[pos];
        }
    }

    public Object getColumn(int component) {
        for (int i = 0; i < this.components.length; i++) {
            if (this.components[i] == component) {
                return this.columns[i];
            }
        }
        throw new IllegalArgumentException("Component " + component + " is not present in this archetype.");
    }

    public int getCount() {
        return this.count;
    }
}
