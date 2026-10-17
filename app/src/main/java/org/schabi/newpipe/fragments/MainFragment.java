/*
 * SPDX-FileCopyrightText: 2026 HyoMusic contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.fragments;

import android.os.Bundle;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.image.CoilHelper;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

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
        binding.recentAll.setOnClickListener(view ->
                NavigationHelper.openStatisticFragment(getFM()));
        binding.playlistAll.setOnClickListener(view ->
                NavigationHelper.openBookmarksFragment(getFM()));
        binding.hyoPop.setOnClickListener(view -> playGenre("pop music"));
        binding.hyoRock.setOnClickListener(view -> playGenre("rock music"));
        binding.hyoBallad.setOnClickListener(view -> playGenre("ballad music"));
        binding.hyoJazz.setOnClickListener(view -> playGenre("jazz music"));
        binding.hyoClassical.setOnClickListener(view -> playGenre("classical music"));
        binding.hyoOldies.setOnClickListener(view -> playGenre("oldies music"));
        binding.themeButton.setOnClickListener(view -> showThemePicker());
        addPressAnimation(binding.hyoSearch);
        addPressAnimation(binding.hyoPlaylist);
        addPressAnimation(binding.recentAll);
        addPressAnimation(binding.playlistAll);
        addPressAnimation(binding.themeButton);
        addPressAnimation(binding.hyoPop);
        addPressAnimation(binding.hyoRock);
        addPressAnimation(binding.hyoBallad);
        addPressAnimation(binding.hyoJazz);
        addPressAnimation(binding.hyoClassical);
        addPressAnimation(binding.hyoOldies);
        colorizeGenreIcons();
        loadHomeCollections();
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
                            .limit(3)
                            .map(StreamHistoryEntry::toStreamInfoItem)
                            .collect(Collectors.toList());
                    if (recent.isEmpty()) {
                        loadFallbackRecent();
                    } else {
                        bindRecent(recent);
                    }
                }, throwable -> loadFallbackRecent()));
        disposables.add(localPlaylistManager.getPlaylists()
                .take(1)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::bindQuickPlaylists,
                        throwable -> bindQuickPlaylists(Collections.emptyList())));
    }

    private void loadFallbackRecent() {
        disposables.add(ExtractorHelper.searchFor(ServiceList.YouTube.getServiceId(),
                        "pop music official audio", Collections.emptyList(), "")
                .subscribeOn(Schedulers.io())
                .map(result -> result.getRelatedItems().stream()
                        .filter(item -> item instanceof StreamInfoItem)
                        .map(item -> (StreamInfoItem) item)
                        .limit(3)
                        .collect(Collectors.toList()))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::bindRecent, throwable -> bindRecent(Collections.emptyList())));
    }

    private void bindRecent(final List<StreamInfoItem> items) {
        final View[] cards = {binding.recentItem1, binding.recentItem2, binding.recentItem3};
        final android.widget.ImageView[] thumbnails = {binding.recentThumbnail1,
                binding.recentThumbnail2, binding.recentThumbnail3};
        final org.schabi.newpipe.views.NewPipeTextView[] titles = {binding.recentTitle1,
                binding.recentTitle2, binding.recentTitle3};
        final org.schabi.newpipe.views.NewPipeTextView[] artists = {binding.recentArtist1,
                binding.recentArtist2, binding.recentArtist3};
        final org.schabi.newpipe.views.NewPipeTextView[] equalizers = {binding.recentEqualizer1,
                binding.recentEqualizer2, binding.recentEqualizer3};
        final View[] playButtons = {binding.recentPlay1, binding.recentPlay2, binding.recentPlay3};
        final org.schabi.newpipe.player.playqueue.PlayQueueItem currentItem =
                PlayerHolder.getInstance().getCurrentItem();
        final boolean playerIsPlaying = PlayerHolder.getInstance().isPlaying();
        binding.recentSection.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        for (int i = 0; i < cards.length; i++) {
            if (i >= items.size()) {
                cards[i].setVisibility(View.GONE);
                continue;
            }
            final StreamInfoItem item = items.get(i);
            cards[i].setVisibility(View.VISIBLE);
            titles[i].setText(item.getName());
            artists[i].setText(item.getUploaderName());
            CoilHelper.INSTANCE.loadThumbnail(thumbnails[i], item.getThumbnails());
            final boolean isPlaying = playerIsPlaying && currentItem != null
                    && item.getUrl().equals(currentItem.getUrl());
            equalizers[i].setVisibility(isPlaying ? View.VISIBLE : View.GONE);
            equalizers[i].clearAnimation();
            if (isPlaying) {
                startEqualizerAnimation(equalizers[i]);
            }
            final View.OnClickListener play = view -> NavigationHelper.playOnBackgroundPlayer(
                    requireContext(), new SinglePlayQueue(item), true);
            cards[i].setOnClickListener(play);
            playButtons[i].setOnClickListener(play);
            addPressAnimation(cards[i]);
            addPressAnimation(playButtons[i]);
        }
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
        styleCard(binding.recentItem1, card, second);
        styleCard(binding.recentItem2, card, second);
        styleCard(binding.recentItem3, card, second);
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
