package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import io.github.reoseah.ecs.graphs.TarjanScc;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/// A container of the ECS systems that executes them in parallel based on the
/// components they access and explicit dependencies.
public class MultithreadedSchedule extends Schedule {
    private static final Logger LOGGER = Logger.getLogger(MultithreadedSchedule.class.getName());

    private final ExecutorService threadPool;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition systemCompleted = lock.newCondition();

    /// Array parallel to [Schedule#systems] where values contain the number of
    /// dependencies a particular system has.
    ///
    /// This is rebuilt if [Schedule#systemsChanged] is true. At the beginning
    /// of [#run], it's copied to [#remainingDependenciesCount].
    private int[] dependenciesCount = new int[8];

    /// Array parallel to [Schedule#systems] where values contain IDs of systems
    /// that require that system to run first, may be `null` if there's none.
    ///
    /// This is rebuilt if [Schedule#systemsChanged] is true. When a system
    /// completes, values in [#remainingDependenciesCount] of the systems
    /// depending on it are decremented using this state.
    private @Nullable IntList[] dependents = new IntList[8];

    /// Array parallel to [Schedule#system] with numbers of dependencies
    /// remaining to run a system.
    ///
    /// It's copied at the start of [#run] from [#dependenciesCount], and the
    /// values are decremented when a dependency system completes. When a value
    /// reaches zero, the corresponding system is added to [#readySystems].
    private int[] remainingDependenciesCount = new int[8];

    /// Bitset of component IDs that are used by the currently running systems.
    /// A system can be run if its "writes" set and this do not intersect.
    private long[] totalReadsAndWrites = new long[8];

    /// Bitset with IDs of systems that are ready to be run.
    private long[] readySystems = new long[8];
    /// Bitset with IDs of systems that are currently running.
    private long[] runningSystems = new long[8];
    /// Bitset with IDs of systems that have finished.
    private long[] completedSystems = new long[8];

    public MultithreadedSchedule(World world, ExecutorService threadPool) {
        super(world);
        this.threadPool = threadPool;
    }

    @Override
    public void run() {
        this.lock.lock();
        try {
            if (this.systemsChanged) {
                this.processDependencyGraph();
                this.resizePerRunState();
                this.systemsChanged = false;
            }

            this.resetPerRunState();

            while (BitSets.count(this.completedSystems) < this.systems.size()) {
                var systemsRemain = tryScheduleSystems();
                if (systemsRemain) {
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
        if (this.dependenciesCount.length < this.systems.size()) {
            int newLength = this.dependenciesCount.length * 2;
            while (newLength < this.systems.size()) {
                newLength *= 2;
            }
            this.dependenciesCount = new int[newLength];
            this.dependents = new IntList[newLength];
        } else {
            Arrays.fill(this.dependenciesCount, 0);
            for (var dependent : this.dependents) {
                if (dependent != null) {
                    dependent.clear();
                }
            }
        }

        var sccList = TarjanScc.getStronglyConnectedComponents(this.systems.size(), Arrays.asList(this.dependencies));

        checkForCycles(sccList);

        for (var scc : sccList) {
            int system = scc[0];

            var systemState = this.systems.get(system);
            if (systemState.readsAndWrites != null && systemState.readsAndWrites.length > this.totalReadsAndWrites.length) {
                this.totalReadsAndWrites = new long[systemState.readsAndWrites.length];
            }

            var dependencies = this.dependencies[system];
            if (dependencies == null) {
                continue;
            }
            for (int i = 0; i < dependencies.size(); i++) {
                var dependency = dependencies.getInt(i);

                this.dependenciesCount[system]++;

                var dependentEntry = this.dependents[dependency];
                if (dependentEntry == null) {
                    dependentEntry = new IntArrayList();
                    this.dependents[dependency] = dependentEntry;
                }
                dependentEntry.add(system);
            }
        }
    }

    private void resizePerRunState() {
        int requiredLength = BitSets.getRequiredLength(this.systems.size());
        if (requiredLength > this.readySystems.length) {
            this.readySystems = new long[requiredLength];
            this.runningSystems = new long[requiredLength];
            this.completedSystems = new long[requiredLength];
        }

        if (this.remainingDependenciesCount.length < this.dependenciesCount.length) {
            this.remainingDependenciesCount = new int[this.dependenciesCount.length];
        }
    }

    private void resetPerRunState() {
        Arrays.fill(this.readySystems, 0);
        Arrays.fill(this.runningSystems, 0);
        Arrays.fill(this.completedSystems, 0);
        System.arraycopy(this.dependenciesCount, 0, this.remainingDependenciesCount, 0, this.dependenciesCount.length);

        for (int i = 0; i < this.systems.size(); i++) {
            if (this.remainingDependenciesCount[i] == 0) {
                BitSets.add(this.readySystems, i);
            }
        }
    }

    private boolean tryScheduleSystems() {
        boolean systemsRemain = false;
        for (var system = BitSets.nextSetBit(this.readySystems, 0); system != -1; system = BitSets.nextSetBit(this.readySystems, system + 1)) {
            if (BitSets.contains(this.completedSystems, system) ||
                    BitSets.contains(this.runningSystems, system)) {
                systemsRemain = true;
                continue;
            }

            var systemState = this.systems.get(system);

            this.rebuildComponentReadsAndWrites();
            if (!this.canRun(systemState)) {
                systemsRemain = true;
                continue;
            }

            BitSets.add(this.runningSystems, system);
            this.threadPool.submit(() -> {
                // TODO: possibly implement Runnable on SystemState, pass it instead of
                //    creating these small arrow functions?
                try {
                    systemState.runnable.run(systemState.archetypes, this.world);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Exception while running system " + systemState.id + " (" + systemState.runnable + ")" + ": ", e);
                } finally {
                    this.lock.lock();
                    try {
                        BitSets.add(this.completedSystems, systemState.id);
                        BitSets.remove(this.runningSystems, systemState.id);
                        var dependents = this.dependents[systemState.id];
                        if (dependents != null) {
                            for (var dependent : dependents) {
                                this.remainingDependenciesCount[dependent]--;
                                if (this.remainingDependenciesCount[dependent] == 0) {
                                    BitSets.add(this.readySystems, dependent);
                                }
                            }
                        }

                        this.systemCompleted.signalAll();
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Exception while signaling completion of system " + systemState.id + " (" + systemState.runnable + ")" + ": ", e);
                    } finally {
                        this.lock.unlock();
                    }
                }
            });
        }
        return systemsRemain;
    }

    private void rebuildComponentReadsAndWrites() {
        Arrays.fill(this.totalReadsAndWrites, 0);

        for (int i = BitSets.nextSetBit(this.runningSystems, 0); i != -1; i = BitSets.nextSetBit(this.runningSystems, i + 1)) {
            var systemState = this.systems.get(i);
            if (systemState.readsAndWrites != null) {
                for (var i2 = 0; i2 < systemState.readsAndWrites.length; i2++) {
                    this.totalReadsAndWrites[i2] |= systemState.readsAndWrites[i2];
                }
            }
        }
    }

    private boolean canRun(ScheduleSystem system) {
        // assumes that this.readsAndWrites is up to date, i.e., this.rebuildComponentReadsAndWrites was called and no changes happened since
        return BitSets.isDisjoint(system.writes, this.totalReadsAndWrites);
    }
}
