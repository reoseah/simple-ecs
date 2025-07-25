package io.github.reoseah.ecs;

import io.github.reoseah.ecs.bitmanipulation.BitSets;
import io.github.reoseah.ecs.bitmanipulation.Queries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class QueriesTest {
    @Test
    void testQueryBuilder() {
        var builder = Queries.builder()
                .use(1, 2)
                .require(3, 4)
                .exclude(5, 6);

        long[] query = builder.build();

        long[] expected = new long[]{
                (1L << 32L) | 1L, // all values are under 64, so would fit in just one 'long'
                (1 << 1) | (1 << 2),
                (1 << 3) | (1 << 4),
                (1 << 5) | (1 << 6)
        };

        assertArrayEquals(expected, query);
    }

    @Test
    void testQueryMatching() {
        // Create a query that uses components 1,2, requires 3,4 to also be present,
        // and 5,6 to not be present
        long[] query = new Queries.Builder()
                .use(1, 2)
                .require(3, 4)
                .exclude(5, 6)
                .build();

        // matching entity bits (has 1,2,3,4 and not 5,6)
        long[] matchingBits = BitSets.of(1, 2, 3, 4);

        // non-matching entity bits (has 5 which should be excluded)
        long[] nonMatchingBits = BitSets.of(1, 2, 3, 4, 5);

        assertTrue(Queries.matches(query, matchingBits));
        assertFalse(Queries.matches(query, nonMatchingBits));
    }

    @Test
    void testEmptyQuery() {
        long[] query = Queries.of();

        // empty query should match empty bitset
        assertTrue(Queries.matches(query, new long[1]));
    }
}
