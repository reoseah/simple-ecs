package io.github.reoseah.ecs;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntCollection;

import java.util.List;

public final class SystemState {
    private final SystemHandler system;
    public final long[] componentMask;
    public final List<Archetype> archetypes;

    public SystemState(SystemHandler system, long[] componentMask, List<Archetype> archetypes) {
        this.system = system;
        this.componentMask = componentMask;
        this.archetypes = archetypes;
    }

    public void run(World world) {
        this.system.execute(this.archetypes, world);
    }

    public static final class Builder {
        private final SystemHandler system;
        private final IntArraySet components = new IntArraySet();

        // TODO: instead of just 'components', allow a query to consist of
        //     'accessed' components, non-accessed 'with' components and
        //     'without' components like, for example, Bevy;
        //     try representing such queries it as a single long array, where
        //     first value contains indices where 'with' and 'without' bitsets
        //     start, so like this in pseudo-code:
        //     long[] query = new long[] {
        //         (withoutComponentsOffset << 32L) | withComponentsOffset,
        //         [1]: ...accessedComponents,
        //         [withComponentsOffset]: ...withComponents,
        //         [withoutComponentsOffset]: ...withoutComponents
        //     }
        //     A class like `Queries` would contain utilities to work with such
        //     arrays:
        //     - boolean matches(long[] query, long[] componentMask)
        //     - class Builder { components: IntSet; with: IntSet; without: IntSet; long[] build() }
        //         - void register(World world) - alternatively or in addition to "build"
        //     - Queries.Builder builder()
        //     - Queries.Builder toMutable(long[] query)
        //     Instances of 'queries' may be registered per world, likely maps
        //     from query ID to an instance and its users will be maintained.

        public Builder(SystemHandler system) {
            this.system = system;
        }

        public Builder with(int component) {
            this.components.add(component);
            return this;
        }

        public Builder with(int component1, int component2) {
            this.components.add(component1);
            this.components.add(component2);
            return this;
        }

        public Builder with(int component1, int component2, int component3) {
            this.components.add(component1);
            this.components.add(component2);
            this.components.add(component3);
            return this;
        }

        public Builder with(int... components) {
            for (int component : components) {
                this.components.add(component);
            }
            return this;
        }

        public Builder with(IntCollection components) {
            this.components.addAll(components);
            return this;
        }

        public SystemState build(World world) {
            long[] mask = BitSets.encode(this.components);
            return new SystemState(this.system, mask, world.getMatchingArchetypes(mask));
        }
    }
}
