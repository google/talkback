/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.utils.labeling;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import com.android.utils.LogUtils;

import java.util.Collection;
import java.util.Map;

/**
 * {@link BroadcastReceiver} used to remove {@link Label}s when their
 * originating application is removed from the system.
 */
public class PackageRemovalReceiver extends BroadcastReceiver {

    public static final int MIN_API_LEVEL = CustomLabelManager.MIN_API_LEVEL;

    private static final IntentFilter INTENT_FILTER = new IntentFilter(
            Intent.ACTION_PACKAGE_REMOVED);
    static {
        INTENT_FILTER.addDataScheme("package");
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
            final boolean isUpgrade = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
            if (!isUpgrade) {
                final CustomLabelManager labelManager = new CustomLabelManager(ctx);
                final String packageName = intent.getData().toString();
                LogUtils.log(
                        this, Log.VERBOSE, "Package %s removed.  Discarding associated labels.",
                        packageName);

                final PackageLabelsFetchRequest.OnLabelsFetchedListener callback =
                        new PackageLabelsFetchRequest.OnLabelsFetchedListener() {
                    @Override
                    public void onLabelsFetched(Map<String, Label> results) {
                        // Remove each label that matches the removed package
                        // from the label database.
                        if ((results != null) && !results.isEmpty()) {
                            final Collection<Label> labels = results.values();
                            LogUtils.log(this, Log.VERBOSE, "Removing %d labels.", labels.size());
                            for (Label l : labels) {
                                labelManager.removeLabel(l);
                            }
                        }

                        labelManager.shutdown();
                    }
                };

                labelManager.getLabelsForPackageFromDatabase(packageName, callback);
            }
        }
    }

    public IntentFilter getFilter() {
        return INTENT_FILTER;
    }
}
