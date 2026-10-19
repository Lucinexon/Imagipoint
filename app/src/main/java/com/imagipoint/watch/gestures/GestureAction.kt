package com.imagipoint.watch.gestures

/** Mouse-only actions that can safely be triggered by a verified gesture class. */
enum class GestureAction(val displayName: String, val description: String) {
    NONE("No action", "Ignore this gesture"),
    LEFT_CLICK("Left click", "Click the primary mouse button"),
    RIGHT_CLICK("Right click", "Click the secondary mouse button"),
    MIDDLE_CLICK("Middle click", "Click the middle mouse button"),
    SCROLL_UP("Scroll up", "Send one wheel notch up"),
    SCROLL_DOWN("Scroll down", "Send one wheel notch down"),
}
