package io.github.reoseah.ecs.bitmanipulation;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntSet;

/// Query masks are `long[]` containing three bitsets inside. The first value
/// in such array stores the sizes of the first two bitsets in its upper and 
/// lower halves.
///
/// The first bit set is "used" components, you are querying them; the second -
/// "required" components, needed for the query to match, but are not used; the
/// third is "excluded", must not be present to match.
///
/// ## Example
/// ```java
/// // a query that uses components 1 and 2, requires 3 and 4 to also be present,
/// // and 5 and 6 to be absent
///  long[] query = new Queries.Builder()
///      .use(1, 2)
///      .require(3, 4)
///      .exclude(5, 6)
///      .build();
///
///  // matching entity bits (has 1, 2, 3 and 4 and not 5 or 6)
///  long[] matchingBits = BitSets.encode(1, 2, 3, 4);
///
///  // non-matching entity bits (has 5 which should be excluded)
///  long[] nonMatchingBits = BitSets.encode(1, 2, 3, 4, 5);
///
///  assertTrue(Queries.matches(query, matchingBits));
///  assertFalse(Queries.matches(query, nonMatchingBits));
///```
public class Queries {
    /// @param query query, three bitsets stored in one array
    /// @param bits  a bit set of component IDs to match against the query
    /// @return true if `bits` has all bits for `used` and `required` sets and
    ///  none of the `excluded` ones.
    /// @see Queries class Javadoc for description of queries
    public static boolean matches(long[] query, long[] bits) {
        int usedWords = (int) query[0];
        int requiredWords = (int) (query[0] >> 32L);
        int excludedWords = query.length - 1 - usedWords - requiredWords;

        for (int i = 0; i < bits.length; i++) {
            long word = bits[i];
            if (i < usedWords) {
                if ((word & query[i + 1]) != query[i + 1]) {
                    return false;
                }
            }
            if (i < requiredWords) {
                if ((word & query[i + 1 + usedWords]) != query[i + 1 + usedWords]) {
                    return false;
                }
            }
            if (i < excludedWords) {
                if ((word & query[i + 1 + usedWords + requiredWords]) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public static Builder builder() {
        return new Builder();
    }

    /// Shortcut to create a query with just 'used' bitset. Similar to
    /// [BitSets#of] except for the first value.
    public static long[] of(int... components) {
        int max = 0;
        for (int value : components) {
            if (value > max) {
                max = value;
            }
        }
        int size = (max / Long.SIZE) + 1;

        long[] bits = new long[1 + size];
        bits[0] = (max / Long.SIZE) + 1;
        for (int value : components) {
            int index = (value / Long.SIZE);
            int bit = value % Long.SIZE;

            bits[1 + index] |= 1L << bit;
        }
        return bits;
    }

    public static long[] encode(IntCollection used, IntCollection required, IntCollection excluded) {
        int usedMax = 0;
        for (int component : used) {
            usedMax = Math.max(usedMax, component);
        }
        int usedSize = used.isEmpty() ? 0 : 1 + usedMax / Long.SIZE;

        int requiredMax = 0;
        for (int component : required) {
            requiredMax = Math.max(requiredMax, component);
        }
        int requiredSize = required.isEmpty() ? 0 : 1 + requiredMax / Long.SIZE;

        int excludedMax = 0;
        for (int component : excluded) {
            excludedMax = Math.max(excludedMax, component);
        }
        int excludedSize = excluded.isEmpty() ? 0 : 1 + excludedMax / Long.SIZE;

        long[] query = new long[1 + usedSize + requiredSize + excludedSize];
        query[0] = (((long) requiredSize) << 32L) | usedSize;

        for (int value : used) {
            int index = (value / Long.SIZE);
            int bit = value % Long.SIZE;
            query[1 + index] |= 1L << bit;
        }
        for (int value : required) {
            int index = (value / Long.SIZE);
            int bit = value % Long.SIZE;
            query[1 + usedSize + index] |= 1L << bit;
        }
        for (int value : excluded) {
            int index = (value / Long.SIZE);
            int bit = value % Long.SIZE;
            query[1 + usedSize + requiredSize + index] |= 1L << bit;
        }
        return query;
    }

    public static class Builder {
        /// Component IDs that are 'used' by a system registered with the resulting query.
        private final IntSet used = new IntArraySet();
        /// Component IDs that are 'required' but not 'used' by a system registered with the
        /// resulting query. Currently, there's no difference between this and [#used]
        /// in the current implementation, but it could be used in the future to schedule
        /// systems to run in parallel like in, e.g., Bevy.
        private final IntSet required = new IntArraySet();
        /// Component IDs that are required to not be present on entities by a system
        /// registered with the resulting query.
        private final IntSet excluded = new IntArraySet();

        // region Methods for adding to this#used

        /// Adds components that will be accessed by the system (read or write).
        public Builder use(int component) {
            this.used.add(component);
            return this;
        }

        /// Adds components that will be accessed by the system (read or write).
        public Builder use(int component1, int component2) {
            this.used.add(component1);
            this.used.add(component2);
            return this;
        }

        /// Adds components that will be accessed by the system (read or write).
        public Builder use(int component1, int component2, int component3) {
            this.used.add(component1);
            this.used.add(component2);
            this.used.add(component3);
            return this;
        }

        /// Adds components that will be accessed by the system (read or write).
        public Builder use(int... components) {
            for (int component : components) {
                this.used.add(component);
            }
            return this;
        }

        /// Adds components that will be accessed by the system (read or write).
        public Builder use(IntSet components) {
            this.used.addAll(components);
            return this;
        }
        // endregion

        // region Methods for adding to this#required

        /// Requires components to be present on the entity but they will not be accessed.
        public Builder require(int component) {
            this.required.add(component);
            return this;
        }

        /// Requires components to be present on the entity but they will not be accessed.
        public Builder require(int component1, int component2) {
            this.required.add(component1);
            this.required.add(component2);
            return this;
        }

        /// Requires components to be present on the entity but they will not be accessed.
        public Builder require(int component1, int component2, int component3) {
            this.required.add(component1);
            this.required.add(component2);
            this.required.add(component3);
            return this;
        }

        /// Requires components to be present on the entity but they will not be accessed.
        public Builder require(int... components) {
            for (int component : components) {
                this.required.add(component);
            }
            return this;
        }

        /// Requires components to be present on the entity but they will not be accessed.
        public Builder require(IntSet components) {
            this.required.addAll(this.used);
            return this;
        }
        // endregion

        // region Methods for adding to this#excluded

        /// Ensures component is not present on the entity.
        public Builder exclude(int component) {
            this.excluded.add(component);
            return this;
        }

        /// Ensures components are not present on the entity.
        public Builder exclude(int component1, int component2) {
            this.excluded.add(component1);
            this.excluded.add(component2);
            return this;
        }

        /// Ensures components are not present on the entity.
        public Builder exclude(int component1, int component2, int component3) {
            this.excluded.add(component1);
            this.excluded.add(component2);
            this.excluded.add(component3);
            return this;
        }

        /// Ensures components are not present on the entity.
        public Builder exclude(int... components) {
            for (int component : components) {
                this.excluded.add(component);
            }
            return this;
        }

        /// Ensures components are not present on the entity.
        public Builder exclude(IntSet components) {
            this.excluded.addAll(this.used);
            return this;
        }
        // endregion

        public long[] build() {
            return encode(this.used, this.required, this.excluded);
        }
    }
}
