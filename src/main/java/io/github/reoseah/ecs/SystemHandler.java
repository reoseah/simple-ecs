package io.github.reoseah.ecs;

import java.util.List;

// I'd like to call it just `System`, but that name is already taken by Java
@FunctionalInterface
public interface SystemHandler {
    void execute(List<Archetype> archetypes, World world);
}
