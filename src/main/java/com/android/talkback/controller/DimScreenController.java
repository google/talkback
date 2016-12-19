/*
 * Copyright 2015 Google Inc.
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

package com.android.talkback.controller;

public interface DimScreenController {

    /**
     * @return Whether the dim-screen setting is enabled in shared preferences. This corresponds to
     * the actual dimming state except when TalkBack is suspended or off.
     */
    public boolean isDimmingEnabled();

    /**
     * @return whether exit dim screen instruction is displayed on screen
     */
    public boolean isInstructionDisplayed();

    /**
     * Turns dimming off and sets the shared preference off as well.
     */
    public void disableDimming();

    /**
     * By default, shows a dialog warning the user before dimming the screen.
     * If the user has elected to not show the dialog, or the user selects "OK" from the warning
     * dialog, this method will turn dimming on and set the shared preference on as well.
     */
    public void showDimScreenDialog();

    public void resume();
    public void suspend();
    public void shutdown();
}
