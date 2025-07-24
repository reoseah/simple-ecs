package io.github.reoseah.ecs;

import java.util.List;

// I'd like to call it just System, but that name is already taken by java.lang.System
@FunctionalInterface
public interface SystemRunnable {
    void run(List<Archetype> archetypes, World world);
}
