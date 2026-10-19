package com.imagipoint.watch.gestures

/**
 * Labels verified from the supplied model metadata and labels.json.
 * Runtime loading additionally checks the bundled assets and output tensor size.
 */
object GestureLabels {
    val VERIFIED: List<String> = listOf(
        "Clapping",
        "Coin Flipping",
        "Finger Snapping",
        "Fist Making",
        "Horizontal Wrist Extension",
        "Index Finger Flick",
        "Index Thumb Tap",
        "Shooting",
        "Thumb Up",
        "Wrist Extension",
        "Wrist Flexion",
    )
    val VERIFIED_SET: Set<String> = VERIFIED.toSet()
}
