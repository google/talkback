/*
 * Copyright (C) 2012 Google Inc.
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

package com.android.utils;

import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

public class NodeUtils {
    /**
     * Returns the speech rule as an XML string.
     * <p>
     * The implementation is simplified and utilizes knowledge about speech rule
     * syntax. Node attributes are not processed, empty nodes are not avoided,
     * and all nodes are assumed to be either Element or Text. This is required
     * since we build against 1.6 which does not support Node.getTextContent()
     * API.
     * </p>
     *
     * @param node The currently processed node.
     */
    public static String asXmlString(Node node) {
        final StringBuilder builder = new StringBuilder();
        asXmlStringRecursive(node, builder);
        return builder.toString();
    }

    /**
     * Recursive helper method for that returns s speech rule as an XML string.
     *
     * @param node The currently processed node.
     * @param stringBuilder The builder which accumulates the XML string.
     */
    private static void asXmlStringRecursive(Node node, StringBuilder stringBuilder) {
        final int nodeType = node.getNodeType();

        switch (nodeType) {
            case Node.ELEMENT_NODE: {
                stringBuilder.append("<");
                stringBuilder.append(node.getNodeName());
                stringBuilder.append(">");

                final NodeList childNodes = node.getChildNodes();

                for (int i = 0, count = childNodes.getLength(); i < count; i++) {
                    Node childNode = childNodes.item(i);
                    asXmlStringRecursive(childNode, stringBuilder);
                }

                stringBuilder.append("</");
                stringBuilder.append(node.getNodeName());
                stringBuilder.append(">");
                break;
            }
            case Node.TEXT_NODE: {
                final Text text = (Text) node;
                stringBuilder.append(text.getData());
                break;
            }
        }
    }
}
