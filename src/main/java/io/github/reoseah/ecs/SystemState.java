package io.github.reoseah.ecs;

import java.util.List;

public final class SystemState {
    private final SystemRunnable runnable;
    public final long[] query;
    public final List<Archetype> archetypes;

    public SystemState(SystemRunnable runnable, long[] query, List<Archetype> archetypes) {
        this.runnable = runnable;
        this.query = query;
        this.archetypes = archetypes;
    }

    public void run(World world) {
        this.runnable.run(this.archetypes, world);
    }
}
