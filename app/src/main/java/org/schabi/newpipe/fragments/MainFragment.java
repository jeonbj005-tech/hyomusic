/*
 * SPDX-FileCopyrightText: 2026 HyoMusic contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.content.DialogInterface;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.lifecycle.LiveData;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.NewVersionWorker;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.dialog.PlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.MusicRecommendationEngine;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.CoilHelper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainFragment extends BaseFragment {
    private FragmentMainBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private HistoryRecordManager historyRecordManager;
    private LocalPlaylistManager localPlaylistManager;

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        binding = FragmentMainBinding.bind(rootView);
        setTitle(getString(R.string.app_name));
        applyTheme(PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getInt("simple_tunes_theme", 0));

        binding.hyoSearch.setOnClickListener(view -> NavigationHelper.openSearchFragment(
                getFM(), ServiceList.YouTube.getServiceId(), ""));
        binding.hyoPlaylist.setOnClickListener(view ->
                NavigationHelper.openBookmarksFragment(getFM()));
        binding.hyoGemini.setOnClickListener(view -> showGeminiPrompt());
        binding.recentAll.setOnClickListener(view ->
                NavigationHelper.openStatisticFragment(getFM()));
        binding.playlistAll.setOnClickListener(view ->
                NavigationHelper.openBookmarksFragment(getFM()));
        binding.hyoPop.setOnClickListener(view -> showRegionPicker("MUSIC",
                MusicRecommendationEngine.Mode.TRENDING));
        binding.hyoRock.setOnClickListener(view -> showRegionPicker("MUSIC",
                MusicRecommendationEngine.Mode.NEW_RELEASES));
        binding.hyoBallad.setOnClickListener(view -> showGenrePicker());
        binding.hyoJazz.setOnClickListener(view -> playPersonalized());
        binding.hyoClassical.setOnClickListener(view ->
                NavigationHelper.openBookmarksFragment(getFM()));
        binding.hyoOldies.setOnClickListener(view ->
                NavigationHelper.openStatisticFragment(getFM()));
        binding.themeButton.setOnClickListener(view -> showThemePicker());
        binding.updateButton.setOnClickListener(view -> checkForUpdates());
        addPressAnimation(binding.hyoSearch);
        addPressAnimation(binding.hyoPlaylist);
        addPressAnimation(binding.hyoGemini);
        addPressAnimation(binding.recentAll);
        addPressAnimation(binding.playlistAll);
        addPressAnimation(binding.themeButton);
        addPressAnimation(binding.updateButton);
        addPressAnimation(binding.hyoPop);
        addPressAnimation(binding.hyoRock);
        addPressAnimation(binding.hyoBallad);
        addPressAnimation(binding.hyoJazz);
        addPressAnimation(binding.hyoClassical);
        addPressAnimation(binding.hyoOldies);
        colorizeGenreIcons();
        loadHomeCollections();
    }

    private void checkForUpdates() {
        Toast.makeText(requireContext(), R.string.checking_updates_toast,
                Toast.LENGTH_SHORT).show();
        final java.util.UUID workId = NewVersionWorker.enqueueNewVersionCheckingWork(
                requireContext(), true);
        final LiveData<WorkInfo> workInfo = WorkManager.getInstance(requireContext())
                .getWorkInfoByIdLiveData(workId);
        workInfo.observe(getViewLifecycleOwner(), info -> {
                    if (info == null || !info.getState().isFinished()) {
                        return;
                    }
                    workInfo.removeObservers(getViewLifecycleOwner());
                    if (info.getState() != WorkInfo.State.SUCCEEDED
                            || info.getOutputData().getBoolean(NewVersionWorker.CHECK_FAILED,
                            false)) {
                        Toast.makeText(requireContext(), R.string.simple_tunes_update_check_failed,
                                Toast.LENGTH_LONG).show();
                    } else if (info.getOutputData().getBoolean(NewVersionWorker.UPDATE_AVAILABLE,
                            false)) {
                        final String version = info.getOutputData().getString(
                                NewVersionWorker.VERSION_NAME);
                        final String apkUrl = info.getOutputData().getString(
                                NewVersionWorker.APK_URL);
                        final android.net.Uri downloadUri = android.net.Uri.parse(apkUrl);
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setMessage(getString(R.string.simple_tunes_update_available,
                                        version))
                                .setPositiveButton(R.string.simple_tunes_update_download,
                                        (dialog, which) -> {
                                    try {
                                        startActivity(new Intent(Intent.ACTION_VIEW, downloadUri));
                                    } catch (final ActivityNotFoundException ignored) {
                                        Toast.makeText(requireContext(),
                                                R.string.simple_tunes_update_check_failed,
                                                Toast.LENGTH_LONG).show();
                                    }
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                    } else {
                        Toast.makeText(requireContext(), R.string.app_update_unavailable_toast,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void loadHomeCollections() {
        historyRecordManager = new HistoryRecordManager(requireContext());
        localPlaylistManager = new LocalPlaylistManager(
                NewPipeDatabase.getInstance(requireContext()));
        disposables.add(historyRecordManager.getStreamHistory()
                .take(1)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(entries -> {
                    final List<StreamInfoItem> recent = entries.stream()
                            .limit(30)
                            .map(StreamHistoryEntry::toStreamInfoItem)
                            .collect(Collectors.toList());
                    bindRecent(recent);
                }, throwable -> bindRecent(Collections.emptyList())));
        disposables.add(localPlaylistManager.getPlaylists()
                .take(1)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::bindQuickPlaylists,
                        throwable -> bindQuickPlaylists(Collections.emptyList())));
    }

    private void bindRecent(final List<StreamInfoItem> items) {
        final org.schabi.newpipe.player.playqueue.PlayQueueItem currentItem =
                PlayerHolder.getInstance().getCurrentItem();
        final boolean playerIsPlaying = PlayerHolder.getInstance().isPlaying();
        final boolean hasRecentItems = !items.isEmpty();
        binding.recentSection.setVisibility(hasRecentItems ? View.VISIBLE : View.GONE);
        binding.spaceBeforeRecent.setVisibility(hasRecentItems ? View.VISIBLE : View.GONE);
        binding.spaceBeforePlaylists.setVisibility(hasRecentItems ? View.VISIBLE : View.GONE);
        binding.recentItems.removeAllViews();
        for (final StreamInfoItem item : items) {
            final boolean isPlaying = playerIsPlaying && currentItem != null
                    && item.getUrl().equals(currentItem.getUrl());
            binding.recentItems.addView(createRecentRow(item, isPlaying));
        }
    }

    private View createRecentRow(final StreamInfoItem item, final boolean isPlaying) {
        final LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(6), dp(6), dp(6), dp(6));
        final TypedValue selectable = new TypedValue();
        if (requireContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackground, selectable, true)) {
            row.setBackgroundResource(selectable.resourceId);
        }

        final ImageView thumbnail = new ImageView(requireContext());
        thumbnail.setContentDescription(getString(R.string.simple_tunes_thumbnail));
        thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbnail.setImageResource(R.drawable.placeholder_thumbnail_video);
        row.addView(thumbnail, new LinearLayout.LayoutParams(dp(46), dp(46)));
        CoilHelper.INSTANCE.loadThumbnail(thumbnail, item.getThumbnails());

        final LinearLayout labels = new LinearLayout(requireContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        labelsParams.setMarginStart(dp(8));
        labelsParams.setMarginEnd(dp(4));
        row.addView(labels, labelsParams);
        final org.schabi.newpipe.views.NewPipeTextView title = new
                org.schabi.newpipe.views.NewPipeTextView(requireContext());
        title.setText(item.getName());
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(title);
        final org.schabi.newpipe.views.NewPipeTextView artist = new
                org.schabi.newpipe.views.NewPipeTextView(requireContext());
        artist.setText(item.getUploaderName());
        artist.setTextColor(Color.rgb(174, 184, 211));
        artist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        artist.setMaxLines(1);
        artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labels.addView(artist);

        final org.schabi.newpipe.views.NewPipeTextView equalizer = new
                org.schabi.newpipe.views.NewPipeTextView(requireContext());
        equalizer.setText("▂▅▇");
        equalizer.setTextColor(Color.rgb(156, 199, 255));
        equalizer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        equalizer.setGravity(Gravity.CENTER);
        equalizer.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
        row.addView(equalizer, new LinearLayout.LayoutParams(dp(32),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (isPlaying) {
            startEqualizerAnimation(equalizer);
        }

        final androidx.appcompat.widget.AppCompatImageButton play =
                new androidx.appcompat.widget.AppCompatImageButton(requireContext());
        play.setContentDescription(getString(R.string.play));
        play.setImageResource(R.drawable.ic_play_arrow);
        play.setColorFilter(Color.WHITE);
        play.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.addView(play, new LinearLayout.LayoutParams(dp(40), dp(40)));
        final View.OnClickListener playItem = view -> NavigationHelper.playOnBackgroundPlayer(
                requireContext(), new SinglePlayQueue(item), true);
        row.setOnClickListener(playItem);
        play.setOnClickListener(playItem);
        addPressAnimation(row);
        addPressAnimation(play);
        final LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (binding.recentItems.getChildCount() > 0) {
            rowParams.topMargin = dp(4);
        }
        row.setLayoutParams(rowParams);
        return row;
    }

    private void startEqualizerAnimation(final View view) {
        final Animation animation = new AlphaAnimation(0.35f, 1f);
        animation.setDuration(420);
        animation.setRepeatMode(Animation.REVERSE);
        animation.setRepeatCount(Animation.INFINITE);
        view.startAnimation(animation);
    }

    private void bindQuickPlaylists(final List<PlaylistMetadataEntry> playlists) {
        binding.quickPlaylist1.setOnClickListener(view ->
                NavigationHelper.openStatisticFragment(getFM()));
        binding.quickPlaylistTitle1.setText(R.string.simple_tunes_recent_title);
        binding.quickPlaylistSubtitle1.setText(R.string.simple_tunes_recent_subtitle);
        if (playlists.isEmpty()) {
            binding.quickPlaylistTitle2.setText(R.string.simple_tunes_my_playlists);
            binding.quickPlaylistSubtitle2.setText(R.string.simple_tunes_playlist_empty);
            binding.quickPlaylist2.setOnClickListener(view ->
                    NavigationHelper.openBookmarksFragment(getFM()));
        } else {
            final PlaylistMetadataEntry playlist = playlists.get(0);
            binding.quickPlaylistTitle2.setText(playlist.getOrderingName());
            binding.quickPlaylistSubtitle2.setText(getString(
                    R.string.simple_tunes_playlist_count, playlist.getStreamCount()));
            binding.quickPlaylist2.setOnClickListener(view ->
                    NavigationHelper.openLocalPlaylistFragment(getFM(), playlist.getUid(),
                            playlist.getOrderingName()));
        }
        addPressAnimation(binding.quickPlaylist1);
        addPressAnimation(binding.quickPlaylist2);
    }

    private void addPressAnimation(final View view) {
        view.setOnTouchListener((target, event) -> {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    target.animate().scaleX(0.98f).scaleY(0.98f).setDuration(170).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    target.animate().scaleX(1f).scaleY(1f).setDuration(170).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private void colorizeGenreIcons() {
        colorize(binding.hyoPop, 0xFF9CC7FF);
        colorize(binding.hyoRock, 0xFFD4A7FF);
        colorize(binding.hyoBallad, 0xFFFFA9C8);
        colorize(binding.hyoJazz, 0xFF8FE2D5);
        colorize(binding.hyoClassical, 0xFFFFD28C);
        colorize(binding.hyoOldies, 0xFFB7D2FF);
    }

    private void colorize(final android.widget.TextView view, final int color) {
        final String label = view.getText().toString();
        final SpannableString styled = new SpannableString(label);
        styled.setSpan(new ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(styled);
    }

    private void showThemePicker() {
        final String[] themes = {"Blue", "Purple", "Green", "Orange"};
        final int selected = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getInt("simple_tunes_theme", 0);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.simple_tunes_theme_title)
                .setSingleChoiceItems(themes, selected, (dialog, which) -> {
                    applyTheme(which);
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, (DialogInterface dialog, int which) ->
                        dialog.dismiss())
                .show();
    }

    private void showGeminiPrompt() {
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint(R.string.simple_tunes_gemini_prompt_hint);
        input.setSingleLine(true);
        input.setPadding(32, 8, 32, 8);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.simple_tunes_gemini_playlist)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    final String prompt = input.getText().toString().trim();
                    if (!prompt.isEmpty()) {
                        generateGeminiPlaylist(prompt);
                    }
                })
                .show();
    }

    private void generateGeminiPlaylist(final String prompt) {
        Toast.makeText(requireContext(), R.string.simple_tunes_gemini_loading,
                Toast.LENGTH_SHORT).show();
        disposables.add(Observable.fromCallable(() -> requestGeminiQueries(prompt))
                .subscribeOn(Schedulers.io())
                .flatMapIterable(queries -> queries)
                .concatMapMaybe(query -> ExtractorHelper.searchFor(
                                ServiceList.YouTube.getServiceId(), query,
                                Collections.emptyList(), "")
                        .subscribeOn(Schedulers.io())
                        .map(result -> result.getRelatedItems().stream()
                                .filter(item -> item instanceof StreamInfoItem)
                                .map(item -> (StreamInfoItem) item)
                                .findFirst()
                                .orElse(null))
                        .flatMapMaybe(item -> item == null ? Maybe.empty() : Maybe.just(item))
                        .onErrorResumeWith(Maybe.empty()))
                .take(10)
                .toList()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::appendGeminiResults, this::showGeminiError));
    }

    private void showGeminiError(final Throwable throwable) {
        String detail = throwable.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = throwable.getClass().getSimpleName();
        }
        detail = detail.replace('\n', ' ');
        if (detail.length() > 180) {
            detail = detail.substring(0, 180) + "…";
        }
        Toast.makeText(requireContext(), getString(R.string.simple_tunes_gemini_error_detail,
                detail), Toast.LENGTH_LONG).show();
    }

    private List<String> requestGeminiQueries(final String prompt)
            throws Exception {
        if (BuildConfig.GEMINI_BACKEND_URL.trim().isEmpty()) {
            throw new IllegalStateException("Gemini backend is not configured");
        }
        final JSONObject requestJson = new JSONObject().put("prompt", prompt);
        final Request request = new Request.Builder()
                .url(BuildConfig.GEMINI_BACKEND_URL)
                .post(RequestBody.create(requestJson.toString(), MediaType.get(
                        "application/json; charset=utf-8")))
                .build();
        try (Response response = DownloaderImpl.getInstance().getClient()
                .newCall(request).execute()) {
            final String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            final JSONArray queriesJson = new JSONObject(body).optJSONArray("queries");
            if (queriesJson == null) {
                throw new IllegalStateException("Gemini returned no query list");
            }
            final List<String> queries = new ArrayList<>();
            for (int i = 0; i < queriesJson.length() && queries.size() < 10; i++) {
                final String query = queriesJson.optString(i).trim();
                if (!query.isEmpty()) {
                    queries.add(query);
                }
            }
            return queries;
        }
    }

    private void appendGeminiResults(final List<StreamInfoItem> items) {
        if (items.isEmpty()) {
            Toast.makeText(requireContext(), R.string.simple_tunes_gemini_empty,
                    Toast.LENGTH_LONG).show();
            return;
        }
        final List<StreamEntity> entities = items.stream().map(StreamEntity::new)
                .collect(Collectors.toList());
        disposables.add(PlaylistDialog.createCorrespondingDialog(requireContext(), entities,
                dialog -> dialog.show(getParentFragmentManager(), "GeminiPlaylistDialog")));
    }

    private void applyTheme(final int theme) {
        final int background;
        final int card;
        final int first;
        final int second;
        switch (theme) {
            case 1 -> {
                background = Color.rgb(16, 13, 32);
                card = Color.rgb(42, 31, 74);
                first = Color.rgb(150, 82, 255);
                second = Color.rgb(61, 118, 255);
            }
            case 2 -> {
                background = Color.rgb(8, 25, 25);
                card = Color.rgb(24, 61, 60);
                first = Color.rgb(24, 181, 148);
                second = Color.rgb(81, 209, 125);
            }
            case 3 -> {
                background = Color.rgb(31, 17, 12);
                card = Color.rgb(75, 43, 29);
                first = Color.rgb(255, 126, 66);
                second = Color.rgb(255, 190, 78);
            }
            default -> {
                background = Color.rgb(13, 16, 32);
                card = Color.rgb(32, 38, 64);
                first = Color.rgb(109, 69, 245);
                second = Color.rgb(24, 183, 216);
            }
        }
        binding.simpleRoot.setBackgroundColor(background);
        styleCard(binding.hyoSearch, card, second);
        styleCard(binding.hyoPlaylist, card, second);
        styleCard(binding.hyoGemini, card, first);
        // The activity toolbar and status bar are outside fragment_main.xml. Keep them
        // in the same palette so the home screen does not start with an unrelated red bar.
        final View toolbar = requireActivity().findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setBackgroundColor(background);
        }
        requireActivity().getWindow().setStatusBarColor(background);
        requireActivity().getWindow().setNavigationBarColor(background);
        styleCard(binding.hyoPop, card, Color.rgb(106, 164, 255));
        styleCard(binding.hyoRock, card, Color.rgb(178, 124, 245));
        styleCard(binding.hyoBallad, card, Color.rgb(242, 132, 173));
        styleCard(binding.hyoJazz, card, Color.rgb(74, 196, 178));
        styleCard(binding.hyoClassical, card, Color.rgb(245, 187, 104));
        styleCard(binding.hyoOldies, card, Color.rgb(133, 174, 230));
        styleCard(binding.recentScroll, card, second);
        styleCard(binding.quickPlaylist1, card, first);
        styleCard(binding.quickPlaylist2, card, first);
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putInt("simple_tunes_theme", theme).apply();
    }

    private void styleCard(final View view, final int color, final int accent) {
        final float density = getResources().getDisplayMetrics().density;
        final GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(20 * density);
        shape.setStroke((int) Math.ceil(density), Color.argb(65,
                Color.red(accent), Color.green(accent), Color.blue(accent)));
        androidx.core.view.ViewCompat.setBackgroundTintList(view, null);
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(45,
                        Color.red(accent), Color.green(accent), Color.blue(accent))),
                shape, null));
    }

    private void showGenrePicker() {
        final String[] genres = {"POP", "K-POP", "HIP-HOP", "R&B", "ROCK", "LATIN", "EDM",
                "BALLAD", "JAZZ", "INDIE", "CLASSICAL"};
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.simple_tunes_choose_genre)
                .setItems(genres, (dialog, which) -> {
                    dialog.dismiss();
                    showRegionPicker(genres[which], null);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRegionPicker(final String genre, final MusicRecommendationEngine.Mode mode) {
        final MusicRecommendationEngine.Region[] regions =
                MusicRecommendationEngine.Region.values();
        final String[] labels = Arrays.stream(regions)
                .map(MusicRecommendationEngine.Region::getLabel)
                .toArray(String[]::new);
        final int selected = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getInt("simple_tunes_region", 0);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.simple_tunes_choose_region)
                .setSingleChoiceItems(labels, Math.min(selected, labels.length - 1),
                        (dialog, which) -> {
                    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                            .putInt("simple_tunes_region", which).apply();
                    dialog.dismiss();
                    final MusicRecommendationEngine.Region region = regions[which];
                    if (mode == null) {
                        showModePicker(genre, region);
                    } else {
                        loadRecommendations(genre, region, mode);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showModePicker(final String genre, final MusicRecommendationEngine.Region region) {
        final MusicRecommendationEngine.Mode[] modes = MusicRecommendationEngine.Mode.values();
        final String[] labels = Arrays.stream(modes)
                .map(MusicRecommendationEngine.Mode::getLabel)
                .toArray(String[]::new);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.simple_tunes_choose_mode)
                .setSingleChoiceItems(labels, 0, (dialog, which) -> {
                    dialog.dismiss();
                    loadRecommendations(genre, region, modes[which]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void loadRecommendations(final String genre,
                                     final MusicRecommendationEngine.Region region,
                                     final MusicRecommendationEngine.Mode mode) {
        final String cacheKey = MusicRecommendationEngine.cacheKey(genre, region, mode);
        final List<StreamInfoItem> cached = MusicRecommendationEngine.readCache(requireContext(),
                cacheKey);
        final List<StreamInfoItem> staleCache = MusicRecommendationEngine.readCache(
                requireContext(), cacheKey, true);
        if (!cached.isEmpty()) {
            showRecommendations(cached, genre, region, mode);
            return;
        }
        Toast.makeText(requireContext(), R.string.simple_tunes_loading, Toast.LENGTH_SHORT).show();
        disposables.add(Observable.fromIterable(MusicRecommendationEngine.buildQueries(
                        genre, region, mode))
                .concatMap(query -> ExtractorHelper.searchFor(ServiceList.YouTube.getServiceId(),
                                query, Collections.emptyList(), "")
                        .subscribeOn(Schedulers.io())
                        .toObservable())
                .flatMapIterable(result -> result.getRelatedItems())
                .filter(item -> item instanceof StreamInfoItem)
                .map(item -> (StreamInfoItem) item)
                .toList()
                .map(items -> MusicRecommendationEngine.rank(items, genre, mode))
                .doOnSuccess(items -> MusicRecommendationEngine.writeCache(requireContext(),
                        cacheKey, items))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    if (items.isEmpty() && staleCache.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.simple_tunes_recommend_empty,
                                Toast.LENGTH_LONG).show();
                    } else {
                        showRecommendations(items.isEmpty() ? staleCache : items,
                                genre, region, mode);
                    }
                }, throwable -> {
                    if (staleCache.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.simple_tunes_recommend_error,
                                Toast.LENGTH_LONG).show();
                    } else {
                        showRecommendations(staleCache, genre, region, mode);
                    }
                }));
    }

    private void showRecommendations(final List<StreamInfoItem> items, final String genre,
                                     final MusicRecommendationEngine.Region region,
                                     final MusicRecommendationEngine.Mode mode) {
        final LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        final int padding = dp(8);
        list.setPadding(padding, 0, padding, 0);
        final TextView summary = new TextView(requireContext());
        summary.setText(getString(R.string.simple_tunes_recommend_summary, items.size(),
                genre, region.getLabel(), mode.getLabel()));
        summary.setTextColor(Color.LTGRAY);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        summary.setPadding(dp(8), dp(8), dp(8), dp(8));
        list.addView(summary);
        for (final StreamInfoItem item : items) {
            final LinearLayout row = new LinearLayout(requireContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(5), dp(4), dp(5));
            final ImageView thumbnail = new ImageView(requireContext());
            thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumbnail.setImageResource(R.drawable.placeholder_thumbnail_video);
            row.addView(thumbnail, new LinearLayout.LayoutParams(dp(64), dp(64)));
            CoilHelper.INSTANCE.loadThumbnail(thumbnail, item.getThumbnails());

            final LinearLayout text = new LinearLayout(requireContext());
            text.setOrientation(LinearLayout.VERTICAL);
            text.setPadding(dp(8), 0, dp(6), 0);
            final TextView title = new TextView(requireContext());
            title.setText(item.getName());
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            title.setMaxLines(2);
            final TextView artist = new TextView(requireContext());
            artist.setText(item.getUploaderName());
            artist.setTextColor(Color.LTGRAY);
            artist.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            artist.setMaxLines(1);
            text.addView(title);
            text.addView(artist);
            row.addView(text, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            final Button play = new Button(requireContext());
            play.setText(R.string.simple_tunes_play);
            play.setAllCaps(false);
            play.setOnClickListener(view -> NavigationHelper.playOnBackgroundPlayer(
                    requireContext(), new SinglePlayQueue(item), true));
            row.addView(play, new LinearLayout.LayoutParams(dp(64), dp(48)));
            row.setOnClickListener(view -> NavigationHelper.playOnBackgroundPlayer(
                    requireContext(), new SinglePlayQueue(item), true));
            addPressAnimation(row);
            list.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        final ScrollView scroll = new ScrollView(requireContext());
        scroll.addView(list);
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.simple_tunes_recommend_title)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void playPersonalized() {
        if (historyRecordManager == null) {
            historyRecordManager = new HistoryRecordManager(requireContext());
        }
        disposables.add(historyRecordManager.getStreamHistory()
                .take(1)
                .map(entries -> entries.stream()
                        .limit(10)
                        .map(StreamHistoryEntry::toStreamInfoItem)
                        .collect(Collectors.toList()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    if (items.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.hyo_no_history,
                                Toast.LENGTH_LONG).show();
                    } else {
                        NavigationHelper.playOnBackgroundPlayer(requireContext(),
                                new SinglePlayQueue(items, 0), true);
                    }
                }, throwable -> Toast.makeText(requireContext(), R.string.hyo_history_error,
                        Toast.LENGTH_LONG).show()));
    }

    /** Kept for MainActivity's existing lifecycle hook. */
    public void commitPlaylistTabs() {
        // The HyoMusic home has no editable playlist tabs.
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        disposables.dispose();
        super.onDestroy();
    }
}
