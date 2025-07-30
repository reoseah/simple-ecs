package io.github.reoseah.ecs.schedule;

import io.github.reoseah.ecs.SystemRunnable;
import io.github.reoseah.ecs.bitmanipulation.BitSets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Schedule {
    // TODO: store World reference and other state that may be needed
    /// Systems inside this schedule with their state.
    ///
    /// ### API notes:
    /// - only adding values is correctly handled
    /// - [ScheduleSystem#id] should match position here and in all parallel lists
    /// - when modified, [#systemsChanged] should be set true
    protected final List<ScheduleSystem> systems = new ArrayList<>();
    /// Array parallel to [#systems] where values contain IDs of systems that
    /// should run before and can be `null` for systems without dependencies.
    protected @Nullable IntList[] dependencies = new IntList[8];
    /// True if [#systems] or [#dependencies] were modified and derived data
    /// needs to be rebuilt.
    protected boolean systemsChanged;

    protected static final class ScheduleSystem {
        public final int id;
        public final SystemRunnable runnable;
        public final long @Nullable [] writes;
        public final long @Nullable [] readsAndWrites;

        public ScheduleSystem(int id, SystemRunnable runnable, long[] writes, long[] readsAndWrites) {
            this.id = id;
            this.runnable = runnable;
            this.writes = writes;
            this.readsAndWrites = readsAndWrites;
        }
    }

    @Accessors(fluent = true)
    public static final class ScheduleSystemBuilder {
        private final Schedule schedule;
        @Setter
        private SystemRunnable runnable;
        private @Nullable IntSet dependencies;
        private @Nullable IntSet dependents;
        private long @Nullable [] writes;
        private long @Nullable [] readsAndWrites;

        public ScheduleSystemBuilder(Schedule schedule) {
            this.schedule = schedule;
        }

        // TODO: allow to build 'hierarchies' imitating Bevy tuples/system sets,
        //    which would provide tiny optimizations and reduce amount of code
        //    when applying same config to many systems and also with `.chain()`
        //    this would probably use additional `SystemSetConfig` class and
        //    a field to parent set here with corresponding methods and in
        //    .apply() merging configs from all parent sets

        public ScheduleSystemBuilder after(int... systems) {
            this.dependencies = createIntSetOrAddAll(this.dependencies, systems);
            return this;
        }

        public ScheduleSystemBuilder before(int... systems) {
            this.dependents = createIntSetOrAddAll(this.dependents, systems);
            return this;
        }

        public ScheduleSystemBuilder reads(int... components) {
            this.readsAndWrites = BitSets.mergeValues(this.readsAndWrites, components);
            return this;
        }

        public ScheduleSystemBuilder reads(long[] componentMask) {
            this.readsAndWrites = BitSets.merge(this.readsAndWrites, componentMask);
            return this;
        }

        public ScheduleSystemBuilder writes(int... components) {
            this.writes = BitSets.mergeValues(this.writes, components);
            this.readsAndWrites = BitSets.mergeValues(this.readsAndWrites, components);
            return this;
        }

        public ScheduleSystemBuilder writes(long[] componentMask) {
            this.writes = BitSets.merge(this.writes, componentMask);
            this.readsAndWrites = BitSets.merge(this.readsAndWrites, componentMask);
            return this;
        }

        /// Registers the current system and returns its numeric ID.
        public int apply() {
            int id = this.schedule.systems.size();

            var state = new ScheduleSystem(id, this.runnable, this.writes, this.readsAndWrites);
            this.schedule.systemsChanged = true;
            this.schedule.systems.add(state);
            if (this.dependencies != null) {
                this.schedule.dependencies = MultithreadedSchedule.ensureCapacity(this.schedule.dependencies, id);
                this.schedule.dependencies[id] = new IntArrayList(this.dependencies);
            }
            if (this.dependents != null) {
                for (int dependent : this.dependents) {
                    this.schedule.dependencies = MultithreadedSchedule.ensureCapacity(this.schedule.dependencies, dependent);
                    var dependentDependencies = this.schedule.dependencies[dependent];
                    if (dependentDependencies == null) {
                        dependentDependencies = new IntArrayList();
                        this.schedule.dependencies[dependent] = dependentDependencies;
                    }
                    dependentDependencies.add(id);
                }
            }

            return id;
        }

        private static IntSet createIntSetOrAddAll(@Nullable IntSet set, int... values) {
            if (set == null) {
                return IntArraySet.of(values);
            }
            set.addAll(IntList.of(values));
            return set;
        }
    }

    protected static <T> T[] ensureCapacity(T[] array, int minCapacity) {
        if (array.length < minCapacity) {
            int nextLength = array.length * 2;
            while (nextLength < minCapacity) {
                nextLength *= 2;
            }
            return Arrays.copyOf(array, nextLength);
        }
        return array;
    }


    protected static void checkForCycles(ArrayList<int[]> sccList) {
        var sccWithMoreThanOneSystem = new ArrayList<int[]>();
        for (var scc : sccList) {
            if (scc.length > 1) {
                sccWithMoreThanOneSystem.add(scc);
            }
        }
        if (!sccWithMoreThanOneSystem.isEmpty()) {
            var error = new StringBuilder("Found dependency cycle(s) between systems. Strongly connected components: \n");
            for (var scc : sccWithMoreThanOneSystem) {
                error.append("- ");
                error.append(Arrays.toString(scc)).append(",\n");
                // TODO: allow assigning string names to systems, or use toString on 'runnable's?
                // TODO: perhaps print out "simple loops" in a helpful way, scc bridges
            }
            throw new IllegalStateException(error.toString());
        }
    }
}
