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
     * @return whether dim setting is enabled
     */
    public boolean isDimmingEnabled();

    /**
     * @return whether exit dim screen instruction is displayed on screen
     */
    public boolean isInstructionDisplayed();

    /**
     * make screen dim if it is bright and vice versa
     */
    public void switchState();

    /**
     * dim the screen
     */
    public void makeScreenDim();

    /**
     * turn dimming off
     */
    public void makeScreenBright();

    public void shutdown();
}
