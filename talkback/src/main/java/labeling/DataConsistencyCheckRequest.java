/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.labeling;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.text.TextUtils;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.labeling.LabelProviderClient;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Filters a group of labels, removing labels with valid package name & hash. */
public class DataConsistencyCheckRequest extends LabelClientRequest<@Nullable List<Label>> {

  private static final String TAG = "DataConsistencyCheckReq";

  private Context context;
  private OnDataConsistencyCheckCallback callback;

  public DataConsistencyCheckRequest(
      LabelProviderClient client, Context context, OnDataConsistencyCheckCallback callback) {
    super(client);
    this.context = context;
    this.callback = callback;
  }

  @Override
  public @Nullable List<Label> doInBackground() {
    final List<Label> allLabels = mClient.getCurrentLabels();

    if ((allLabels == null) || allLabels.isEmpty()) {
      return null;
    }

    final PackageManager pm = context.getPackageManager();

    final List<Label> candidates = new ArrayList<>(allLabels);
    ListIterator<Label> i = candidates.listIterator();

    // Iterate through the labels database, and prune labels that belong
    // to valid packages.
    while (i.hasNext()) {
      final Label l = i.next();

      // Ensure the label has a matching installed package.
      final String packageName = l.getPackageName();
      PackageInfo packageInfo;
      try {
        packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
      } catch (PackageManager.NameNotFoundException e) {
        // If there's no installed package, leave the label in the
        // list for removal.
        LogUtils.v(TAG, "Consistency check removing label for unknown package %s.", packageName);
        continue;
      }

      // Ensure the signature hash of the application matches
      // the hash of the package when the label was stored.
      final String expectedHash = l.getPackageSignature();
      final String actualHash = computePackageSignatureHash(packageInfo);
      if (TextUtils.isEmpty(expectedHash)
          || TextUtils.isEmpty(actualHash)
          || !expectedHash.equals(actualHash)) {
        // If the expected or actual signature hashes aren't
        // valid, or they don't match, leave the label in the list
        // for removal.
        LogUtils.w(
            TAG,
            "Consistency check removing label due to signature mismatch " + "for package %s.",
            packageName);
        continue;
      }

      // If the label has passed all consistency checks, prune the
      // label from the list of potentials for removal.
      i.remove();
    }

    return candidates; // now containing only labels for removal
  }

  @Override
  public void onPostExecute(@Nullable List<Label> labelsToRemove) {
    if (callback != null) {
      callback.onConsistencyCheckCompleted(labelsToRemove);
    }
  }

  private static String computePackageSignatureHash(PackageInfo packageInfo) {
    String signatureHash = "";

    final Signature[] sigs = packageInfo.signatures;
    try {
      final MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
      for (Signature s : sigs) {
        messageDigest.update(s.toByteArray());
      }

      signatureHash = StringBuilderUtils.bytesToHexString(messageDigest.digest());
    } catch (NoSuchAlgorithmException e) {
      LogUtils.w(TAG, "Unable to create SHA-1 MessageDigest");
    }

    return signatureHash;
  }

  public interface OnDataConsistencyCheckCallback {
    public void onConsistencyCheckCompleted(@Nullable List<Label> labelsToRemove);
  }
}
