package io.github.reoseah.ecs;

import java.util.ArrayList;
import java.util.List;

public class Schedule {
    private final World world;
    private final List<SystemState> systems;
    // TODO: use query information to order/parallelize multiple systems

    public Schedule(World world) {
        this.world = world;
        this.systems = new ArrayList<>();
    }

    public void run() {
        for (var system : this.systems) {
            system.run(this.world);
        }
    }

    public void addSystem(long[] query, SystemRunnable handler) {
        var system = new SystemState(handler, query, this.world.getQueryArchetypes(query));
        this.systems.add(system);
    }
}
