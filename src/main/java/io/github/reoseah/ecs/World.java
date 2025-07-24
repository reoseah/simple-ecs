package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import io.github.reoseah.ecs.bitmanipulation.LongArrayHashStrategy;
import io.github.reoseah.ecs.bitmanipulation.Queries;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class World {
    private static final int DEFAULT_ENTITY_CAPACITY = 512;

    private static final long REMOVED_ENTITY_FLAG = 1L << 63;
    private static final long ENTITY_BITS = 0xFFFF_FFFFL;

    /// List of all components. Maps component ids to their column type.
    @SuppressWarnings("rawtypes")
    private final List<ColumnType> components = new ArrayList<>();

    /// Alive entities mapped from their id to a pair of archetype id and their
    /// position inside the archetype aka "row", packed into a long like so:
    /// ```java
    /// entityMap[entity] = (archetype << 32L) | row
    ///```
    ///
    /// Removed entities are mapped to an entity removed before them. This
    /// forms an implicit "stack" of dead entities, without having to use
    /// extra memory for it. For more detailed explanation see
    /// <a href="https://skypjack.github.io/2019-05-06-ecs-baf-part-3/">ECS
    /// back and forth, Part 3 - Why you don't need to store deleted entities</a>.
    /// The [#removedEntity] is the "head" of this stack.
    private long[] entityMap = new long[DEFAULT_ENTITY_CAPACITY];
    private int entityCount = 0;
    private int removedEntity = -1;

    /// List of all archetypes. Maps archetype ids to their instance.
    private final List<Archetype> archetypes = new ArrayList<>();

    // TODO: use adjacency graph
    private final Map<long[], Archetype> archetypeMap = new Object2ObjectOpenCustomHashMap<>(LongArrayHashStrategy.INSTANCE);

    private final Map<long[], List<Archetype>> queries = new Object2ObjectOpenCustomHashMap<>(LongArrayHashStrategy.INSTANCE);

    public int createComponent(ColumnType<?> component) {
        int idx = this.components.size();
        this.components.add(component);
        return idx;
    }

    @SuppressWarnings("rawtypes")
    public ColumnType getComponentType(int id) {
        return this.components.get((id));
    }

    public int getEntityCount() {
        return this.entityCount;
    }

    public EntityHelper spawn(long[] componentMask) {
        int entity;
        if (this.removedEntity != -1) {
            entity = this.removedEntity;

            long removedEntityData = this.entityMap[this.removedEntity];
            this.removedEntity = (int) (removedEntityData & ENTITY_BITS);
        } else {
            entity = this.entityCount;

            if (entity == this.entityMap.length) {
                this.entityMap = Arrays.copyOf(this.entityMap, this.entityMap.length * 2);
            }
        }

        var archetype = this.archetypeMap.get(componentMask);
        if (archetype == null) {
            archetype = createArchetype(componentMask);
        }

        int pos = archetype.add(entity);
        this.entityMap[entity] = ((long) archetype.id << 32) | pos;
        this.entityCount++;

        return new EntityHelper(entity, archetype, pos);
    }

    /// Returns a helper object with primitive-specialized chaining methods to
    /// set the state of the entity.
    public EntityHelper accessEntity(int entity) {
        long location = this.entityMap[entity];
        if ((location & REMOVED_ENTITY_FLAG) != 0) {
            return null;
        }

        int archetypeId = (int) (location >> 32);

        int pos = (int) location;
        var archetype = this.archetypes.get(archetypeId);

        return new EntityHelper(entity, archetype, pos);
    }

    public void removeEntity(int entity) {
        long location = this.entityMap[entity];

        this.entityMap[entity] = this.removedEntity | REMOVED_ENTITY_FLAG;
        this.removedEntity = entity;
        this.entityCount--;

        int archetypeId = (int) (location >> 32);
        int pos = (int) location;

        var archetype = this.archetypes.get(archetypeId);
        int swapped = archetype.remove(entity, pos);
        if (swapped != -1) {
            this.entityMap[swapped] = location;
        }
    }

    public EntityHelper insertComponents(int entity, long[] componentMask) {
        long location = this.entityMap[entity];
        int archetypeId = (int) (location >> 32);
        int pos = (int) location;

        var archetype = this.archetypes.get(archetypeId);

        var newComponentMask = BitSets.add(archetype.componentMask, componentMask);
        var newArchetype = this.archetypeMap.get(newComponentMask);
        if (newArchetype == null) {
            newArchetype = createArchetype(newComponentMask);
        }

        int newPos = move(entity, pos, archetype, newArchetype);
        this.entityMap[entity] = ((long) newArchetype.id << 32L) | newPos;

        return new EntityHelper(entity, newArchetype, newPos);
    }

    public EntityHelper removeComponents(int entity, long[] componentMask) {
        long location = this.entityMap[entity];
        int archetypeId = (int) (location >> 32);
        int pos = (int) location;

        var archetype = this.archetypes.get(archetypeId);

        var newComponentMask = BitSets.subtract(archetype.componentMask, componentMask);
        var newArchetype = this.archetypeMap.get(newComponentMask);
        if (newArchetype == null) {
            newArchetype = createArchetype(newComponentMask);
        }

        int newPos = move(entity, pos, archetype, newArchetype);
        this.entityMap[entity] = ((long) newArchetype.id << 32L) | newPos;

        return new EntityHelper(entity, newArchetype, newPos);
    }

    public EntityHelper modifyComponents(int entity, long[] maskToAdd, long[] maskToRemove) {
        long location = this.entityMap[entity];
        int archetypeId = (int) (location >> 32);
        int pos = (int) location;

        var archetype = this.archetypes.get(archetypeId);

        var newComponentMask = BitSets.addAndSubtract(archetype.componentMask, maskToAdd, maskToRemove);
        var newArchetype = this.archetypeMap.get(newComponentMask);
        if (newArchetype == null) {
            newArchetype = createArchetype(newComponentMask);
        }

        int newPos = move(entity, pos, archetype, newArchetype);
        this.entityMap[entity] = ((long) newArchetype.id << 32L) | newPos;

        return new EntityHelper(entity, newArchetype, newPos);
    }

    @SuppressWarnings("unchecked")
    int move(int entity, long location, Archetype archetype, Archetype newArchetype) {
        int pos = (int) (location & ENTITY_BITS);
        // 1. create entry in the target archetype
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

            var columnType = this.components.get(component);
            columnType.transfer(column, pos, newColumn, newPos);
        }

        // 3. delete entry in the old archetype
        int swapped = archetype.remove(entity, pos);
        if (swapped != -1) {
            this.entityMap[swapped] = location;
        }

        return newPos;
    }

    public Schedule createSchedule() {
        return new Schedule(this);
    }

    public void runOnce(long[] query, SystemRunnable system) {
        var list = this.queries.get(query);
        if (list == null) {
            list = new ArrayList<>();
            for (var archetype : this.archetypes) {
                if (Queries.matches(query, archetype.componentMask)) {
                    list.add(archetype);
                }
            }
        }

        var state = new SystemState(system, query, list);
        state.run(this);
    }

    Archetype createArchetype(long[] componentMask) {
        var archetype = new Archetype(this, this.archetypes.size(), componentMask);
        this.archetypes.add(archetype);
        this.archetypeMap.put(componentMask, archetype);

        for (var entry : this.queries.entrySet()) {
            var query = entry.getKey();
            if (Queries.matches(query, componentMask)) {
                entry.getValue().add(archetype);
            }
        }

        return archetype;
    }

    List<Archetype> getQueryArchetypes(long[] query) {
        var list = this.queries.get(query);

        if (list == null) {
            list = new ArrayList<>();
            for (var archetype : this.archetypes) {
                if (Queries.matches(query, archetype.componentMask)) {
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
        private final int pos;

        EntityHelper(int entity, Archetype archetype, int pos) {
            this.entity = entity;
            this.archetype = archetype;
            this.pos = pos;
        }

        /// Sets value for an `int` component. Assumes the component is backed
        /// by `int[]`, otherwise will throw [ClassCastException].
        public EntityHelper setInt(int component, int value) {
            ((int[]) this.archetype.getColumn(component))[this.pos] = value;
            return this;
        }

        /// Sets value for a `long` component. Assumes the component is backed
        /// by `long[]`, otherwise will throw [ClassCastException].
        public EntityHelper setLong(int component, long value) {
            ((long[]) this.archetype.getColumn(component))[this.pos] = value;
            return this;
        }

        /// Sets value for a `Object` component. Assumes the component is backed
        /// by `Object[]`, otherwise will throw [ClassCastException].
        @SuppressWarnings("unchecked")
        public <T> EntityHelper setObject(int component, T value) {
            ((T[]) this.archetype.getColumn(component))[this.pos] = value;
            return this;
        }

        // TODO: add other overloads
    }
}
