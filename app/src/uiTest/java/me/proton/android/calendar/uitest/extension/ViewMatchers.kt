package me.proton.android.calendar.uitest.extension

import android.view.View
import android.view.ViewGroup
import androidx.core.view.descendants
import androidx.test.espresso.matcher.BoundedMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher

object ViewMatchers {
    fun withChildMatcher(childMatcher: Matcher<View>, childCount: Int?) =
        object : BoundedMatcher<View, ViewGroup>(ViewGroup::class.java) {

            override fun matchesSafely(item: ViewGroup): Boolean =
                item.descendants.filter { childMatcher.matches(it) }.count().let {
                    if (childCount == null) it > 0 else it == childCount
                }

            override fun describeTo(description: Description) =
                description
                    .appendText("ViewGroup with child count == $childCount")
                    .let { childMatcher.describeTo(it) }
        }
}
