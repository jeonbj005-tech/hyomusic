/*
 * SPDX-FileCopyrightText: 2026 HyoMusic contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.util;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ranks YouTube search candidates without pretending that search results are an official chart.
 * Official YouTube Data API data is intentionally not assumed when no API key is configured.
 */
public final class MusicRecommendationEngine {
    private static final String TAG = "SimpleTunesRecommend";
    private static final String CACHE_PREFS = "simple_tunes_recommendation_cache";
    private static final long CACHE_TTL_MS = 12 * 60 * 60 * 1000L;
    private static final int MAX_RESULTS = 20;
    private static final Pattern SEPARATOR = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> EXCLUDED_TERMS = new HashSet<>(Arrays.asList(
            "cover", "karaoke", "reaction", "review", "tutorial", "lesson", "lecture",
            "mix", "compilation", "playlist", "radio", "livestream", "live stream", "shorts",
            "반주", "노래방", "커버", "모음", "리액션", "리뷰", "강의", "라이브", "쇼츠", "믹스",
            "remix"));
    private static final Map<String, List<String>> GENRE_TERMS = new HashMap<>();

    static {
        GENRE_TERMS.put("POP", Arrays.asList("pop", "팝"));
        GENRE_TERMS.put("K-POP", Arrays.asList("k-pop", "kpop", "케이팝", "한국"));
        GENRE_TERMS.put("HIP-HOP", Arrays.asList("hip hop", "hip-hop", "hiphop", "rap", "랩", "힙합"));
        GENRE_TERMS.put("R&B", Arrays.asList("r&b", "rnb", "soul", "알앤비"));
        GENRE_TERMS.put("ROCK", Arrays.asList("rock", "록"));
        GENRE_TERMS.put("LATIN", Arrays.asList("latin", "reggaeton", "salsa", "라틴"));
        GENRE_TERMS.put("EDM", Arrays.asList("edm", "electronic", "dance"));
        GENRE_TERMS.put("BALLAD", Arrays.asList("ballad", "발라드"));
        GENRE_TERMS.put("JAZZ", Arrays.asList("jazz", "재즈"));
        GENRE_TERMS.put("INDIE", Arrays.asList("indie", "인디"));
        GENRE_TERMS.put("CLASSICAL", Arrays.asList("classical", "클래식"));
    }

    private MusicRecommendationEngine() {
    }

    public enum Mode {
        TRENDING("지금 인기"),
        CLASSICS("인기 명곡"),
        NEW_RELEASES("최신곡");

        private final String label;

