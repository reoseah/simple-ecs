package io.github.reoseah.ecs;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class World {
    private final List<Component<?>> components = new ArrayList<>();
    private final Map<long[], Archetype> archetypes = new Object2ObjectOpenCustomHashMap<>(new BitSets.HashStrategy());
    // TODO: reuse deleted entities
    private int nextEntity = 0;

    public int register(Component<?> component) {
        int idx = this.components.size();
        this.components.add(component);
        return idx;
    }

    public Component<?> getComponent(int id) {
        return this.components.get((id));
    }

    public int createEmptyEntity() {
        return this.nextEntity++;
    }

    public Archetype.Index createEntity(long[] componentMask) {
        int entity = this.nextEntity++;

        var archetype = this.archetypes.get(componentMask);
        if (archetype == null) {
            archetype = new Archetype(this, componentMask);
            this.archetypes.put(componentMask, archetype);
        }

        return archetype.add(entity);
    }

    // A simple system that will be called with every matching archetype.
    public interface ArchetypeSystem {
        void execute(List<Archetype> archetype);
    }

    private static class ArchetypeSystemEntry {
        public final long[] componentMask;
        public final ArchetypeSystem system;
        public final List<Archetype> archetypes = new ArrayList<>();

        public ArchetypeSystemEntry(long[] componentMask, ArchetypeSystem system) {
            this.componentMask = componentMask;
            this.system = system;
        }
    }

    private final List<ArchetypeSystemEntry> archetypeSystems = new ArrayList<>();

    public void createSystem(long[] componentMask, ArchetypeSystem system) {
        var entry = new ArchetypeSystemEntry(componentMask, system);
        for (var archetypeEntry : this.archetypes.entrySet()) {
            if (BitSets.contains(archetypeEntry.getKey(), componentMask)) {
                entry.archetypes.add(archetypeEntry.getValue());
            }
        }
        this.archetypeSystems.add(entry);
    }

    public void execute() {
        for (var entry : this.archetypeSystems) {
            entry.system.execute(entry.archetypes);
        }
    }
}
