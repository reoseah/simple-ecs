package io.github.reoseah.ecs;

import it.unimi.dsi.fastutil.ints.IntList;

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


}
