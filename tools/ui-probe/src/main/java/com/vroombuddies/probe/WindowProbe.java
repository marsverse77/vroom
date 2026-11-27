package com.vroombuddies.probe;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Read one accessibility snapshot without waiting for an animated game to become idle. */
public final class WindowProbe extends Instrumentation {
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            UiAutomation ui = getUiAutomation();
            AccessibilityNodeInfo root = null;
            for (int i = 0; i < 12 && root == null; i++) {
                root = ui.getRootInActiveWindow();
                if (root == null) SystemClock.sleep(100);
            }
            JSONArray nodes = new JSONArray();
            if (root != null) walk(root, nodes);
            result.putString("nodes", nodes.toString());
            finish(Activity.RESULT_OK, result);
        } catch (Exception e) {
            result.putString("error", e.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void walk(AccessibilityNodeInfo node, JSONArray nodes) throws JSONException {
        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        CharSequence label = node.getText();
        if (label == null) label = node.getContentDescription();
        if (label != null && node.isVisibleToUser() && !rect.isEmpty()
                && "com.vroombuddies.game".contentEquals(String.valueOf(node.getPackageName()))) {
            nodes.put(new JSONObject().put("text", label.toString())
                    .put("bounds", new JSONArray().put(rect.left).put(rect.top).put(rect.right).put(rect.bottom)));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) walk(child, nodes);
        }
    }
}
