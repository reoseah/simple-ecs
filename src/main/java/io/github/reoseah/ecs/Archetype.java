package io.github.reoseah.ecs;

import java.util.Arrays;

public final class Archetype {
    private static final int DEFAULT_CAPACITY = 16; // same as ArrayList#DEFAULT_CAPACITY
    private static final float DEFAULT_LOAD_FACTOR = 1.5F;

    private final int[] componentMask;

    private final int[] componentMap;
    private final Object[] componentData;
    private int[] entityMap;
    private int nextIndex;
    private int capacity;
    private int[] recycledIndices;
    private int recycledIndicesCount;

    public Archetype(int[] componentMask) {
        this.componentMask = componentMask;
        int size = 0;
        for (int maskFragment : componentMask) {
            size += Integer.bitCount(maskFragment);
        }
        this.componentMap = new int[size];
        this.componentData = new Object[size];

        this.capacity = DEFAULT_CAPACITY;
        this.entityMap = new int[this.capacity];
        this.nextIndex = 0;

        var count = 0;
        for (int i = 0; i < componentMask.length * 32; i++) {
            int arrayIdx = i / 32;
            int bitIdx = i % 32;

            if ((componentMask[arrayIdx] & (1 << bitIdx)) != 0) {
                this.componentMap[count] = i;
                // TODO: customize data representing a component, not just ints
                this.componentData[count] = new int[this.capacity];
                count++;
            }
        }
        this.recycledIndices = new int[DEFAULT_CAPACITY];
        this.recycledIndicesCount = 0;
    }

    public Archetype add(int entityId) {
        if (this.recycledIndicesCount > 0) {
            int idx = this.recycledIndices[this.recycledIndicesCount - 1];
            this.entityMap[idx] = entityId;
            this.recycledIndicesCount--;
        } else {
            if (this.nextIndex == this.capacity) {
                this.grow();
            }
            this.entityMap[this.nextIndex] = entityId;
            this.nextIndex++;
        }

        return this;
    }

    private void grow() {
        int newCapacity = (int) (this.capacity * DEFAULT_LOAD_FACTOR);
        this.capacity = newCapacity;

        this.entityMap = Arrays.copyOf(this.entityMap, newCapacity);

        for (int i = 0; i < this.componentMap.length; i++) {
            Object data = this.componentData[i];

            // TODO: customize data representing a component, not just ints
            Object newData = Arrays.copyOf((int[]) data, newCapacity);
            this.componentData[i] = newData;
        }
    }

    public Archetype remove(int entityId) {
        for (int i = 0; i < this.nextIndex; i++) {
            if (this.entityMap[i] == entityId) {
                this.entityMap[i] = -1;

                if (this.recycledIndices.length == this.recycledIndicesCount) {
                    int newCapacity = (int) (this.recycledIndices.length * DEFAULT_LOAD_FACTOR);
                    this.recycledIndices = Arrays.copyOf(this.recycledIndices, newCapacity);
                }

                this.recycledIndices[this.recycledIndicesCount] = entityId;
                this.recycledIndicesCount++;

                return this;
            }
        }
        throw new IllegalArgumentException("Entity " + entityId + " is not present in this archetype.");
    }

    public int get(int entityId, int componentId) {
        int componentIndex = -1;

        for (int i = 0; i < this.componentMap.length; i++) {
            if (this.componentMap[i] == componentId) {
                componentIndex = i;
            }
        }
        if (componentIndex == -1) {
            throw new IllegalArgumentException("Component " + componentId + " is not present in this archetype.");
        }

        for (int i = 0; i < this.nextIndex; i++) {
            if (this.entityMap[i] == entityId) {
                var data = this.componentData[componentIndex];

                // TODO: customize data representing a component, not just ints
                //       abstract corresponding logic away into a component type
                return ((int[]) data)[i];
            }
        }
        throw new IllegalArgumentException("Entity " + entityId + " is not present in this archetype.");
    }
}
