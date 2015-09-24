/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.switchaccess;

import android.content.Context;
import android.text.TextUtils;
import com.android.utils.AccessibilityNodeInfoUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ProbabilityModelReader is the bridge between the PPMTrie and the HuffmanTreeBuilder. It provides
 * the needed probability distribution to the HuffmanTreeBuilder, which in turns is obtained
 * through the PPMTrie interface.
 */
public class ProbabilityModelReader {

    Map<Integer, Double> mModel;
    PPMTrie mPPMTrie;
    /* TODO(rmorina) This should be a file descriptor of the training file, but is temporarily 0 */
    int probabilityModelResource = 0;
    private final Context mContext;

    public ProbabilityModelReader(Context context, int userContextOrder) {
        mPPMTrie = new PPMTrie(userContextOrder);
        mPPMTrie.initializeTrie(context, probabilityModelResource);
        mContext = context;
    }

    /**
     * Given a set of SwitchAccessNodeCompats, computes an integer representation for each
     * of them.
     *
     * @param nodeInfoCompats The SwitchAccessNodeCompats whose representation we are
     *        interested in.
     * @return Returns a map that associates each of the SwitchAccessNodeCompats in the set
     *         {@code nodeInfoCompats} to an int representation.
     */
    /* TODO(rmorina) This should probably be in a separate class. Ultimately we want to be able to
     *  represent any of the SwitchAcccessNodeCompat as an Integer. It will be a more involved
     *  process than simply getting the content description for the node.
     */
    private Map<SwitchAccessNodeCompat, Integer> getNodeRepresentation(
            Set<SwitchAccessNodeCompat> nodeInfoCompats) {
        Map<SwitchAccessNodeCompat, Integer> nodeRepresentation = new HashMap<>();
        for (SwitchAccessNodeCompat compatNode : nodeInfoCompats) {
            if (!TextUtils.isEmpty(AccessibilityNodeInfoUtils.getNodeText(compatNode))) {
                String nodeContent = AccessibilityNodeInfoUtils.getNodeText(compatNode).toString();
                // TODO(rmorina) The assumption that nodeContent is a char!
                nodeRepresentation.put(compatNode, (int) nodeContent.charAt(0));
            }
            /* TODO(rmorina) Currently if the node's content is null, we just don't have a
             * representation. This is a very temporary implementation and should change once a
             * more robust implementation is developed to represent a
             * SwitchAccessNodeCompat. This implementation works for the keyboard though. */
        }
        return nodeRepresentation;
    }

    /**
     * Given the user context, computes the probability for all the SwitchAccessNodeCompats in
     * the set of {@code nodeInfoCompats}.
     *
     * @param userContext The context represent the actions that the user has taken so far.
     * @param nodeInfoCompats The SwitchAccessNodeCompats whose probability value we are
     *        interested in.
     * @return Returns a map that associates each of the SwitchAccessNodeCompats in the set
     *         {@code nodeInfoCompats} to a probability value.
     */
    public Map<SwitchAccessNodeCompat, Double> getProbabilityDistribution(String userContext,
            Set<SwitchAccessNodeCompat> nodeInfoCompats) {
        Map<SwitchAccessNodeCompat, Integer> nodesRepresentation =
                getNodeRepresentation(nodeInfoCompats);
        Map<Integer, Double> nodeRepresentationProbabilities =
                mPPMTrie.getProbabilityDistribution(userContext,
                        new HashSet<>(nodesRepresentation.values()));
        Map<SwitchAccessNodeCompat, Double> compatNodesProbabilities = new HashMap<>();
        for (SwitchAccessNodeCompat compatNode : nodeInfoCompats) {
            Integer nodeRepresentation = nodesRepresentation.get(compatNode);
            if (nodeRepresentation != null) {
                compatNodesProbabilities.put(compatNode,
                        nodeRepresentationProbabilities.get(nodeRepresentation));
            } else {
                /* TODO(rmorina) This should not happen once we have a robust way to represent
                 * each AccessibilityNodeInfoCompat */
                Double defaultProbability = 1.0 / nodeInfoCompats.size();
                compatNodesProbabilities.put(compatNode, defaultProbability);
            }
        }
        return compatNodesProbabilities;
    }
}