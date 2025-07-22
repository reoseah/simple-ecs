package io.github.reoseah.ecs;

import java.util.ArrayList;
import java.util.List;

public class Schedule {
    private final World world;
    private final List<SystemState> systems;

    public Schedule(World world) {
        this.world = world;
        this.systems = new ArrayList<>();
    }

    public void run() {
        for (var system : this.systems) {
            system.run(this.world);
        }
    }

    public void addSystem(SystemState.Builder builder) {
        var system = builder.build(this.world);
        this.systems.add(system);
        this.world.addSystem(system);
    }
}
