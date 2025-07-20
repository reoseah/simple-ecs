package io.github.reoseah.ecs;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;

public class Query implements Iterable<Archetype> {
    private final long[] componentMask;
    private final ArrayList<Archetype> archetypes;

    public Query(long[] componentMask) {
        this.componentMask = componentMask;
        this.archetypes = new ArrayList<>();
    }

    @Override
    public @NotNull Iterator<Archetype> iterator() {
        return this.archetypes.iterator();
    }
}
