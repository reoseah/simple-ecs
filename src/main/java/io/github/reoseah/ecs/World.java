package io.github.reoseah.ecs;

import java.util.ArrayList;

public class World {
    private final ArrayList<Component<?>> components = new ArrayList<>();
    private int nextEntity = 0;

    public int register(Component<?> component) {
        int idx = this.components.size();
        this.components.add(component);
        return idx;
    }

    public Component<?> getComponent(int id) {
        return this.components.get((id));
    }

    public int createEntity() {
        return this.nextEntity++;
    }
}
