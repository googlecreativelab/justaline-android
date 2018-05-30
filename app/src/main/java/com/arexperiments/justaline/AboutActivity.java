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

package com.arexperiments.justaline;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ReplacementSpan;
import android.view.View;
import android.widget.TextView;

public class AboutActivity extends BaseActivity implements View.OnClickListener {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        overridePendingTransition(R.anim.slide_in_right, R.anim.none);

        findViewById(R.id.container_privacy_policy).setOnClickListener(this);
        findViewById(R.id.container_source_licenses).setOnClickListener(this);
        findViewById(R.id.container_terms).setOnClickListener(this);
        findViewById(R.id.close_button).setOnClickListener(this);
        ((TextView) findViewById(R.id.about_version)).setText(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");

        makeLinkInDescriptionClickable();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.close_button:
                onBackPressed();
                break;
            case R.id.container_privacy_policy:
                openUrl(R.string.privacy_policy_url);
                break;
            case R.id.container_terms:
                openUrl(R.string.terms_url);
                break;
            case R.id.container_source_licenses:
                startSourceActivity();
                break;
        }
    }

    private void startSourceActivity() {
        Intent intent = new Intent(this, LicensesActivity.class);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.none, R.anim.slide_in_left);
    }

    private void openUrl(int urlRes) {
        Intent privacyIntent = new Intent(Intent.ACTION_VIEW);
        privacyIntent.setData(Uri.parse(getString(urlRes)));
        startActivity(privacyIntent);
    }

    private void makeLinkInDescriptionClickable() {

        TextView aboutDescription = findViewById(R.id.about_description_text);
        String aboutText = getString(R.string.about_text);
        String urlText = getString(R.string.about_text_link);

        int start = aboutText.indexOf(urlText);

        SpannableString spannableString = new SpannableString(aboutText);
        UnderlineSpan underlineSpan = new UnderlineSpan(urlText);
        spannableString.setSpan(underlineSpan, start, start + urlText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View textView) {
                openUrl(R.string.jal_url);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);

                ds.setColor(Color.WHITE);
            }
        };

        spannableString.setSpan(clickableSpan, start, start + urlText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        aboutDescription.setText(spannableString);
        aboutDescription.setMovementMethod(LinkMovementMethod.getInstance());
        aboutDescription.setHighlightColor(Color.TRANSPARENT);
    }

    private static class UnderlineSpan extends ReplacementSpan {

        private Paint mPaint;

        private int mWidth;

        private String mSpan;

        private float mSpanLength;

        private boolean mLengthIsCached = false;

        public UnderlineSpan(String spannedText) {
            mPaint = new Paint();
            mPaint.setColor(Color.WHITE);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(4);
            mSpan = spannedText;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end,
                           Paint.FontMetricsInt fm) {
            mWidth = (int) paint.measureText(text, start, end);
            return mWidth;
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top,
                         int y, int bottom, Paint paint) {
            canvas.drawText(text, start, end, x, y, paint);
            if (!mLengthIsCached) {
                mSpanLength = paint.measureText(mSpan);
            }

            int yOffset = 24;
            canvas.drawLine(x, y + yOffset, x + mSpanLength, y + yOffset, this.mPaint);
        }


    }
}
