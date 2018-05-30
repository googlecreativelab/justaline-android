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

/**
 * The callback interface for a dialog box. The single method is invoked when the OK button of the
 * dialog is pressed.
 */
public interface DialogOkListener {

    /**
     * This method is called by the dialog box when its OK button is pressed.
     *
     * @param dialogValue The string value provided by the dialog box.
     */
    void onOkPressed(String dialogValue);
}
