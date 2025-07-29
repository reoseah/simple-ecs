package io.github.reoseah.ecs.schedule;

import io.github.reoseah.ecs.SystemRunnable;
import io.github.reoseah.ecs.TarjanScc;
import io.github.reoseah.ecs.bitmanipulation.BitSets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/// A container of the ECS systems that executes them in parallel based on the
/// components they access and explicit dependencies.
public class MultithreadedSchedule {
    // TODO: store World reference and other state that may be needed
    private final List<SystemState> systems = new ArrayList<>();
    /// Adjacency representation of the graph formed by system dependencies.
    /// Indexed by system ID. Empty values are not created and kept as `null`.
    private final List<@Nullable IntList> graph = new ArrayList<>();
    /// True if [#systems] or [#graph] were modified and derived data needs to
    /// be rebuilt.
    private boolean dirty;

    /// Number of systems the system depends on. Indexed by system ID.
    /// Copied to [#remainingDependenciesCount] at the start of every run.
    private final IntList dependencyCounts = new IntArrayList();
    /// List of systems that depend on the system. Indexed by system ID.
    /// Used to update [#remainingDependenciesCount] when a system completes.
    private final List<IntList> dependents = new ArrayList<>();

    private final ExecutorService threadPool;

    /// Lock for data concurrently modified when running the schedule, such as
    /// [#runningSystems], [#totalReadsAndWrites], [#remainingDependenciesCount].
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition systemCompleted = lock.newCondition();

    /// Number of dependencies remaining to run a system, indexed by system ID.
    private int[] remainingDependenciesCount = new int[8];

    /// Bitset with components used by currently running systems.
    /// A system can be run if its "writes" do not intersect with all reads and
    /// writes of the already running systems.
    private long[] totalReadsAndWrites = new long[8];

    /// Bitset with IDs of systems that are ready to be run.
    private long[] readySystems = new long[8];
    /// Bitset with IDs of systems that are currently running.
    private long[] runningSystems = new long[8];
    /// Bitset with IDs of systems that have finished.
    private long[] completedSystems = new long[8];

    public MultithreadedSchedule(ExecutorService threadPool) {
        this.threadPool = threadPool;
    }

    /// Returns a helper object to configure and add a system to the schedule.
    public SystemConfig configure(SystemRunnable runnable) {
        return new SystemConfig(this).runnable(runnable);
    }

