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
 * Created by Kat on 2/15/18.
 * Dialog brought up when we fail to stop recording
 */

public class ErrorDialog extends BaseDialog {

    private static final String ARG_TITLE = "title";

    private static final String ARG_MESSAGE = "message";

    private static final String ARG_EXIT_ON_OK = "exitOnOk";

    private Listener mListener;

    public static ErrorDialog newInstance(int titleMessageRes, int errorMessageRes, boolean exitOnOk) {
        ErrorDialog errorDialog = new ErrorDialog();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, titleMessageRes);
        args.putInt(ARG_MESSAGE, errorMessageRes);
        args.putBoolean(ARG_EXIT_ON_OK, exitOnOk);
        errorDialog.setArguments(args);

        return errorDialog;
    }

    public static ErrorDialog newInstance(int errorMessageRes, boolean exitOnOk) {
        ErrorDialog errorDialog = new ErrorDialog();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putInt(ARG_TITLE, -1);
        args.putInt(ARG_MESSAGE, errorMessageRes);
        args.putBoolean(ARG_EXIT_ON_OK, exitOnOk);
        errorDialog.setArguments(args);

        return errorDialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        int titleRes = getArguments().getInt(ARG_TITLE);
        int messageRes = getArguments().getInt(ARG_MESSAGE);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext()).setMessage(messageRes);

        if (titleRes > -1) builder.setTitle(titleRes);

        // Set up the buttons
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (getArguments().getBoolean(ARG_EXIT_ON_OK) && mListener != null) {
                    mListener.exitApp();
                }
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

        void exitApp();
    }
}
