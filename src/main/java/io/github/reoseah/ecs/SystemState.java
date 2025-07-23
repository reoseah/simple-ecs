package io.github.reoseah.ecs;

import java.util.List;

public final class SystemState {
    private final SystemFunction system;
    public final long[] query;
    public final List<Archetype> archetypes;

    public SystemState(SystemFunction system, long[] query, List<Archetype> archetypes) {
        this.system = system;
        this.query = query;
        this.archetypes = archetypes;
    }

    public void run(World world) {
        this.system.run(this.archetypes, world);
    }
}