        Mode(final String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum Region {
        KOREA("한국", "Korea"),
        GLOBAL("글로벌", "global"),
        UNITED_STATES("미국", "United States"),
        JAPAN("일본", "Japan"),
        MEXICO("멕시코", "Mexico"),
        OTHER("기타", "");

        private final String label;
        private final String query;

        Region(final String label, final String query) {
            this.label = label;
            this.query = query;
        }

        public String getLabel() {
            return label;
        }

        public String getQuery() {
            return query;
        }
    }

    public static List<String> buildQueries(final String genre, final Region region,
                                            final Mode mode) {
        final String regionPart = region.getQuery().isEmpty() ? "" : region.getQuery() + " ";
        final String genrePart = genre.equalsIgnoreCase("MUSIC") ? "music" : genre;
        final String base = regionPart + genrePart;
        switch (mode) {
            case CLASSICS:
                return Arrays.asList(base + " greatest songs official audio",
                        base + " classic songs official music video");
            case NEW_RELEASES:
                return Arrays.asList(base + " new music official audio",
                        base + " latest single official music video");
            case TRENDING:
            default:
                return Arrays.asList(base + " popular music official audio",
                        base + " popular official music video",
                        base + " music official audio");
        }
    }

    public static List<StreamInfoItem> rank(final List<StreamInfoItem> candidates,
                                            final String genre, final Mode mode) {
        final Map<String, ScoredItem> bestBySong = new HashMap<>();
        for (final StreamInfoItem item : candidates) {
            final ScoredItem scored = score(item, genre, mode);
            if (scored == null) {
                continue;
            }
            final String key = songKey(item);
            final ScoredItem previous = bestBySong.get(key);
            if (previous == null || scored.score > previous.score) {
                bestBySong.put(key, scored);
            }
        }
        final List<ScoredItem> ranked = new ArrayList<>(bestBySong.values());
        ranked.sort(Comparator.comparingDouble((ScoredItem item) -> item.score).reversed());
        final List<StreamInfoItem> result = ranked.stream()
                .limit(MAX_RESULTS)
                .map(item -> item.item)
                .collect(Collectors.toList());
        Log.d(TAG, "ranked genre=" + genre + " mode=" + mode + " candidates="
                + candidates.size() + " accepted=" + result.size());
        for (int i = 0; i < result.size(); i++) {
            final StreamInfoItem item = result.get(i);
            final ScoredItem scored = ranked.get(i);
            Log.d(TAG, "#" + (i + 1) + " " + item.getName() + " | "
                    + safe(item.getUploaderName()) + " | id=" + item.getUrl()
                    + " | score=" + Math.round(scored.score) + " | " + scored.reason);
        }
        return result;
    }

    public static String cacheKey(final String genre, final Region region, final Mode mode) {
        return "recommendation_" + Integer.toHexString((genre + "|" + region + "|" + mode)
                .toLowerCase(Locale.ROOT).hashCode());
    }

    public static List<StreamInfoItem> readCache(final Context context, final String key) {
        return readCache(context, key, false);
    }

    public static List<StreamInfoItem> readCache(final Context context, final String key,
                                                 final boolean allowExpired) {
        try {
            final String raw = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
                    .getString(key, null);
            if (raw == null) {
                return Collections.emptyList();
            }
            final JSONObject root = new JSONObject(raw);
            if (!allowExpired && System.currentTimeMillis() - root.getLong("savedAt")
                    > CACHE_TTL_MS) {
                return Collections.emptyList();
            }
            final JSONArray array = root.getJSONArray("items");
            final List<StreamInfoItem> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                final JSONObject json = array.getJSONObject(i);
                final StreamInfoItem item = new StreamInfoItem(
                        json.getInt("serviceId"), json.getString("name"),
                        json.getString("url"), StreamType.valueOf(json.getString("type")));
                item.setUploaderName(json.optString("uploader", ""));
                item.setUploaderUrl(json.optString("uploaderUrl", ""));
                item.setViewCount(json.optLong("views", -1));
                item.setDuration(json.optLong("duration", -1));
                item.setShortDescription(json.optString("description", ""));
                item.setTextualUploadDate(json.optString("uploadDate", ""));
                item.setUploaderVerified(json.optBoolean("verified", false));
                final String thumbnail = json.optString("thumbnail", "");
                if (!thumbnail.isEmpty()) {
                    item.setThumbnails(Collections.singletonList(new Image(thumbnail,
                            Image.WIDTH_UNKNOWN, Image.HEIGHT_UNKNOWN,
                            Image.ResolutionLevel.UNKNOWN)));
                }
                items.add(item);
            }
            return items;
        } catch (final Exception ignored) {
            return Collections.emptyList();
        }
    }

    public static void writeCache(final Context context, final String key,
                                  final List<StreamInfoItem> items) {
        try {
            final JSONArray array = new JSONArray();
            for (final StreamInfoItem item : items) {
                final JSONObject json = new JSONObject()
                        .put("serviceId", item.getServiceId())
                        .put("name", item.getName())
                        .put("url", item.getUrl())
                        .put("type", item.getStreamType().name())
                        .put("uploader", item.getUploaderName())
                        .put("uploaderUrl", item.getUploaderUrl())
                        .put("views", item.getViewCount())
                        .put("duration", item.getDuration())
                        .put("description", item.getShortDescription())
                        .put("uploadDate", item.getTextualUploadDate())
                        .put("verified", item.isUploaderVerified());
                if (!item.getThumbnails().isEmpty()) {
                    json.put("thumbnail", item.getThumbnails().get(0).getUrl());
                }
                array.put(json);
            }
            context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
                    .putString(key, new JSONObject().put("savedAt", System.currentTimeMillis())
                            .put("items", array).toString())
                    .apply();
        } catch (final Exception ignored) {
            // A cache failure must never prevent playback.
        }
    }

