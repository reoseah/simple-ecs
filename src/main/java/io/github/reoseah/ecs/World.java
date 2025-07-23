package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import io.github.reoseah.ecs.bitmanipulation.LongArrayHashStrategy;
import io.github.reoseah.ecs.bitmanipulation.Queries;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class World {
    private static final int DEFAULT_ENTITY_CAPACITY = 512;

    /// Maps component ids to their column type.
    private final List<ColumnType<?>> components = new ArrayList<>();

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

    /// Maps archetype ids to their instance.
    private final List<Archetype> archetypes = new ArrayList<>();

    // TODO: use adjacency graph instead
    private final Map<long[], Archetype> archetypeMap = new Object2ObjectOpenCustomHashMap<>(LongArrayHashStrategy.INSTANCE);

    private final List<SystemState> systems = new ArrayList<>();

    public int createComponent(ColumnType<?> component) {
        int idx = this.components.size();
        this.components.add(component);
        return idx;
    }

    public ColumnType<?> getComponentType(int id) {
        return this.components.get((id));
    }

    public int getEntityCount() {
        return this.entityCount;
    }

    public CreateEntityResult createEntity(long[] componentMask) {
        int entity;
        if (this.removedEntity != -1) {
            entity = this.removedEntity;

            long removedEntityData = this.entityMap[this.removedEntity];
            this.removedEntity = (int) removedEntityData;
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

        return new CreateEntityResult(archetype, pos, entity);
    }

    public EntityLocation<?> accessEntity(int entity) {
        long location = this.entityMap[entity];
        int archetypeId = (int) (location >> 32);
        int pos = (int) location;
        var archetype = this.archetypes.get(archetypeId);

        return new EntityLocation<>(archetype, pos);
    }

    public long getEntityLocation(int entity) {
        return this.entityMap[entity];
    }

    public void removeEntity(int entity) {
        long location = this.entityMap[entity];

        this.entityMap[entity] = this.removedEntity;
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

    @ApiStatus.Internal
    public Archetype createArchetype(long[] componentMask) {
        var archetype = new Archetype(this, this.archetypes.size(), componentMask);
        this.archetypes.add(archetype);
        this.archetypeMap.put(componentMask, archetype);

        for (var system : this.systems) {
            if (BitSets.contains(system.query, componentMask)) {
                system.archetypes.add(archetype);
            }
        }

        return archetype;
    }

    // TODO: currently systems using the same components/query each maintain
    //     a list of matching archetypes, possibly cache returned instances,
    //     keyed by bitmask/query, and in #createArchetype iterate through
    //     existing keys and add archetype to corresponding lists;
    //     systems would cache a reference to these mutable list, so they get
    //     their state updated automatically
    @ApiStatus.Internal
    public List<Archetype> getQueryArchetypes(long[] query) {
        var archetypes = new ArrayList<Archetype>();
        for (var archetype : this.archetypes) {
            if (Queries.matches(query, archetype.componentMask)) {
                archetypes.add(archetype);
            }
        }
        return archetypes;
    }

    @ApiStatus.Internal
    public void addSystem(SystemState system) {
        this.systems.add(system);
    }

    public void runOnce(long[] query, SystemFunction system) {
        var state = new SystemState(system, query, this.getQueryArchetypes(query));
        state.run(this);
    }


    public Schedule createSchedule() {
        return new Schedule(this);
    }

    /// Allows setting entity's components in a way that avoids boxing
    /// primitives to the extent possible.
    ///
    /// ## Example:
    /// ```java
    /// var world = new World();
    /// int myInt = world.createComponent(ComponentType.IntegerComponent.INSTANCE);
    /// int myLong = world.createComponent(ComponentType.LongComponent.INSTANCE);
    ///
    /// // world.createEntity returns a subtype of this class
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
    @SuppressWarnings("unchecked") // casting "this" to class extending it for method chaining
    public sealed static class EntityLocation<Self extends EntityLocation<Self>> permits CreateEntityResult {
        private final Archetype archetype;
        private final int pos;

        EntityLocation(Archetype archetype, int pos) {
            this.archetype = archetype;
            this.pos = pos;
        }

        /// Sets value for an `int` component. Assumes the component is backed
        /// by `int[]`, otherwise will throw [ClassCastException].
        public Self setInt(int component, int value) {
            ((int[]) this.archetype.getColumn(component))[this.pos] = value;
            return (Self) this;
        }

        /// Sets value for a `long` component. Assumes the component is backed
        /// by `long[]`, otherwise will throw [ClassCastException].
        public Self setLong(int component, long value) {
            ((long[]) this.archetype.getColumn(component))[this.pos] = value;
            return (Self) this;
        }

        // TODO: add other overloads
    }

    /// Return value from [World#createEntity]. Allows to access ID of the
    /// created entity and also set component values with chaining methods.
    ///
    /// @see EntityLocation parent class for description of other methods
    public static final class CreateEntityResult extends EntityLocation<CreateEntityResult> {
        public final int entity;

        CreateEntityResult(Archetype archetype, int pos, int entity) {
            super(archetype, pos);
            this.entity = entity;
        }
    }
}
