// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.arexperiments.justaline.view;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

import com.arexperiments.justaline.R;

/**
 * Created by Kat on 2/2/18.
 * Custom view for circular progress bar around record button on DrawARActivity
 */

public class RecordButtonProgressBar extends View {

    /*
     * Paint
     */
    private static final int START_ANGLE = -90;

    private int ringWidth = 20;

    private RectF circle;

    private Paint progressPaint;

    private Path progressPath;

    private float endPercentProgress;

    private int endSweep;

    private int currentSweep;

    public RecordButtonProgressBar(Context context) {
        super(context);
        init();
    }

    public RecordButtonProgressBar(Context context,
                                   @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public RecordButtonProgressBar(Context context,
                                   @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {

        //
        // set up paint components
        //

        float scale = getContext().getResources().getDisplayMetrics().density;
        // formula from framework: px = (int) (dps * scale + 0.5f)
        ringWidth = (int) (7 * scale + 0.5f);

        endPercentProgress = 0;
        int startSweep = 0;
        endSweep = 0;
        currentSweep = startSweep;

        int progressColor = ContextCompat.getColor(getContext(), R.color.record_highlight);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setColor(progressColor);
        progressPaint.setStrokeWidth(ringWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        circle = new RectF(0, 0, getWidth(), getHeight());

        progressPath = new Path();

    }

    public void reset() {
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        circle.set(ringWidth / 2, ringWidth / 2, w - ringWidth / 2, w - ringWidth / 2);

        updateProgressPath();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Try for a width based on our minimum
        int minw = getPaddingLeft() + getPaddingRight() + getSuggestedMinimumWidth();
        int w = resolveSize(minw, widthMeasureSpec);

        // Whatever the width ends up being, ask for a height that would let the pie
        // get as big as it can
        resolveSize(MeasureSpec.getSize(w), heightMeasureSpec);

        setMeasuredDimension(w, w);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawPath(progressPath, progressPaint);
    }

    public void setCurrentDuration(long currentDuration, long maxDuration) {
        this.endPercentProgress = currentDuration / (maxDuration * 1f);

        int MAX_SWEEP = 359;
        this.endSweep = (int) (MAX_SWEEP * endPercentProgress);

        if (endSweep >= MAX_SWEEP) {
            endSweep = MAX_SWEEP;
            // switch stroke cap to square so that ends of progress bar meet
            progressPaint.setStrokeCap(Paint.Cap.SQUARE);
        }

        progressPath.reset();
        progressPath.arcTo(circle, START_ANGLE, endSweep);

        invalidate();
    }

    private void updateProgressPath() {

        progressPath.reset();
        progressPath.arcTo(circle, START_ANGLE, currentSweep);
    }


}
