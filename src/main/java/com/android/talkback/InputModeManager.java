/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.talkback;

import android.support.annotation.IntDef;
/**
 * InputModeManager manages input mode which user is currently using to interact with TalkBack.
 */
public class InputModeManager {

    @IntDef({INPUT_MODE_UNKNOWN, INPUT_MODE_TOUCH, INPUT_MODE_KEYBOARD, INPUT_MODE_TV_REMOTE})
    public @interface InputMode {}

    public static final int INPUT_MODE_UNKNOWN = -1;
    public static final int INPUT_MODE_TOUCH = 0;
    public static final int INPUT_MODE_KEYBOARD = 1;
    public static final int INPUT_MODE_TV_REMOTE = 2;

    private @InputMode int mInputMode = INPUT_MODE_UNKNOWN;

    public void clear() {
        mInputMode = INPUT_MODE_UNKNOWN;
    }

    public void setInputMode(@InputMode int inputMode) {
        if (inputMode == INPUT_MODE_UNKNOWN) {
            return;
        }

        mInputMode = inputMode;
    }

    public @InputMode int getInputMode() {
        return mInputMode;
    }

}
