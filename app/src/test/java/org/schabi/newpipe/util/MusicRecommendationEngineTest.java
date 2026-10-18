/*
 * SPDX-FileCopyrightText: 2026 HyoMusic contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MusicRecommendationEngineTest {
    @Test
    public void trendingQueriesIncludeRegionAndMusicSignals() {
        final var queries = MusicRecommendationEngine.buildQueries(
                "BALLAD", MusicRecommendationEngine.Region.KOREA,
                MusicRecommendationEngine.Mode.TRENDING);

        assertEquals(3, queries.size());
        assertTrue(queries.get(0).contains("Korea"));
        assertTrue(queries.get(0).contains("BALLAD"));
        assertTrue(queries.get(0).contains("popular"));
    }

    @Test
    public void newReleaseQueriesDoNotClaimAChart() {
        final var queries = MusicRecommendationEngine.buildQueries(
                "MUSIC", MusicRecommendationEngine.Region.GLOBAL,
                MusicRecommendationEngine.Mode.NEW_RELEASES);

        assertTrue(queries.stream().allMatch(query -> query.contains("new")
                || query.contains("latest")));
        assertTrue(queries.stream().noneMatch(query -> query.contains("TOP 100")));
    }
}
