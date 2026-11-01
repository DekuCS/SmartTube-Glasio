package io.github.ivanosh.stmaterial.logic

/** What Back does on the home screen before it may leave the app. */
enum class BackStep { FOCUS_TABS, SELECT_START, LEAVE }

/**
 * Content goes to the tabs, a tab that is not the start tab goes to the start tab, and only on the
 * start tab Back is the activity's (the double press that exits).
 */
fun backStep(contentFocused: Boolean, selected: Int?, start: Int, tabs: List<Int>): BackStep = when {
    contentFocused -> BackStep.FOCUS_TABS
    selected != null && selected != start && start in tabs -> BackStep.SELECT_START
    else -> BackStep.LEAVE
}
