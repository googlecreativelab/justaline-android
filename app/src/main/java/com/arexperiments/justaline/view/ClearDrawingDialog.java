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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.WindowManager;

import com.arexperiments.justaline.R;

/**
 * Created by Kat on 12/4/17.
 * Dialog brought up when user selects clear drawing
 */

public class ClearDrawingDialog extends BaseDialog {

    private Listener mListener;

    private static final String ARG_PAIRED_SESSION = "pairedSession";

    public static ClearDrawingDialog newInstance(boolean paired) {
        ClearDrawingDialog dialog = new ClearDrawingDialog();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putBoolean(ARG_PAIRED_SESSION, paired);
        dialog.setArguments(args);

        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        int titleRes = -1;
        int messageRes = R.string.clear_confirmation_message;

        boolean pairedSession = getArguments().getBoolean(ARG_PAIRED_SESSION);
        if (pairedSession) {
            titleRes = R.string.clear_confirmation_title_paired;
            messageRes = R.string.clear_confirmation_message_paired;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setMessage(messageRes);

        if (titleRes > -1) builder.setTitle(titleRes);

        // Set up the buttons
        builder.setPositiveButton(R.string.clear, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mListener != null) {
                    mListener.onClearDrawingConfirmed();
                }
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        AlertDialog dialog = builder.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }

        setCancelable(false);

        return dialog;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        try {
            mListener = (Listener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.getClass() + " must implement Listener");
        }

    }

    @Override
    public void onDetach() {
        super.onDetach();

        mListener = null;
    }

    public interface Listener {

        void onClearDrawingConfirmed();
    }
}
