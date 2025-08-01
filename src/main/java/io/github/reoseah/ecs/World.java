package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import io.github.reoseah.ecs.bitmanipulation.LongArrayHashStrategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public final class World {
    /// List of all components and resources registered.
    ///
    /// For resources, ECS doesn't care what the value here is. They are just
    /// opaque `int` IDs used in [MultithreadedSchedule] to run systems in
    /// parallel if their component/resource bitsets do not overlap.
    ///
    /// For components, the value is an instance of [ColumnType] that allows
    /// to create, resize, etc., a storage for the component, so component
    /// instances can be stored in primitive arrays like `int[]` or `float[]`
    /// or other data structure that can implement [ColumnType] interface.
    /// The corresponding bit in [#components] bitset is set when registering a
    /// component.
    // TODO: implement resources, currently this and #components bitset are not used
    private final List<Object> componentsAndResources = new ArrayList<>();
    /// Bitset of all indices in [#componentsAndResources] that are components.
    private long[] components = new long[8];

    /// Array of entity data indexed by entity IDs.
    ///
    /// For "live" entities, it's a pair of archetype ID and row therein,
    /// stored inside a 64-bit integer:
    /// ```java
    /// entityMap[entity] = (archetype << 32L) | row
    ///```
    ///
    /// For removed entities, it points to the next removed entity, forming an
    /// implicit "stack" of dead entities without needing extra memory:
    /// ```java
    /// entityMap[removedEntity] = REMOVED_ENTITY_FLAG | nextRemovedEntity
    ///```
    ///
    /// For a more detailed explanation of this, see
    /// <a href="https://skypjack.github.io/2019-05-06-ecs-baf-part-3/">ECS
    /// back and forth, Part 3 - Why you don't need to store deleted entities</a>.
    private long[] entities = new long[512];

    private static final long REMOVED_ENTITY_FLAG = 1L << 63;
    private static final long ENTITY_BITS = 0xFFFF_FFFFL;

    /// Number of live entities in this world.
    private int entityCount = 0;
    private int removedEntity = -1;

    /// List of all archetypes. Maps archetype ids to their instance.
    private final List<Archetype> archetypes = new ArrayList<>();

    // TODO: use adjacency graph
    private final Map<long[], Archetype> archetypeMap = new Object2ObjectOpenCustomHashMap<>(LongArrayHashStrategy.INSTANCE);

    private final Map<long[], List<Archetype>> queries = new Object2ObjectOpenCustomHashMap<>(LongArrayHashStrategy.INSTANCE);

    public int createComponent(ColumnType<?> component) {
        int idx = this.componentsAndResources.size();
        this.componentsAndResources.add(component);
        this.components = BitSets.growAndAdd(this.components, idx);
        return idx;
    }

    public int createResource() {
        int idx = this.componentsAndResources.size();
        this.componentsAndResources.add(null);
        return idx;
    }

    @SuppressWarnings("unchecked")
    public <T> ColumnType<T> componentColumnType(int id) {
        return (ColumnType<T>) this.componentsAndResources.get((id));
    }

    public int entityCount() {
        return this.entityCount;
    }

    public EntityHelper spawn(long[] componentMask) {
        int entity;
        if (this.removedEntity != -1) {
            entity = this.removedEntity;

            long removedEntityData = this.entities[this.removedEntity];
            this.removedEntity = (int) (removedEntityData & ENTITY_BITS);
        } else {
            entity = this.entityCount;

            if (entity == this.entities.length) {
                this.entities = Arrays.copyOf(this.entities, this.entities.length * 2);
            }
        }

        var archetype = this.archetypeMap.get(componentMask);
        if (archetype == null) {
            archetype = createArchetype(componentMask);
        }

        int row = archetype.add(entity);
        this.entities[entity] = ((long) archetype.id << 32) | row;
        this.entityCount++;

        return new EntityHelper(entity, archetype, row);
    }

    /// Returns a helper object to set the state of the entity with chaining,
    /// primitive-specialized methods.
    public EntityHelper accessEntity(int entity) {
        long location = this.entities[entity];
        if ((location & REMOVED_ENTITY_FLAG) != 0) {
            return null;
        }

        int archetypeId = (int) (location >> 32);
        int row = (int) location;

        var archetype = this.archetypes.get(archetypeId);

        return new EntityHelper(entity, archetype, row);
    }

    public void removeEntity(int entity) {
        long location = this.entities[entity];

        this.entities[entity] = this.removedEntity | REMOVED_ENTITY_FLAG;
        this.removedEntity = entity;
        this.entityCount--;

        int archetypeId = (int) (location >> 32);
        int pos = (int) location;

        var archetype = this.archetypes.get(archetypeId);
        int swapped = archetype.remove(entity, pos);
        if (swapped != -1) {
            this.entities[swapped] = location;
        }
    }

    public EntityHelper insertComponents(int entity, long[] componentMask) {
        long location = this.entities[entity];
        int archetypeId = (int) (location >> 32);
        int pos = (int) location;

        var archetype = this.archetypes.get(archetypeId);

        var newComponentMask = BitSets.union(archetype.componentMask, componentMask);

        var newArchetype = this.archetypeMap.get(newComponentMask);
        if (newArchetype == null) {
            newArchetype = this.createArchetype(newComponentMask);
        }

        int newPos = move(entity, pos, archetype, newArchetype);
        this.entities[entity] = ((long) newArchetype.id << 32L) | newPos;

        return new EntityHelper(entity, newArchetype, newPos);
    }

    public EntityHelper removeComponents(int entity, long[] componentMask) {
        long location = this.entities[entity];
        int archetypeId = (int) (location >> 32);
        int pos = (int) location;

        var archetype = this.archetypes.get(archetypeId);

        var newComponentMask = BitSets.difference(archetype.componentMask, componentMask);
        var newArchetype = this.archetypeMap.get(newComponentMask);
        if (newArchetype == null) {
            newArchetype = createArchetype(newComponentMask);
        }

        int newPos = move(entity, pos, archetype, newArchetype);
        this.entities[entity] = ((long) newArchetype.id << 32L) | newPos;

        return new EntityHelper(entity, newArchetype, newPos);
    }

    public EntityHelper modifyComponents(int entity, long[] maskToAdd, long[] maskToRemove) {
        long location = this.entities[entity];
        int archetypeId = (int) (location >> 32);
        int pos = (int) location;

        var archetype = this.archetypes.get(archetypeId);

        var newComponentMask = BitSets.unionAndDifference(archetype.componentMask, maskToAdd, maskToRemove);
        var newArchetype = this.archetypeMap.get(newComponentMask);
        if (newArchetype == null) {
            newArchetype = createArchetype(newComponentMask);
        }

        int newPos = move(entity, pos, archetype, newArchetype);
        this.entities[entity] = ((long) newArchetype.id << 32L) | newPos;

        return new EntityHelper(entity, newArchetype, newPos);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    int move(int entity, long location, Archetype archetype, Archetype newArchetype) {
        int pos = (int) (location & ENTITY_BITS);
        // 1. create an entry in the target archetype
        int newPos = newArchetype.add(entity);

        // 2. move data from the old archetype
        for (int i = 0; i < archetype.components.length; i++) {
            int component = archetype.components[i];

            int newIndex = -1;
            for (int j = 0; j < newArchetype.components.length; j++) {
                if (newArchetype.components[j] == component) {
                    newIndex = j;
                    break;
                }
            }
            if (newIndex == -1) {
                continue;
            }

            var column = archetype.columns[i];
            var newColumn = newArchetype.columns[newIndex];

            var columnType = (ColumnType) this.componentsAndResources.get(component);
            columnType.transfer(column, pos, newColumn, newPos);
        }

        // 3. delete entry in the old archetype
        int swapped = archetype.remove(entity, pos);
        if (swapped != -1) {
            this.entities[swapped] = location;
        }

        return newPos;
    }

    public Schedule createSchedule(ExecutorService threadPool) {
        return new MultithreadedSchedule(this, threadPool);
    }

    public void runOnce(long[] query, SystemRunnable system) {
        var list = this.queries.get(query);
        if (list == null) {
            list = new ArrayList<>();
            for (var archetype : this.archetypes) {
                if (BitSets.isSubset(archetype.componentMask, query)) {
                    list.add(archetype);
                }
            }
        }

        system.run(list, this);
    }

    Archetype createArchetype(long[] componentMask) {
        var archetype = new Archetype(this, this.archetypes.size(), componentMask);
        this.archetypes.add(archetype);
        this.archetypeMap.put(componentMask, archetype);

        for (var entry : this.queries.entrySet()) {
            var query = entry.getKey();
            if (BitSets.isSubset(componentMask, query)) {
                entry.getValue().add(archetype);
            }
        }

        return archetype;
    }

    /// Returns a list of archetypes matching the query. The list is 'live' and
    /// will be updated if matching archetypes are created.
    List<Archetype> getQueryArchetypes(long[] query) {
        var list = this.queries.get(query);

        if (list == null) {
            list = new ArrayList<>();
            for (var archetype : this.archetypes) {
                if (BitSets.isSubset(query, archetype.componentMask)) {
                    list.add(archetype);
                }
            }
            this.queries.put(query, list);
        }

        return list;
    }

    /// Allows setting entity's components in a way that avoids boxing
    /// primitives to the extent possible. Also, it is returned from
    /// [World#spawn] where it contains id of the created entity.
    ///
    /// ## Example:
    /// ```java
    /// var world = new World();
    /// int myInt = world.createComponent(ComponentType.IntegerComponent.INSTANCE);
    /// int myLong = world.createComponent(ComponentType.LongComponent.INSTANCE);
    ///
    /// int myEntity =
    ///         world.createEntity(BitSets.encode(myInt, myLong))
    ///         .setInt(myInt, 1)
    ///         .setLong(myLong, 10)
    ///         .entity;
    ///
    /// world.accessEntity(myEntity)
    ///         .setInt(myInt, 2)
    ///         .setLong(myLong, 20);
    ///```
    public static class EntityHelper {
        public final int entity;
        private final Archetype archetype;
        private final int row;

        EntityHelper(int entity, Archetype archetype, int row) {
            this.entity = entity;
            this.archetype = archetype;
            this.row = row;
        }

        /// Sets value for an [ColumnType.IntArray] component, otherwise
        /// either throws [ClassCastException] or leaves the column in an
        /// invalid state.
        public EntityHelper setInt(int component, int value) {
            assert this.archetype.columnTypes[component] == ColumnType.IntArray.INSTANCE;

            ((int[]) this.archetype.getColumn(component))[this.row] = value;
            return this;
        }

        /// Sets value for an [ColumnType.LongArray] component, otherwise
        /// either throws [ClassCastException] or leaves the column in an
        /// invalid state.
        public EntityHelper setLong(int component, long value) {
            assert this.archetype.columnTypes[component] == ColumnType.LongArray.INSTANCE;

            ((long[]) this.archetype.getColumn(component))[this.row] = value;
            return this;
        }

        /// Sets value for an [ColumnType.ObjectArray] component, otherwise
        /// either throws [ClassCastException] or leaves the column in an
        /// invalid state.
        @SuppressWarnings("unchecked")
        public <T> EntityHelper setObject(int component, T value) {
            assert this.archetype.columnTypes[component] == ColumnType.ObjectArray.INSTANCE;

            ((T[]) this.archetype.getColumn(component))[this.row] = value;
            return this;
        }

        /// Sets value for an [ColumnType.BitSet] component, otherwise either
        /// throws [ClassCastException] or leaves the column in an invalid
        /// state.
        public EntityHelper setBit(int component, boolean value) {
            assert this.archetype.columnTypes[component] == ColumnType.BitSet.INSTANCE;

            var bitset = (long[]) this.archetype.getColumn(component);
            BitSets.set(bitset, this.row, value);
            return this;
        }

        // TODO: add other primitive specializations
    }
}
