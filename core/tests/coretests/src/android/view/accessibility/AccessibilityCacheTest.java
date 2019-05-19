/*
 * Copyright 2016 The Android Open Source Project
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

package android.view.accessibility;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class AccessibilityCacheTest {
    private static final int WINDOW_ID_1 = 0xBEEF;
    private static final int WINDOW_ID_2 = 0xFACE;
    private static final int SINGLE_VIEW_ID = 0xCAFE;
    private static final int OTHER_VIEW_ID = 0xCAB2;
    private static final int PARENT_VIEW_ID = 0xFED4;
    private static final int CHILD_VIEW_ID = 0xFEED;
    private static final int OTHER_CHILD_VIEW_ID = 0xACE2;
    private static final int MOCK_CONNECTION_ID = 1;

    AccessibilityCache mAccessibilityCache;
    AccessibilityCache.AccessibilityNodeRefresher mAccessibilityNodeRefresher;
    AtomicInteger mNumA11yNodeInfosInUse = new AtomicInteger(0);
    AtomicInteger mNumA11yWinInfosInUse = new AtomicInteger(0);

    @Before
    public void setUp() {
        mAccessibilityNodeRefresher = mock(AccessibilityCache.AccessibilityNodeRefresher.class);
        when(mAccessibilityNodeRefresher.refreshNode(anyObject(), anyBoolean())).thenReturn(true);
        mAccessibilityCache = new AccessibilityCache(mAccessibilityNodeRefresher);
        AccessibilityNodeInfo.setNumInstancesInUseCounter(mNumA11yNodeInfosInUse);
        AccessibilityWindowInfo.setNumInstancesInUseCounter(mNumA11yWinInfosInUse);
    }

    @After
    public void tearDown() {
        // Make sure we're recycling all of our window and node infos
        mAccessibilityCache.clear();
        AccessibilityInteractionClient.getInstance().clearCache();
        assertEquals(0, mNumA11yWinInfosInUse.get());
        assertEquals(0, mNumA11yNodeInfosInUse.get());
    }

    @Test
    public void testEmptyCache_returnsNull() {
        assertNull(mAccessibilityCache.getNode(0, 0));
        assertNull(mAccessibilityCache.getWindows());
        assertNull(mAccessibilityCache.getWindow(0));
    }

    @Test
    public void testEmptyCache_clearDoesntCrash() {
        mAccessibilityCache.clear();
    }

    @Test
    public void testEmptyCache_a11yEventsHaveNoEffect() {
        AccessibilityEvent event = AccessibilityEvent.obtain();
        int[] a11yEventTypes = {
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED,
                AccessibilityEvent.TYPE_VIEW_FOCUSED,
                AccessibilityEvent.TYPE_VIEW_SELECTED,
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.TYPE_VIEW_SCROLLED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED};
        for (int i = 0; i < a11yEventTypes.length; i++) {
            event.setEventType(a11yEventTypes[i]);
            mAccessibilityCache.onAccessibilityEvent(event);
        }
    }

    @Test
    public void addThenGetWindow_returnsEquivalentButNotSameWindow() {
        AccessibilityWindowInfo windowInfo = null, copyOfInfo = null, windowFromCache = null;
        try {
            windowInfo = AccessibilityWindowInfo.obtain();
            windowInfo.setId(WINDOW_ID_1);
            mAccessibilityCache.addWindow(windowInfo);
            // Make a copy
            copyOfInfo = AccessibilityWindowInfo.obtain(windowInfo);
            windowInfo.setId(WINDOW_ID_2); // Simulate recycling and reusing the original info
            windowFromCache = mAccessibilityCache.getWindow(WINDOW_ID_1);
            assertEquals(copyOfInfo, windowFromCache);
        } finally {
            windowFromCache.recycle();
            windowInfo.recycle();
            copyOfInfo.recycle();
        }
    }

    @Test
    public void addWindowThenClear_noLongerInCache() {
        putWindowWithIdInCache(WINDOW_ID_1);
        mAccessibilityCache.clear();
        assertNull(mAccessibilityCache.getWindow(WINDOW_ID_1));
    }

    @Test
    public void addWindowGetOtherId_returnsNull() {
        putWindowWithIdInCache(WINDOW_ID_1);
        assertNull(mAccessibilityCache.getWindow(WINDOW_ID_1 + 1));
    }

    @Test
    public void addWindowThenGetWindows_returnsNull() {
        putWindowWithIdInCache(WINDOW_ID_1);
        assertNull(mAccessibilityCache.getWindows());
    }

    @Test
    public void setWindowsThenGetWindows_returnsInDecreasingLayerOrder() {
        AccessibilityWindowInfo windowInfo1 = null, windowInfo2 = null;
        AccessibilityWindowInfo window1Out = null, window2Out = null;
        List<AccessibilityWindowInfo> windowsOut = null;
        try {
            windowInfo1 = AccessibilityWindowInfo.obtain();
            windowInfo1.setId(WINDOW_ID_1);
            windowInfo1.setLayer(5);
            windowInfo2 = AccessibilityWindowInfo.obtain();
            windowInfo2.setId(WINDOW_ID_2);
            windowInfo2.setLayer(windowInfo1.getLayer() + 1);
            List<AccessibilityWindowInfo> windowsIn = Arrays.asList(windowInfo1, windowInfo2);
            mAccessibilityCache.setWindows(windowsIn);

            windowsOut = mAccessibilityCache.getWindows();
            window1Out = mAccessibilityCache.getWindow(WINDOW_ID_1);
            window2Out = mAccessibilityCache.getWindow(WINDOW_ID_2);

            assertEquals(2, windowsOut.size());
            assertEquals(windowInfo2, windowsOut.get(0));
            assertEquals(windowInfo1, windowsOut.get(1));
            assertEquals(windowInfo1, window1Out);
            assertEquals(windowInfo2, window2Out);
        } finally {
            window1Out.recycle();
            window2Out.recycle();
            windowInfo1.recycle();
            windowInfo2.recycle();
            for (AccessibilityWindowInfo windowInfo : windowsOut) {
                windowInfo.recycle();
            }
        }
    }

    @Test
    public void addWindowThenStateChangedEvent_noLongerInCache() {
        putWindowWithIdInCache(WINDOW_ID_1);
        mAccessibilityCache.onAccessibilityEvent(
                AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED));
        assertNull(mAccessibilityCache.getWindow(WINDOW_ID_1));
    }

    @Test
    public void addWindowThenWindowsChangedEvent_noLongerInCache() {
        putWindowWithIdInCache(WINDOW_ID_1);
        mAccessibilityCache.onAccessibilityEvent(
                AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOWS_CHANGED));
        assertNull(mAccessibilityCache.getWindow(WINDOW_ID_1));
    }

    @Test
    public void addThenGetNode_returnsEquivalentNode() {
        AccessibilityNodeInfo nodeInfo, nodeCopy = null, nodeFromCache = null;
        try {
            nodeInfo = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_1);
            long id = nodeInfo.getSourceNodeId();
            nodeCopy = AccessibilityNodeInfo.obtain(nodeInfo);
            mAccessibilityCache.add(nodeInfo);
            nodeInfo.recycle();
            nodeFromCache = mAccessibilityCache.getNode(WINDOW_ID_1, id);
            assertEquals(nodeCopy, nodeFromCache);
        } finally {
            nodeFromCache.recycle();
            nodeCopy.recycle();
        }
    }

    @Test
    public void overwriteThenGetNode_returnsNewNode() {
        final CharSequence contentDescription1 = "foo";
        final CharSequence contentDescription2 = "bar";
        AccessibilityNodeInfo nodeInfo1 = null, nodeInfo2 = null, nodeFromCache = null;
        try {
            nodeInfo1 = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_1);
            nodeInfo1.setContentDescription(contentDescription1);
            long id = nodeInfo1.getSourceNodeId();
            nodeInfo2 = AccessibilityNodeInfo.obtain(nodeInfo1);
            nodeInfo2.setContentDescription(contentDescription2);
            mAccessibilityCache.add(nodeInfo1);
            mAccessibilityCache.add(nodeInfo2);
            nodeFromCache = mAccessibilityCache.getNode(WINDOW_ID_1, id);
            assertEquals(nodeInfo2, nodeFromCache);
            assertEquals(contentDescription2, nodeFromCache.getContentDescription());
        } finally {
            nodeFromCache.recycle();
            nodeInfo2.recycle();
            nodeInfo1.recycle();
        }
    }

    @Test
    public void nodesInDifferentWindowWithSameId_areKeptSeparate() {
        final CharSequence contentDescription1 = "foo";
        final CharSequence contentDescription2 = "bar";
        AccessibilityNodeInfo nodeInfo1 = null, nodeInfo2 = null,
                node1FromCache = null, node2FromCache = null;
        try {
            nodeInfo1 = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_1);
            nodeInfo1.setContentDescription(contentDescription1);
            long id = nodeInfo1.getSourceNodeId();
            nodeInfo2 = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_2);
            nodeInfo2.setContentDescription(contentDescription2);
            assertEquals(id, nodeInfo2.getSourceNodeId());
            mAccessibilityCache.add(nodeInfo1);
            mAccessibilityCache.add(nodeInfo2);
            node1FromCache = mAccessibilityCache.getNode(WINDOW_ID_1, id);
            node2FromCache = mAccessibilityCache.getNode(WINDOW_ID_2, id);
            assertEquals(nodeInfo1, node1FromCache);
            assertEquals(nodeInfo2, node2FromCache);
            assertEquals(nodeInfo1.getContentDescription(), node1FromCache.getContentDescription());
            assertEquals(nodeInfo2.getContentDescription(), node2FromCache.getContentDescription());
        } finally {
            node1FromCache.recycle();
            node2FromCache.recycle();
            nodeInfo1.recycle();
            nodeInfo2.recycle();
        }
    }

    @Test
    public void addNodeThenClear_nodeIsRemoved() {
        AccessibilityNodeInfo nodeInfo = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_1);
        long id = nodeInfo.getSourceNodeId();
        mAccessibilityCache.add(nodeInfo);
        nodeInfo.recycle();
        mAccessibilityCache.clear();
        assertNull(mAccessibilityCache.getNode(WINDOW_ID_1, id));
    }

    @Test
    public void windowStateChangeAndWindowsChangedEvents_clearsNode() {
        assertEventTypeClearsNode(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        assertEventTypeClearsNode(AccessibilityEvent.TYPE_WINDOWS_CHANGED);
    }

    @Test
    public void subTreeChangeEvent_clearsNodeAndChild() {
        AccessibilityEvent event = AccessibilityEvent
                .obtain(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        event.setContentChangeTypes(AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE);
        event.setSource(getMockViewWithA11yAndWindowIds(PARENT_VIEW_ID, WINDOW_ID_1));

        try {
            assertEventClearsParentAndChild(event);
        } finally {
            event.recycle();
        }
    }

    @Test
    public void scrollEvent_clearsNodeAndChild() {
        AccessibilityEvent event = AccessibilityEvent
                .obtain(AccessibilityEvent.TYPE_VIEW_SCROLLED);
        event.setSource(getMockViewWithA11yAndWindowIds(PARENT_VIEW_ID, WINDOW_ID_1));
        try {
            assertEventClearsParentAndChild(event);
        } finally {
            event.recycle();
        }
    }

    @Test
    public void reparentNode_clearsOldParent() {
        AccessibilityNodeInfo parentNodeInfo = getParentNode();
        AccessibilityNodeInfo childNodeInfo = getChildNode();
        long parentId = parentNodeInfo.getSourceNodeId();
        mAccessibilityCache.add(parentNodeInfo);
        mAccessibilityCache.add(childNodeInfo);

        childNodeInfo.setParent(getMockViewWithA11yAndWindowIds(PARENT_VIEW_ID + 1, WINDOW_ID_1));
        mAccessibilityCache.add(childNodeInfo);

        AccessibilityNodeInfo parentFromCache = mAccessibilityCache.getNode(WINDOW_ID_1, parentId);
        try {
            assertNull(parentFromCache);
        } finally {
            parentNodeInfo.recycle();
            childNodeInfo.recycle();
            if (parentFromCache != null) {
                parentFromCache.recycle();
            }
        }
    }

    @Test
    public void removeChildFromParent_clearsChild() {
        AccessibilityNodeInfo parentNodeInfo = getParentNode();
        AccessibilityNodeInfo childNodeInfo = getChildNode();
        long childId = childNodeInfo.getSourceNodeId();
        mAccessibilityCache.add(parentNodeInfo);
        mAccessibilityCache.add(childNodeInfo);

        AccessibilityNodeInfo parentNodeInfoWithNoChildren =
                getNodeWithA11yAndWindowId(PARENT_VIEW_ID, WINDOW_ID_1);
        mAccessibilityCache.add(parentNodeInfoWithNoChildren);

        AccessibilityNodeInfo childFromCache = mAccessibilityCache.getNode(WINDOW_ID_1, childId);
        try {
            assertNull(childFromCache);
        } finally {
            parentNodeInfoWithNoChildren.recycle();
            parentNodeInfo.recycle();
            childNodeInfo.recycle();
            if (childFromCache != null) {
                childFromCache.recycle();
            }
        }
    }

    @Test
    public void nodeSourceOfA11yFocusEvent_getsRefreshed() {
        AccessibilityNodeInfo nodeInfo = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_1);
        nodeInfo.setAccessibilityFocused(false);
        mAccessibilityCache.add(nodeInfo);
        AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        event.setSource(getMockViewWithA11yAndWindowIds(SINGLE_VIEW_ID, WINDOW_ID_1));
        mAccessibilityCache.onAccessibilityEvent(event);
        event.recycle();
        try {
            verify(mAccessibilityNodeRefresher).refreshNode(nodeInfo, true);
        } finally {
            nodeInfo.recycle();
        }
    }

    @Test
    public void nodeWithA11yFocusWhenAnotherNodeGetsFocus_getsRefreshed() {
        AccessibilityNodeInfo nodeInfo = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_1);
        nodeInfo.setAccessibilityFocused(true);
        mAccessibilityCache.add(nodeInfo);
        AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        event.setSource(getMockViewWithA11yAndWindowIds(OTHER_VIEW_ID, WINDOW_ID_1));
        mAccessibilityCache.onAccessibilityEvent(event);
        event.recycle();
        try {
            verify(mAccessibilityNodeRefresher).refreshNode(nodeInfo, true);
        } finally {
            nodeInfo.recycle();
        }
    }

    @Test
    public void nodeWithA11yFocusClearsIt_refreshes() {
        AccessibilityNodeInfo nodeInfo = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_1);
        nodeInfo.setAccessibilityFocused(true);
        mAccessibilityCache.add(nodeInfo);
        AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED);
        event.setSource(getMockViewWithA11yAndWindowIds(SINGLE_VIEW_ID, WINDOW_ID_1));
        mAccessibilityCache.onAccessibilityEvent(event);
        event.recycle();
        try {
            verify(mAccessibilityNodeRefresher).refreshNode(nodeInfo, true);
        } finally {
            nodeInfo.recycle();
        }
    }

    @Test
    public void nodeSourceOfInputFocusEvent_getsRefreshed() {
        AccessibilityNodeInfo nodeInfo = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_1);
        nodeInfo.setFocused(false);
        mAccessibilityCache.add(nodeInfo);
        AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_FOCUSED);
        event.setSource(getMockViewWithA11yAndWindowIds(SINGLE_VIEW_ID, WINDOW_ID_1));
        mAccessibilityCache.onAccessibilityEvent(event);
        event.recycle();
        try {
            verify(mAccessibilityNodeRefresher).refreshNode(nodeInfo, true);
        } finally {
            nodeInfo.recycle();
        }
    }

    @Test
    public void nodeWithInputFocusWhenAnotherNodeGetsFocus_getsRefreshed() {
        AccessibilityNodeInfo nodeInfo = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_1);
        nodeInfo.setFocused(true);
        mAccessibilityCache.add(nodeInfo);
        AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_FOCUSED);
        event.setSource(getMockViewWithA11yAndWindowIds(OTHER_VIEW_ID, WINDOW_ID_1));
        mAccessibilityCache.onAccessibilityEvent(event);
        event.recycle();
        try {
            verify(mAccessibilityNodeRefresher).refreshNode(nodeInfo, true);
        } finally {
            nodeInfo.recycle();
        }
    }

    @Test
    public void nodeEventSaysWasSelected_getsRefreshed() {
        assertNodeIsRefreshedWithEventType(AccessibilityEvent.TYPE_VIEW_SELECTED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
    }

    @Test
    public void nodeEventSaysHadTextChanged_getsRefreshed() {
        assertNodeIsRefreshedWithEventType(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
    }

    @Test
    public void nodeEventSaysWasClicked_getsRefreshed() {
        assertNodeIsRefreshedWithEventType(AccessibilityEvent.TYPE_VIEW_CLICKED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
    }

    @Test
    public void nodeEventSaysHadSelectionChange_getsRefreshed() {
        assertNodeIsRefreshedWithEventType(AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED);
    }

    @Test
    public void nodeEventSaysHadTextContentChange_getsRefreshed() {
        assertNodeIsRefreshedWithEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT);
    }

    @Test
    public void nodeEventSaysHadContentDescriptionChange_getsRefreshed() {
        assertNodeIsRefreshedWithEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
                AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION);
    }

    @Test
    public void addNode_whenNodeBeingReplacedIsOwnGrandparent_doesntCrash() {
        AccessibilityNodeInfo parentNodeInfo =
                getNodeWithA11yAndWindowId(PARENT_VIEW_ID, WINDOW_ID_1);
        parentNodeInfo.addChild(getMockViewWithA11yAndWindowIds(CHILD_VIEW_ID, WINDOW_ID_1));
        parentNodeInfo.addChild(getMockViewWithA11yAndWindowIds(OTHER_CHILD_VIEW_ID, WINDOW_ID_1));
        AccessibilityNodeInfo childNodeInfo =
                getNodeWithA11yAndWindowId(CHILD_VIEW_ID, WINDOW_ID_1);
        childNodeInfo.setParent(getMockViewWithA11yAndWindowIds(PARENT_VIEW_ID, WINDOW_ID_1));
        childNodeInfo.addChild(getMockViewWithA11yAndWindowIds(PARENT_VIEW_ID, WINDOW_ID_1));

        AccessibilityNodeInfo replacementParentNodeInfo =
                getNodeWithA11yAndWindowId(PARENT_VIEW_ID, WINDOW_ID_1);
        try {
            mAccessibilityCache.add(parentNodeInfo);
            mAccessibilityCache.add(childNodeInfo);
            mAccessibilityCache.add(replacementParentNodeInfo);
        } finally {
            parentNodeInfo.recycle();
            childNodeInfo.recycle();
            replacementParentNodeInfo.recycle();
        }
    }

    @Test
    public void testCacheCriticalEventList_doesntLackEvents() {
        for (int i = 0; i < 32; i++) {
            int eventType = 1 << i;
            if ((eventType & AccessibilityCache.CACHE_CRITICAL_EVENTS_MASK) == 0) {
                try {
                    assertEventTypeClearsNode(eventType, false);
                    verify(mAccessibilityNodeRefresher, never())
                            .refreshNode(anyObject(), anyBoolean());
                } catch (Throwable e) {
                    throw new AssertionError(
                            "Failed for eventType: " + AccessibilityEvent.eventTypeToString(
                                    eventType),
                            e);
                }
            }
        }
    }

    private void assertNodeIsRefreshedWithEventType(int eventType, int contentChangeTypes) {
        AccessibilityNodeInfo nodeInfo = getNodeWithA11yAndWindowId(SINGLE_VIEW_ID, WINDOW_ID_1);
        mAccessibilityCache.add(nodeInfo);
        AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
        event.setSource(getMockViewWithA11yAndWindowIds(SINGLE_VIEW_ID, WINDOW_ID_1));
        event.setContentChangeTypes(contentChangeTypes);
        mAccessibilityCache.onAccessibilityEvent(event);
        event.recycle();
        try {
            verify(mAccessibilityNodeRefresher).refreshNode(nodeInfo, true);
        } finally {
            nodeInfo.recycle();
        }
    }

    private void putWindowWithIdInCache(int id) {
        AccessibilityWindowInfo windowInfo = AccessibilityWindowInfo.obtain();
        windowInfo.setId(id);
        mAccessibilityCache.addWindow(windowInfo);
        windowInfo.recycle();
    }

    private AccessibilityNodeInfo getNodeWithA11yAndWindowId(int a11yId, int windowId) {
        AccessibilityNodeInfo node =
                AccessibilityNodeInfo.obtain(getMockViewWithA11yAndWindowIds(a11yId, windowId));
        node.setConnectionId(MOCK_CONNECTION_ID);
        return node;
    }

    private View getMockViewWithA11yAndWindowIds(int a11yId, int windowId) {
        View mockView = mock(View.class);
        when(mockView.getAccessibilityViewId()).thenReturn(a11yId);
        when(mockView.getAccessibilityWindowId()).thenReturn(windowId);
        doAnswer(new Answer<AccessibilityNodeInfo>() {
            public AccessibilityNodeInfo answer(InvocationOnMock invocation) {
                return AccessibilityNodeInfo.obtain((View) invocation.getMock());
            }
        }).when(mockView).createAccessibilityNodeInfo();
        return mockView;
    }

    private void assertEventTypeClearsNode(int eventType) {
        assertEventTypeClearsNode(eventType, true);
    }

    private void assertEventTypeClearsNode(int eventType, boolean clears) {
        final int nodeId = 0xBEEF;
        AccessibilityNodeInfo nodeInfo = getNodeWithA11yAndWindowId(nodeId, WINDOW_ID_1);
        long id = nodeInfo.getSourceNodeId();
        mAccessibilityCache.add(nodeInfo);
        nodeInfo.recycle();
        mAccessibilityCache.onAccessibilityEvent(AccessibilityEvent.obtain(eventType));
        AccessibilityNodeInfo cachedNode = mAccessibilityCache.getNode(WINDOW_ID_1, id);
        try {
            if (clears) {
                assertNull(cachedNode);
            } else {
                assertNotNull(cachedNode);
            }
        } finally {
            if (cachedNode != null) {
                cachedNode.recycle();
            }
        }
    }

    private AccessibilityNodeInfo getParentNode() {
        AccessibilityNodeInfo parentNodeInfo =
                getNodeWithA11yAndWindowId(PARENT_VIEW_ID, WINDOW_ID_1);
        parentNodeInfo.addChild(getMockViewWithA11yAndWindowIds(CHILD_VIEW_ID, WINDOW_ID_1));
        return parentNodeInfo;
    }

    private AccessibilityNodeInfo getChildNode() {
        AccessibilityNodeInfo childNodeInfo =
                getNodeWithA11yAndWindowId(CHILD_VIEW_ID, WINDOW_ID_1);
        childNodeInfo.setParent(getMockViewWithA11yAndWindowIds(PARENT_VIEW_ID, WINDOW_ID_1));
        return childNodeInfo;
    }

    private void assertEventClearsParentAndChild(AccessibilityEvent event) {
        AccessibilityNodeInfo parentNodeInfo = getParentNode();
        AccessibilityNodeInfo childNodeInfo = getChildNode();
        long parentId = parentNodeInfo.getSourceNodeId();
        long childId = childNodeInfo.getSourceNodeId();
        mAccessibilityCache.add(parentNodeInfo);
        mAccessibilityCache.add(childNodeInfo);

        mAccessibilityCache.onAccessibilityEvent(event);
        parentNodeInfo.recycle();
        childNodeInfo.recycle();

        AccessibilityNodeInfo parentFromCache = mAccessibilityCache.getNode(WINDOW_ID_1, parentId);
        AccessibilityNodeInfo childFromCache = mAccessibilityCache.getNode(WINDOW_ID_1, childId);
        try {
            assertNull(parentFromCache);
            assertNull(childFromCache);
        } finally {
            if (parentFromCache != null) {
                parentFromCache.recycle();
            }
            if (childFromCache != null) {
                childFromCache.recycle();
            }
        }
    }
}
