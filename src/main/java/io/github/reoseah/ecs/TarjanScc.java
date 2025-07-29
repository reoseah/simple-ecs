package io.github.reoseah.ecs;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;

import java.util.ArrayList;
import java.util.List;

public class TarjanScc {
    /// Returns each strongly connected component in their postorder (reverse
    /// topological sort). Assumes the node numbers are linearly generated
    /// starting at zero, so the graph adjacency representation is just a list
    /// of nodes the current index is connected to. The extra `size` param is
    /// so there's no need to pre-allocate values in `neighbors`.
    ///
    /// Inspired by [`TarjanScc` from Bevy](https://docs.rs/bevy_ecs/0.16.1/src/bevy_ecs/schedule/graph/tarjan_scc.rs.html),
    /// and is a Pierce's memory efficient version of the Tarjan's algorithm.
    public static ArrayList<int[]> getStronglyConnectedComponents(int size, List<IntList> neighbors) {
        var components = new ArrayList<int[]>();

        // Pierce's variant of Tarjan's algorithm needs only one number per
        // graph node instead of two.
        var rootIndices = new int[size];
        var sccIndex = 1;
        var componentId = Integer.MAX_VALUE;

        var dfsStack = new IntArrayList();
        // we use 31 bits for node index and 1 bit for whether it can be a "root"
        var queueStack = new IntArrayList();

        var nodeIter = 0;
        while (nodeIter < size) {
            if (queueStack.isEmpty()) {
                var node = nodeIter++;
                var visited = rootIndices[node] != 0;
                if (!visited) {
                    queueStack.add((node & 0x7FFF_FFFF) | 0x8000_0000);
                }
            }
            dfs:
            while (!queueStack.isEmpty()) {
                var queued = queueStack.popInt();
                var node = queued & 0x7FFF_FFFF;
                var isLocalRoot = (queued & 0x8000_0000) != 0;

                if (rootIndices[node] == 0) {
                    rootIndices[node] = sccIndex;
                    sccIndex++;
                }

                var nodeNeighbors = neighbors.size() > node ? neighbors.get(node) : null;
                if (nodeNeighbors != null && !nodeNeighbors.isEmpty()) {
                    for (int i = 0; i < nodeNeighbors.size(); i++) {
                        var neighbor = nodeNeighbors.getInt(i);

                        if (rootIndices[neighbor] == 0) {
                            queueStack.push(queued);
                            queueStack.push((neighbor & 0x7FFF_FFFF) | 0x8000_0000);

                            continue dfs;
                        }

                        if (rootIndices[neighbor] < rootIndices[node]) {
                            rootIndices[node] = rootIndices[neighbor];
                            isLocalRoot = false;
                        }
                    }
                }

                if (!isLocalRoot) {
                    dfsStack.push(node);
                    break;
                }

                int i;
                for (i = dfsStack.size() - 1; i >= 0; i--) {
                    var stackNode = dfsStack.getInt(i);

                    if (rootIndices[node] > rootIndices[stackNode]) {
                        break;
                    } else {
                        rootIndices[stackNode] = componentId;
                    }
                }
                rootIndices[node] = componentId;

                sccIndex -= dfsStack.size() - i - 1;
                componentId -= 1;

                int sccStart = i + 1;
                int sccLength = dfsStack.size() - sccStart + 1;

                int[] scc = new int[sccLength];
                scc[0] = node;
                dfsStack.getElements(sccStart, scc, 1, sccLength - 1);
                dfsStack.removeElements(sccStart, dfsStack.size());

                components.add(scc);
            }
        }
        return components;
    }
}

