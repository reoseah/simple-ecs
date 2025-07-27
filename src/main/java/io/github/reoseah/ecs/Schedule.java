package io.github.reoseah.ecs;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Schedule {
    private final World world;
    private final List<SystemState> systems;
    /// Dependencies of a system, ordered in parallel to [#systems].
    private final List<IntList> dependencies = new ArrayList<>();

    public Schedule(World world) {
        this.world = world;
        this.systems = new ArrayList<>();
    }

    public void run() {
        for (var system : this.systems) {
            system.run(this.world);
        }
    }

    public int add(long[] query, SystemRunnable handler) {
        var system = new SystemState(handler, query, this.world.getQueryArchetypes(query));
        this.systems.add(system);
        return this.systems.size() - 1;
    }

    /// Adds a system to this schedule and returns its numeric id.
    public int add(SystemConfig config) {
        if (config.dependencies != null) {
            for (int dependency : config.dependencies) {
                if (dependency >= this.systems.size()) {
                    throw new IllegalArgumentException("System " + dependency + " does not exist.");
                }
            }
        }
        if (config.dependents != null) {
            for (int dependent : config.dependents) {
                if (dependent >= this.systems.size()) {
                    throw new IllegalArgumentException("System " + dependent + " does not exist.");
                }
            }
        }
        int system = this.add(config.query, config.handler);

        if (config.dependencies != null) {
            this.dependencies.set(system, config.dependencies);
        }
        if (config.dependents != null) {
            for (int dependent : config.dependents) {
                var list = this.dependencies.get(dependent);
                if (list == null) {
                    list = new IntArrayList();
                    this.dependencies.set(dependent, list);
                }
                list.add(system);
            }
        }
        return system;
    }

    public static class SystemConfig {
        SystemRunnable handler;
        // TODO: allow to pass component IDs as alternative, build bit mask
        long[] query;

        @Nullable IntList dependencies;
        @Nullable IntList dependents;

        public static SystemConfig create(SystemRunnable handler) {
            var config = new SystemConfig();
            config.handler = handler;
            return config;
        }

        public SystemConfig query(long... query) {
            this.query = query;
            return this;
        }

        public SystemConfig after(int... dependencies) {
            this.dependencies = IntList.of(dependencies);
            return this;
        }

        public SystemConfig before(int... dependents) {
            this.dependents = IntList.of(dependents);
            return this;
        }
    }
}
