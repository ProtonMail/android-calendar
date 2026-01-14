package me.proton.android.calendar.uitest.extension

import me.proton.test.fusion.ui.espresso.builders.OnView

fun OnView.hasChildren(childMatcher: OnView, childCount: Int? = null) =
    addViewMatcher(ViewMatchers.withChildMatcher(childMatcher.finalMatcher, childCount))