    public void run() {
        this.lock.lock();
        try {
            if (this.dirty) {
                this.processDependencyGraph();
                this.dirty = false;
            }
            this.prepareRunData();

            while (BitSets.count(this.completedSystems) < this.systems.size()) {
                tryScheduleSystems();
                if (BitSets.count(this.completedSystems) < this.systems.size()) {
                    this.systemCompleted.await();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            this.lock.unlock();
        }
    }

    private void processDependencyGraph() {
//        this.dependencyCounts.clear();
        for (int i = 0; i < this.dependencyCounts.size(); i++) {
            this.dependencyCounts.set(i, 0);
            this.dependents.set(i, new IntArrayList()); // FIXME: wip dirty hack
        }
//        this.dependents.clear(); // TODO: clear sublists only, avoid tiny bit of garbage creation?

        var sccList = TarjanScc.getStronglyConnectedComponents(this.systems.size(), this.graph);
        for (var scc : sccList) {
            if (scc.length > 1) {
                System.err.println("Found dependency cycle between systems, skipping... IDs: " + Arrays.toString(scc));
                // TODO: attach friendly names to systems, use in error messages
            }

            int systemId = scc[0];

            var system = this.systems.get(systemId);
            if (system.readsAndWrites != null && system.readsAndWrites.length > this.totalReadsAndWrites.length) {
                this.totalReadsAndWrites = new long[system.readsAndWrites.length];
            }

            var adjacency = this.graph.size() > systemId ? this.graph.get(systemId) : null;
            if (adjacency == null) {
                continue;
            }
            for (int i = 0; i < adjacency.size(); i++) {
                var dependency = adjacency.getInt(i);
                this.dependencyCounts.set(systemId, this.dependencyCounts.getInt(systemId) + 1);

                var dependentEntry = this.dependents.get(systemId);
                if (dependentEntry == null) {
                    dependentEntry = new IntArrayList();
                    this.dependents.add(dependentEntry);
                }
                dependentEntry.add(dependency);
            }
        }

        int requiredLength = ((this.systems.size() - 1) / 64) + 1;
        if (requiredLength > this.runningSystems.length) {
            this.readySystems = new long[requiredLength];
            this.runningSystems = new long[requiredLength];
            this.completedSystems = new long[requiredLength];
        }
        if (this.remainingDependenciesCount.length < this.systems.size()) {
            this.remainingDependenciesCount = new int[this.systems.size()];
        }
    }

    private void prepareRunData() {
        Arrays.fill(this.readySystems, 0);
        Arrays.fill(this.runningSystems, 0);
        Arrays.fill(this.completedSystems, 0);
        this.dependencyCounts.getElements(0, this.remainingDependenciesCount, 0, this.dependencyCounts.size());

        for (int i = 0; i < this.systems.size(); i++) {
            if (this.remainingDependenciesCount[i] == 0) {
                BitSets.set(this.readySystems, i);
            }
        }
    }

    private boolean tryScheduleSystems() {
        boolean scheduled = false;

        for (var system = BitSets.nextSetBit(this.readySystems, 0); system != -1; system = BitSets.nextSetBit(this.readySystems, system + 1)) {
            if (BitSets.has(this.completedSystems, system) ||
                    BitSets.has(this.runningSystems, system)) {
                continue;
            }

            var systemState = this.systems.get(system);

            this.rebuildComponentReadsAndWrites();
            if (this.canRun(systemState)) {
                BitSets.set(this.runningSystems, system);
                scheduled = true;

                this.threadPool.submit(() -> {
                    // TODO: possibly implement Runnable on SystemState, pass it instead of
                    //    creating these small arrow functions?
                    try {
                        systemState.runnable.run(null, null /* TODO pass the world and component data */);
                    } finally {
                        this.lock.lock();
                        try {
                            BitSets.set(this.completedSystems, systemState.id);
                            BitSets.unset(this.runningSystems, systemState.id);
                            var dependents = this.dependents.size() > systemState.id ? this.dependents.get(systemState.id) : null;
                            if (dependents != null) {
                                for (var dependent : this.dependents.get(systemState.id)) {
                                    this.remainingDependenciesCount[dependent]--;
                                    if (this.remainingDependenciesCount[dependent] == 0) {
                                        BitSets.set(this.readySystems, dependent);
                                    }
                                }
                            }

                            this.systemCompleted.signalAll();
                        } catch (Exception e) {
                            System.err.println("Exception while notifying system completion");
                            e.printStackTrace();
                        } finally {
                            this.lock.unlock();
                        }
                    }
                });
            }
        }

        return scheduled;
    }

    private void rebuildComponentReadsAndWrites() {
        Arrays.fill(this.totalReadsAndWrites, 0);

        // Iterate through bits in runningSystems
        for (int wordIndex = 0; wordIndex < runningSystems.length; wordIndex++) {
            long word = runningSystems[wordIndex];
            if (word == 0) {
                continue;
            }

            // TODO: use efficient nextSetBit implementation?
            for (int bitIndex = 0; bitIndex < 64; bitIndex++) {
                if ((word & (1L << bitIndex)) != 0) {
                    int systemIndex = wordIndex * 64 + bitIndex;
                    if (systemIndex >= systems.size()) break;

                    var system = systems.get(systemIndex);
                    if (system.readsAndWrites != null) {
                        if (system.readsAndWrites.length > this.totalReadsAndWrites.length) {
                            this.totalReadsAndWrites = Arrays.copyOf(
                                    this.totalReadsAndWrites,
                                    system.readsAndWrites.length
                            );
                        }
                        for (var i = 0; i < system.readsAndWrites.length; i++) {
                            this.totalReadsAndWrites[i] |= system.readsAndWrites[i];
                        }
                    }
                }
            }
        }
    }


    private boolean canRun(SystemState system) {
        // assumes that this.readsAndWrites is up to date, i.e., this.rebuildComponentReadsAndWrites was called and no changes happened since
        return BitSets.isDisjoint(system.writes, this.totalReadsAndWrites);
    }

    public static final class SystemState {
        public final int id;
        private final SystemRunnable runnable;
        private final long[] writes;
        private final long[] readsAndWrites;

        public SystemState(int id, SystemRunnable runnable, long[] writes, long[] readsAndWrites) {
            this.id = id;
            this.runnable = runnable;
            this.writes = writes;
            this.readsAndWrites = readsAndWrites;
        }
    }

    @Accessors(fluent = true)
    public static final class SystemConfig {
        private final MultithreadedSchedule schedule;
        @Setter
        private SystemRunnable runnable;
        private @Nullable IntSet dependencies;
        private @Nullable IntSet dependents;
        private long @Nullable [] writes;
        private long @Nullable [] readsAndWrites;

        private SystemConfig(MultithreadedSchedule schedule) {
            this.schedule = schedule;
        }

        // TODO: allow to build 'hierarchies' imitating Bevy tuples/system sets,
        //    which would provide tiny optimizations and reduce amount of code
        //    when applying same config to many systems and also with `.chain()`
        //    this would probably use additional `SystemSetConfig` class and
        //    a field to parent set here with corresponding methods and in
        //    .apply() merging configs from all parent sets

        public SystemConfig after(int... systems) {
            this.dependencies = createIntSetOrAddAll(this.dependencies, systems);
            return this;
        }

        public SystemConfig before(int... systems) {
            this.dependents = createIntSetOrAddAll(this.dependents, systems);
            return this;
        }

        public SystemConfig reads(int... components) {
            this.readsAndWrites = BitSets.mergeValues(this.readsAndWrites, components);
            return this;
        }

        public SystemConfig reads(long[] componentMask) {
            this.readsAndWrites = BitSets.merge(this.readsAndWrites, componentMask);
            return this;
        }

        public SystemConfig writes(int... components) {
            this.writes = BitSets.mergeValues(this.writes, components);
            this.readsAndWrites = BitSets.mergeValues(this.readsAndWrites, components);
            return this;
        }

        public SystemConfig writes(long[] componentMask) {
            this.writes = BitSets.merge(this.writes, componentMask);
            this.readsAndWrites = BitSets.merge(this.readsAndWrites, componentMask);
            return this;
        }

        /// Registers the current system and returns its numeric ID.
        public int apply() {
            int id = this.schedule.systems.size();

            var state = new SystemState(id, this.runnable, this.writes, this.readsAndWrites);
            this.schedule.dirty = true;
            this.schedule.systems.add(state);
            // TODO: fix dependency logic
            //  don't create entries if there's no dependencies, will have to not use ArrayList/IntArrayList types
            this.schedule.graph.add(new IntArrayList());
            this.schedule.dependencyCounts.add(0);
            this.schedule.dependents.add(new IntArrayList());
            if (this.dependencies != null) {
//                for (int dependency : this.dependencies) {
//                    var dependencyDependents = this.schedule.graph.get(dependency);
//                    if (dependencyDependents == null) {
//                        dependencyDependents = new IntArrayList();
//                            this.schedule.graph.set(dependency, dependencyDependents);
//                    }
//                    dependencyDependents.add(id);
//                }
            }
            if (this.dependents != null) {
                var dependents = new IntArrayList(this.dependents);

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
}
