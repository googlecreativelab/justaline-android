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

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Created by kaitlynanderson on 2/14/18.
 * Activity to hold the Third Party Licenses (RecordableSurfaceView-Apache,
 * Vecmath-GNUGeneralPublicLicense, and Gson-Apache)
 */

public class LicensesActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_licenses);
        overridePendingTransition(R.anim.slide_in_right, R.anim.none);

        View closeButton = findViewById(R.id.close_button);
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        TextView rsvLicenseType = findViewById(R.id.text_rsv_license_type);
        makeLinkInDescriptionClickable(rsvLicenseType);
        TextView lottieLicenseType = findViewById(R.id.text_lottie_license_type);
        makeLinkInDescriptionClickable(lottieLicenseType);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.none, R.anim.slide_in_left);
    }

    private void makeLinkInDescriptionClickable(TextView licenseTypeTextView) {

        String apacheLicenseText = getString(R.string.apache_license_type);
        String apacheUrlText = getString(R.string.apache_license_url);

        // Apache Spannables setup
        SpannableString apacheSpannableString = new SpannableString(apacheLicenseText);
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View textView) {
                openUrl(R.string.apache_license_url);
            }

            @Override
            public void updateDrawState(TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
            }
        };
        int apacheStart = apacheLicenseText.indexOf(apacheUrlText);
        apacheSpannableString
                .setSpan(clickableSpan, apacheStart, apacheStart + apacheUrlText.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        licenseTypeTextView.setText(apacheSpannableString);
        licenseTypeTextView.setMovementMethod(LinkMovementMethod.getInstance());
        licenseTypeTextView.setHighlightColor(Color.TRANSPARENT);
    }

    private void openUrl(int urlRes) {
        Intent privacyIntent = new Intent(Intent.ACTION_VIEW);
        privacyIntent.setData(Uri.parse(getString(urlRes)));
        startActivity(privacyIntent);
    }

    @Override
    void setupImmersive() {
        // Standard Android full-screen functionality.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

}
