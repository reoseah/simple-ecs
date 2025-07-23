package io.github.reoseah.ecs;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class World {
    @VisibleForTesting
    final List<ComponentType<?>> components = new ArrayList<>();

    /// Map from entity ID (`int`) to `long` containing archetype ID in the
    /// lower 32 bits and entity position inside the archetype in the upper 32.
    /// (Technically 31 bits, as integers in Java are signed and negative ones
    /// can't be used to index arrays.)
    @VisibleForTesting
    final Int2LongMap entityMap = new Int2LongOpenHashMap();

    @VisibleForTesting
    final List<Archetype> archetypes = new ArrayList<>();

    // TODO: use adjacency graph
    @VisibleForTesting
    final Map<long[], Archetype> archetypeMap = new Object2ObjectOpenCustomHashMap<>(LongArrayHashStrategy.INSTANCE);

    private final List<SystemState> systems = new ArrayList<>();

    public int createComponent(ComponentType<?> component) {
        int idx = this.components.size();
        this.components.add(component);
        return idx;
    }

    public ComponentType<?> getComponentType(int id) {
        return this.components.get((id));
    }

    public int getEntityCount() {
        return this.entityMap.size();
    }

    public EntityAccessor createEntity(long[] componentMask) {
        int entity = this.entityMap.size();

        var archetype = this.archetypeMap.get(componentMask);
        if (archetype == null) {
            archetype = createArchetype(componentMask);
        }

        int pos = archetype.add(entity);
        this.entityMap.put(entity, ((long) archetype.id << Integer.SIZE) | pos);

        return new EntityAccessor(entity, archetype, pos);
    }

    public EntityAccessor accessEntity(int entity) {
        long location = this.entityMap.remove(entity);
        int archetypeId = (int) (location >> Integer.SIZE);
        int pos = (int) location;
        var archetype = this.archetypes.get(archetypeId);

        return new EntityAccessor(entity, archetype, pos);
    }

    /// Caches entity's ID and archetype position and allows setting the
    /// components in a way that avoids boxing primitives to the extent possible.
    ///
    /// The instances are not updated if the underlying data changes,
    /// therefore, should not be cached. Instead, store [#entity].
    ///
    /// ## Example:
    /// ```java
    /// var world = new World();
    /// int myInt = world.createComponent(ComponentType.IntegerComponent.INSTANCE);
    /// int myLong = world.createComponent(ComponentType.LongComponent.INSTANCE);
    ///
    /// int entity = world.createEntity(BitSets.encode(myInt, myLong))
    ///         .setInt(myInt, 2)
    ///         .setLong(myLong, 200)
    ///         .entity;
    ///```
    public static class EntityAccessor {
        public final int entity;
        private final Archetype archetype;
        private final int pos;

        public EntityAccessor(int entity, Archetype archetype, int pos) {
            this.entity = entity;
            this.archetype = archetype;
            this.pos = pos;
        }

        /// Sets value for an `int` component. Assumes the component is backed
        /// by `int[]`, otherwise will throw [ClassCastException].
        public EntityAccessor setInt(int component, int value) {
            ((int[]) this.archetype.getColumn(component))[this.pos] = value;
            return this;
        }

        /// Sets value for a `long` component. Assumes the component is backed
        /// by `long[]`, otherwise will throw [ClassCastException].
        public EntityAccessor setLong(int component, long value) {
            ((long[]) this.archetype.getColumn(component))[this.pos] = value;
            return this;
        }
    }

    public long getEntityLocation(int entity) {
        return this.entityMap.get(entity);
    }

    public void removeEntity(int entity) {
        long location = this.entityMap.remove(entity);
        int archetypeId = (int) (location >> Integer.SIZE);
        int pos = (int) location;

        var archetype = this.archetypes.get(archetypeId);
        int moved = archetype.remove(entity, pos);
        if (moved != -1) {
            this.entityMap.put(moved, location);
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

    @ApiStatus.Internal
    public void addSystem(SystemState system) {
        this.systems.add(system);
    }

    public void runOnce(long[] query, SystemFunction system) {
        var state = new SystemState(system, query, this.getQueryArchetypes(query));
        state.run(this);
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

    public Schedule createSchedule() {
        return new Schedule(this);
    }
}