    private static ScoredItem score(final StreamInfoItem item, final String genre,
                                    final Mode mode) {
        if (item == null || item.getUrl() == null || item.getName() == null
                || item.getName().trim().isEmpty() || item.isShortFormContent()
                || isLive(item.getStreamType()) || item.getDuration() == 0
                || item.getDuration() > 60 * 60) {
            logExcluded(item, "not a playable music-length stream");
            return null;
        }
        final String text = (item.getName() + " " + safe(item.getUploaderName()) + " "
                + safe(item.getShortDescription())).toLowerCase(Locale.ROOT);
        final String normalized = SEPARATOR.matcher(text).replaceAll(" ").trim();
        for (final String excluded : EXCLUDED_TERMS) {
            if (normalized.contains(excluded)) {
                logExcluded(item, "excluded term: " + excluded);
                return null;
            }
        }

        double score = 0;
        final List<String> terms = GENRE_TERMS.getOrDefault(genre.toUpperCase(Locale.ROOT),
                Collections.emptyList());
        final boolean genreEvidence = terms.stream().anyMatch(normalized::contains);
        if (!terms.isEmpty() && genreEvidence) {
            score += 35;
        }
        if (item.isUploaderVerified()) {
            score += 28;
        }
        final String uploaderUrl = safe(item.getUploaderUrl()).toLowerCase(Locale.ROOT);
        if (uploaderUrl.contains("/channel/") || uploaderUrl.contains("/@")) {
            score += 12;
        }
        final boolean musicEvidence = containsAny(normalized, "official", "topic", "audio",
                "lyrics", "music", "song", "뮤직비디오", "가사", "노래")
                || item.getStreamType() == StreamType.AUDIO_STREAM;
        if (!musicEvidence && !genreEvidence && !item.isUploaderVerified()) {
            logExcluded(item, "no music or genre evidence");
            return null;
        }
        if (musicEvidence) {
            score += 20;
        }
        if (item.getViewCount() > 0) {
            score += Math.min(32, Math.log10(item.getViewCount()) * 3.5);
        }
        if (mode == Mode.NEW_RELEASES && item.getUploadDate() != null) {
            final long days = Math.max(0, Duration.between(item.getUploadDate().getInstant(),
                    Instant.now()).toDays());
            score += Math.max(0, 30 - Math.min(30, days / 3.0));
        }
        if (mode == Mode.CLASSICS && item.getViewCount() > 10_000_000) {
            score += 10;
        }
        final String reason = (genreEvidence ? "genre-evidence " : "")
                + (item.isUploaderVerified() ? "verified-channel " : "")
                + "views=" + item.getViewCount();
        return new ScoredItem(item, score, reason);
    }

    private static String songKey(final StreamInfoItem item) {
        final String title = SEPARATOR.matcher(item.getName().toLowerCase(Locale.ROOT))
                .replaceAll(" ").trim();
        final String uploader = SEPARATOR.matcher(safe(item.getUploaderName())
                .toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        return title + "|" + uploader;
    }

    private static boolean isLive(final StreamType type) {
        return type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM;
    }

    private static boolean containsAny(final String text, final String... terms) {
        for (final String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String safe(final String value) {
        return value == null ? "" : value;
    }

    private static void logExcluded(final StreamInfoItem item, final String reason) {
        if (item != null) {
            Log.d(TAG, "excluded " + item.getName() + " | " + reason);
        }
    }

    private static final class ScoredItem {
        private final StreamInfoItem item;
        private final double score;
        private final String reason;

        private ScoredItem(final StreamInfoItem item, final double score, final String reason) {
            this.item = item;
            this.score = score;
            this.reason = reason;
        }
    }
}
