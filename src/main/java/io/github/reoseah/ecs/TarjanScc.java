package io.github.reoseah.ecs;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.ArrayList;
import java.util.List;


public class TarjanScc {
    /// Returns each strongly connected component in their postorder (reverse
    /// topological sort).
    ///
    /// Based on [`TarjanScc` from Bevy](https://docs.rs/bevy_ecs/0.16.1/src/bevy_ecs/schedule/graph/tarjan_scc.rs.html).
    /// 
    /// @param indices map from node keys to their "dense" indices
    /// @param neighbors list of connected nodes, indexed using `indices` param
    public static ArrayList<int[]> getStronglyConnectedComponents(Int2IntMap indices, List<IntList> neighbors) {
        var components = new ArrayList<int[]>();

        var rootIndices = new int[indices.size()];
        var sccIndex = 1;
        var componentId = Integer.MAX_VALUE;

        var queue = new IntArrayList();
        var stack = new IntArrayList();

        var entries = indices.int2IntEntrySet().iterator();
        while (entries.hasNext()) {
            if (queue.isEmpty()) {
                var entry = entries.next();
                var visited = rootIndices[entry.getIntValue()] != 0;
                if (!visited) {
                    queue.add((entry.getIntKey() & 0x7FFF_FFFF) | 0x8000_0000);
                }
            }
            dfs:
            while (!queue.isEmpty()) {
                var queued = queue.popInt();
                var node = queued & 0x7FFF_FFFF;
                var isLocalRoot = (queued & 0x8000_0000) != 0;

                var nodeIndex = indices.get(node);
                if (rootIndices[nodeIndex] == 0) {
                    rootIndices[nodeIndex] = sccIndex;
                    sccIndex++;
                }

                var nodeNeighbors = neighbors.get(nodeIndex);
                for (int i = 0; i < nodeNeighbors.size(); i++) {
                    var neighbor = nodeNeighbors.getInt(i);

                    var neighborIndex = indices.get(neighbor);
                    if (rootIndices[neighborIndex] == 0) {
                        queue.push(queued);
                        queue.push((neighbor & 0x7FFF_FFFF) | 0x8000_0000);

                        continue dfs;
                    }

                    if (rootIndices[neighborIndex] < rootIndices[nodeIndex]) {
                        rootIndices[nodeIndex] = rootIndices[neighborIndex];
                        isLocalRoot = false;
                    }
                }

                if (!isLocalRoot) {
                    stack.push(node);
                    break;
                }

                int i;
                for (i = stack.size() - 1; i >= 0; i--) {
                    var stackEntry = stack.getInt(i);
                    var index = indices.get(stackEntry);

                    if (rootIndices[nodeIndex] > rootIndices[index]) {
                        break;
                    } else {
                        rootIndices[index] = componentId;
                    }
                }
                rootIndices[nodeIndex] = componentId;

                sccIndex -= stack.size() - i - 1;
                componentId -= 1;

                int start = i + 1;
                int[] scc = new int[stack.size() - start + 1];
                scc[0] = node;
                stack.getElements(start, scc, 1, scc.length - 1);

                components.add(scc);
                stack.removeElements(start, stack.size());
            }
        }
        return components;
    }
}

