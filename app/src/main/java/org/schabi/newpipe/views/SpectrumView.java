/*
 * SPDX-FileCopyrightText: 2026 HyoMusic contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.views;

import android.animation.ValueAnimator;
import android.media.audiofx.Visualizer;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

public final class SpectrumView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] heights = {0.52f, 0.78f, 0.38f, 0.92f, 0.62f, 0.84f, 0.44f, 0.70f};
    private final int[] colors = {0xFF8DB8FF, 0xFFB98CFF, 0xFFFFA8D0, 0xFF8CE4D5};
    private ValueAnimator animator;
    private Visualizer visualizer;
    private final float[] liveLevels = new float[8];
    private float phase;

    public SpectrumView(final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        paint.setStyle(Paint.Style.FILL);
        setWillNotDraw(false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(950L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue() * (float) (Math.PI * 2);
            invalidate();
        });
        animator.start();
        try {
            visualizer = new Visualizer(0);
            visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(final Visualizer effect, final byte[] waveform,
                                                  final int samplingRate) {
                }

                @Override
                public void onFftDataCapture(final Visualizer effect, final byte[] fft,
                                             final int samplingRate) {
                    for (int i = 0; i < liveLevels.length; i++) {
                        final int index = 2 + i * 2;
                        if (index + 1 < fft.length) {
                            final float real = fft[index];
                            final float imaginary = fft[index + 1];
                            liveLevels[i] = Math.min(1f, (float) Math.sqrt(real * real
                                    + imaginary * imaginary) / 80f);
                        }
                    }
                    postInvalidateOnAnimation();
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true);
            visualizer.setEnabled(true);
        } catch (final RuntimeException ignored) {
            // Some devices disallow global capture; the local animation remains available.
            visualizer = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (visualizer != null) {
            visualizer.release();
            visualizer = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(35, 50, 88));
        final float gap = getWidth() / 18f;
        final float barWidth = gap * 1.25f;
        final float maxHeight = getHeight() - gap * 2;
        for (int i = 0; i < heights.length; i++) {
            final float live = liveLevels[i];
            final float pulse = live > 0.03f
                    ? 0.65f + live * 0.55f
                    : 0.82f + 0.18f * (float) Math.sin(phase + i * 0.85f);
            final float height = maxHeight * heights[i] * pulse;
            paint.setColor(colors[i % colors.length]);
            final float left = gap + i * (barWidth + gap / 2f);
            canvas.drawRoundRect(left, getHeight() - gap - height,
                    left + barWidth, getHeight() - gap, barWidth / 2f, barWidth / 2f, paint);
        }
    }
}
