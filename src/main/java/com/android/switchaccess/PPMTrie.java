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
import android.util.Log;
import com.android.utils.LogUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Builds a trie to be used for prediction by partial matching. A PPM model maintains the
 * frequencies for actions that have been seen before in all context that have occurred, up to
 * some maximum order.
 *
 * Necessary information to understand the PPM model and how to calculate the probability
 * distribution according to this model was obtained from the paper
 * "Implementing the PPM Data Compression Scheme" by Alistair Moffat, found at the following link:
 * http://cs1.cs.nyu.edu/~roweis/csc310-2006/extras/implementing_ppm.pdf
 */
public class PPMTrie {

    private final TrieNode mRoot;
    private final int mTrieDepth;
    private TrieNode mStartInsertionNode;

    public PPMTrie(int depth) {
        mTrieDepth = depth;
        mRoot = new TrieNode('\0');
        mRoot.setVineNode(null);
        mStartInsertionNode = mRoot;
    }

    /**
     * Uses the text in a training file to form a ppm model and store it in a trie. The file is a
     * .txt file that contains plain unicode text.
     *
     * @param fileResource The file to be used for training the ppm model and constructing the
     *        trie.
     */
    public void initializeTrie(Context context, int fileResource) {
        TrieNode startInsertionNode = mRoot;
        InputStream stream = context.getResources().openRawResource(fileResource);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            String input;
            while ((input = reader.readLine()) != null) {
                for (int i = 0; i < input.length(); i++) {
                    startInsertionNode = insertSymbol(startInsertionNode, input.charAt(i));
                }
            }
        } catch (IOException e) {
            LogUtils.log(this, Log.ERROR, "Unable to read PPMTrie input file: %1$s", e.toString());
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch(IOException e) {
                LogUtils.log(this, Log.ERROR, "Unable to close input file: %1$s", e.toString());
            }
        }
    }

    /**
     * Updates the trie to include the specified symbol. When the symbol is inserted into the trie
     * the node that was inserted at the highest level is tracked. This enables us to track the
     * N most recent symbols inserted into the trie, which hence give us the user context.
     *
     * @param symbol The symbol to be updated/inserted into the trie.
     */
    public void learnSymbol(int symbol) {
        mStartInsertionNode = insertSymbol(mStartInsertionNode, symbol);
    }

    public void clearUserContext() {
        mStartInsertionNode = mRoot;
    }

    /**
     * Given the context, computes the probability for all the symbols in the set of
     * {@code symbols}. If a symbol doesn't appear in the context we are given, we escape to a
     * lower order context and attempt to find the symbol in the lower order context. If we escape
     * from every context and can't find the symbol, we assign a default probability, which is a
     * uniform distribution based on the number of symbols in the {@code symbols} set.
     *
     * To compute the escape probability the well known PPM method C is used, in which at any
     * context, the escape is counted as having occurred a number of times equal to the number of
     * unique symbols encountered in the context, with the total context count inflated by the same
     * amount. Finally, the principle of exclusion is applied when calculating the probabilities:
     * when switching to a lower order context, the count of characters that occurred in the
     * higher context is excluded and only those characters that did not occur in a higher-order
     * context is considered.
     *
     * For further details on how and why the escape count in calculated in the following way
     * refer to the paper found at this link:
     * http://cs1.cs.nyu.edu/~roweis/csc310-2006/extras/implementing_ppm.pdf
     *
     * @param userContext The actions that the user has taken so far.
     * @param symbols The set of symbol whose probability we're interested in.
     * @return A map associating each symbol with a probability value.
     */
    public Map<Integer, Double> getProbabilityDistribution(String userContext,
            Set<Integer> symbols) {
        Map<Integer, Double> probabilityDistribution = new HashMap<>(symbols.size());
        if (symbols.size() == 0) {
            return probabilityDistribution;
        }
        TrieNode node = lookupTrieNode(mRoot, userContext, 0);
        if (node != null) {
            double escapeProbability = 1.0;
            Set<Integer> seenSymbols = new HashSet<>();
            int currentOrder = getNodeDepth(node);
            while (currentOrder >= 0) {
                LinkedList<TrieNode> children = node.getChildren();
                /* the escape character is counted as having occurred a number of times equal to
                 * the number of unique symbols encountered in the context, hence adding the
                 * children.size() to the node count.
                 */
                int parentCount = node.getCount() + children.size();
                int exclusionCount = parentCount;
                for (TrieNode child : children) {
                    if (seenSymbols.contains(child.getContent())) {
                        /* symbols that have been seen in higher order contexts can be excluded
                         * from consideration in lower contexts so that increased probabilities can
                         * be allocated to the remaining symbols. */
                        exclusionCount -= child.getCount();
                    }
                    seenSymbols.add(child.getContent());
                    if (symbols.contains(child.getContent()) &&
                            !probabilityDistribution.containsKey(child.getContent())) {
                        Double childProbability =
                                (escapeProbability * child.getCount()) / parentCount;
                        probabilityDistribution.put(child.getContent(), childProbability);
                    }
                }
                escapeProbability = escapeProbability * children.size() / exclusionCount ;
                node = node.getVineNode();
                currentOrder--;
            }
        }
        assignDefaultProbability(symbols, probabilityDistribution);
        return probabilityDistribution;
    }

    /**
     * The {@code startInsertionNode} points to the TrieNode with symbol X where insertion should
     * begin. If this TrieNode is at a depth less then the max trie depth, the symbol is inserted
     * as a child of that node. Then the vine pointer from TrieNode with symbol X on depth n is
     * followed to a node with the same symbol X on level n - 1. A node is then inserted as a child
     * of this TrieNode at level n - 1. This process is repeated until a node is inserted as a
     * child of the root node. The vine pointers of all the nodes at depth 1, point to the root of
     * the trie.
     *
     * @param startInsertionNode The TrieNode where insertion should begin.
     * @param symbol The symbol to be inserted into the trie.
     * @return The TrieNode inserted at the highest context. Returning this node helps implicitly
     *         keep track of the user context.
     */
    private TrieNode insertSymbol (TrieNode startInsertionNode, int symbol) {
        int currentLevel = getNodeDepth(startInsertionNode);
        TrieNode currentNode = startInsertionNode;
        TrieNode prevModifiedNode = null;
        TrieNode nodeAtHighestDepth = null;
        while (currentLevel >= 0) {
            if (currentLevel < mTrieDepth) {
                TrieNode child = currentNode.addChild(symbol);
                if (child.getCount() == Integer.MAX_VALUE) {
                    scaleCount(mRoot);
                }
                if (prevModifiedNode == null) {
                    // keep track of the node inserted at the greatest depth.
                    nodeAtHighestDepth = child;
                } else if (prevModifiedNode.getVineNode() == null) {
                    /* if the vineNode reference is null that means the node was recently added and
                     * doesn't point to a node on a lower context. Hence update this reference. */
                    prevModifiedNode.setVineNode(child);
                }
                if (currentNode == mRoot && child.getVineNode() == null) {
                    /* For a node that has been inserted as a child of the root node and doesn't
                     * have a vineNode reference, update the vineNode reference to be the root
                     * node */
                    child.setVineNode(mRoot);
                }
                prevModifiedNode = child;
            }
            currentNode = currentNode.getVineNode();
            currentLevel -= 1;
        }
        mRoot.setCount(mRoot.getCount() + 1);
        return nodeAtHighestDepth;
    }

    /* TODO(rmorina) Figure out if there's a more efficient way of scaling without having to
     * scale the entire trie, but rather only certain branches of the trie */
    private void scaleCount(TrieNode rootNode) {
        LinkedList<TrieNode> children = rootNode.getChildren();
        rootNode.setCount(rootNode.getCount() / 2);
        for (TrieNode child : children) {
            scaleCount(child);
        }
    }

    /**
     * Given the context, tries to find the context of greatest length within the trie. The maximum
     * length will naturally be the max depth of the trie. Null is returned only if even a context
     * of length 1 can't be found.
     *
     * @param rootNode The trie node from where the search should begin
     * @param userContext The overall context we are searching
     * @param index The position in the context from where to begin searching.
     * @return The TrieNode found that matches the the max possible components of the context. If
     *         even a context of length 1 can't be found, {@code null} is returned.
     */
    private TrieNode lookupTrieNode(TrieNode rootNode, String userContext, int index) {
        if (index >= userContext.length()) {
            rootNode = (rootNode == mRoot) ? null : rootNode;
            return rootNode;
        }
        int curContent = (int) userContext.charAt(index);
        if (rootNode.hasChild(curContent)) {
            return lookupTrieNode(rootNode.getChild(curContent), userContext,
                    index + 1);
        } else if (rootNode == mRoot) {
            /* could not find context, trying to find a context starting at the next element in
             * the userContext */
            return lookupTrieNode(rootNode, userContext, index + 1);
        } else {
            return lookupTrieNode(rootNode.getVineNode(), userContext, index);
        }
    }

    /**
     * Given a set of symbols whose probability we're interested in and a map which associates a
     * subset of these symbols to probability value, finds the symbols in the set that are not in
     * the map and assigns them a default probability. It is possible that all or none of the
     * symbols in the set are included in the map as well. The default probability is a uniform
     * distribution based on the number of symbols in the {@code symbols} set.
     *
     * @param symbols The set of symbols, whose probability value we're interested in.
     * @param probabilityDistribution The map that associates a probability values to a subset of
     *        symbols in the {@code symbols} set. If a symbol is in the set but not in the map, a
     *        default probability is assigned to the symbol.
     */
    private static void assignDefaultProbability(Set<Integer> symbols,
             Map<Integer, Double> probabilityDistribution) {
        int unassignedSymbolsSize = symbols.size() - probabilityDistribution.size();
        if (unassignedSymbolsSize > 0) {
            Double totalProbability = 0.0;
            for (Double value : probabilityDistribution.values()) {
                totalProbability += value;
            }
            Double missingProbabilityMass = 1.0 - totalProbability;
            Double defaultProbability = missingProbabilityMass / unassignedSymbolsSize;
            for (Integer symbol : symbols) {
                if (probabilityDistribution.get(symbol) == null) {
                    probabilityDistribution.put(symbol, defaultProbability);
                }
            }
        }
    }

    /**
     * Given a TrieNode, finds the node depth by counting the number of vine pointers that have to
     * be followed to reach the root of the trie.
     *
     * @param node The TrieNode whose depth we've interested in.
     * @return The depth of the node
     */
    private int getNodeDepth(TrieNode node) {
        if (node == mRoot) {
            return 0;
        }
        return getNodeDepth(node.getVineNode()) + 1;
    }

    /**
     * Prints the trie. This method is intended for debugging.
     *
     * @param node The TriNode from which printing should begin
     * @param prefix The trie prefix
     * @param index The index of the next free spot in the prefix array
     * @param debugPrefix Any prefix that should be prepended to each line.
     */
    @SuppressWarnings("unused")
    public void printTrie(TrieNode node, char[] prefix, int index, String debugPrefix) {
        LinkedList<TrieNode> children = node.getChildren();
        LogUtils.log(this, Log.DEBUG, "%1$s: current Prefix %2$s", debugPrefix, new String(prefix));
        LogUtils.log(this, Log.DEBUG, "%1$s: children size %2$d", debugPrefix, children.size());
        for (TrieNode child : children) {
            LogUtils.log(this, Log.INFO, "%1$s: Prefix children %2$c : %3$d", debugPrefix,
                    (char) child.getContent(), child.getCount());
        }
        for (TrieNode child : children) {
            char content = (char) child.getContent();
            prefix[index] = content;
            printTrie(child, prefix, index + 1, debugPrefix + "-");
        }
        if (index > 0) {
            prefix[index - 1] = ' ';
        }
    }

    /**
     * The trie nodes
     */
    private class TrieNode {
        /* The content is an int representation for an AccessibilityNodeInfoCompat. For
         * AccessibilityNodeInfoCompats that represent each of the symbols in the keyboard,
         * the unicode for the first character of the content description of these
         * AccessibilityNodeInfoCompat is obtained. For other views a hashing function is probably
         * needed to enable this int representation. */
        private final int mContent;
        /* TODO(rmorina) Consider using a sparse array */
        private final LinkedList<TrieNode> mChildren;
        /* The number of times we can seen the content */
        private int mCount;
        /* The vine node is a reference to a node with the same content on a depth level one less
         * than the depth of the current node. */
        private TrieNode mVineNode;

        public TrieNode(int content) {
            mContent = content;
            mCount = 0;
            mChildren = new LinkedList<>();
            mVineNode = null;
        }

        public int getContent() {
            return mContent;
        }

        public int getCount() {
            return mCount;
        }

        public void setCount(int updatedValue) {
            mCount = updatedValue;
        }

        public TrieNode getVineNode() {
            return mVineNode;
        }

        public void setVineNode(TrieNode trieNode) {
            mVineNode = trieNode;
        }

        public LinkedList<TrieNode> getChildren() {
            return mChildren;
        }

        public TrieNode getChild(int content) {
            for (TrieNode child : mChildren) {
                if (child.getContent() == content) {
                    return child;
                }
            }
            return null;
        }

        public TrieNode addChild(int content) {
            TrieNode child = getChild(content);
            if (child == null) {
                child = new TrieNode(content);
                mChildren.add(child);
            }
            child.mCount += 1;
            return child;
        }

        public boolean hasChild(int content) {
            TrieNode child = getChild(content);
            return child != null;
        }
    }
}
