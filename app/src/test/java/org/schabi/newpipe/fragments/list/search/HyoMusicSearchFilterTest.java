/*
 * SPDX-FileCopyrightText: 2026 HyoMusic contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments.list.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HyoMusicSearchFilterTest {
    @Test
    public void onlyMusicChannelAndPlaylistFiltersAreExposed() {
        assertTrue(SearchFragment.isHyoMusicSearchFilter("all"));
        assertTrue(SearchFragment.isHyoMusicSearchFilter("music_songs"));
        assertTrue(SearchFragment.isHyoMusicSearchFilter("channels"));
        assertTrue(SearchFragment.isHyoMusicSearchFilter("playlists"));
        assertFalse(SearchFragment.isHyoMusicSearchFilter("videos"));
        assertFalse(SearchFragment.isHyoMusicSearchFilter("music_videos"));
        assertFalse(SearchFragment.isHyoMusicSearchFilter("music_albums"));
    }
}
