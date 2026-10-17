/*
 * SPDX-FileCopyrightText: 2026 HyoMusic contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

        binding.hyoSearch.setOnClickListener(view -> NavigationHelper.openSearchFragment(
                getFM(), ServiceList.YouTube.getServiceId(), ""));
        binding.hyoFavorites.setOnClickListener(view ->
                NavigationHelper.openBookmarksFragment(getFM()));
        binding.hyoPlayAll.setOnClickListener(view -> playHistory(false));
        binding.hyoResume.setOnClickListener(view -> playHistory(true));
        binding.hyoPop.setOnClickListener(view -> playGenre("pop music"));
        binding.hyoRock.setOnClickListener(view -> playGenre("rock music"));
        binding.hyoBallad.setOnClickListener(view -> playGenre("ballad music"));
        binding.hyoJazz.setOnClickListener(view -> playGenre("jazz music"));
        binding.hyoClassical.setOnClickListener(view -> playGenre("classical music"));
        binding.hyoOldies.setOnClickListener(view -> playGenre("oldies music"));
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
