/*
 * SPDX-FileCopyrightText: 2026 HyoMusic contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MainFragment extends BaseFragment {
    private FragmentMainBinding binding;
    private final CompositeDisposable disposables = new CompositeDisposable();

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
        binding.hyoFavorites.setOnClickListener(view ->
                NavigationHelper.openBookmarksFragment(getFM()));
        binding.hyoPlayAll.setOnClickListener(view -> playHistory(false));
        binding.hyoResume.setOnClickListener(view -> playHistory(true));
        binding.simpleTopSearch.setOnClickListener(view -> binding.hyoSearch.performClick());
        binding.simpleTopSettings.setOnClickListener(view ->
                NavigationHelper.openSettings(requireContext()));
        binding.hyoNowPlaying.setOnClickListener(view -> playHistory(true));
        binding.hyoPop.setOnClickListener(view -> playGenre("pop music"));
        binding.hyoRock.setOnClickListener(view -> playGenre("rock music"));
        binding.hyoBallad.setOnClickListener(view -> playGenre("ballad music"));
        binding.hyoJazz.setOnClickListener(view -> playGenre("jazz music"));
        binding.hyoClassical.setOnClickListener(view -> playGenre("classical music"));
        binding.hyoOldies.setOnClickListener(view -> playGenre("oldies music"));
        binding.themeBlue.setOnClickListener(view -> applyTheme(0));
        binding.themePurple.setOnClickListener(view -> applyTheme(1));
        binding.themeGreen.setOnClickListener(view -> applyTheme(2));
        binding.themeOrange.setOnClickListener(view -> applyTheme(3));
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
        binding.simpleHero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{first, second}));
        binding.hyoSearch.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{first, second}));
        binding.hyoNowPlaying.setBackgroundColor(card);
        binding.hyoFavorites.setBackgroundColor(card);
        binding.hyoResume.setBackgroundColor(card);
        binding.hyoPlayAll.setBackgroundColor(card);
        binding.hyoPop.setBackgroundColor(card);
        binding.hyoRock.setBackgroundColor(card);
        binding.hyoBallad.setBackgroundColor(card);
        binding.hyoJazz.setBackgroundColor(card);
        binding.hyoClassical.setBackgroundColor(card);
        binding.hyoOldies.setBackgroundColor(card);
        PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                .putInt("simple_tunes_theme", theme).apply();
    }

    private void playGenre(final String genre) {
        Toast.makeText(requireContext(), R.string.simple_tunes_loading, Toast.LENGTH_SHORT).show();
        disposables.add(ExtractorHelper.searchFor(ServiceList.YouTube.getServiceId(),
                        genre + " official audio", Collections.emptyList(), "")
                .subscribeOn(Schedulers.io())
                .map(result -> result.getRelatedItems().stream()
                        .filter(item -> item instanceof StreamInfoItem)
                        .map(item -> (StreamInfoItem) item)
                        .limit(10)
                        .collect(Collectors.toList()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    if (items.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.simple_tunes_no_genre,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    NavigationHelper.playOnBackgroundPlayer(requireContext(),
                            new SinglePlayQueue(items, 0), true);
                }, throwable -> Toast.makeText(requireContext(), R.string.simple_tunes_genre_error,
                        Toast.LENGTH_LONG).show()));
    }

    private void playHistory(final boolean latestOnly) {
        final var historyManager = new HistoryRecordManager(requireContext());
        disposables.add(historyManager.getStreamHistory()
                .firstOrError()
                .map(history -> {
                    Stream<StreamHistoryEntry> youtubeHistory = history.stream()
                            .filter(item -> item.getStreamEntity().getServiceId()
                                    == ServiceList.YouTube.getServiceId());
                    if (latestOnly) {
                        youtubeHistory = youtubeHistory.limit(1);
                    }
                    return youtubeHistory.map(StreamHistoryEntry::toStreamInfoItem)
                            .collect(Collectors.toList());
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    if (items.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.hyo_no_history,
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    NavigationHelper.playOnBackgroundPlayer(requireContext(),
                            new SinglePlayQueue(items, 0), true);
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
