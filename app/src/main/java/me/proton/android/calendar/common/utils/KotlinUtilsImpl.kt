package me.proton.android.calendar.common.utils

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import me.proton.android.calendar.domain.utils.KotlinUtils
import kotlin.time.Duration

object KotlinUtilsImpl : KotlinUtils {

    /**
     * Assumes that elements matching the predicate will be continous in the list,
     * so it breaks the loop eagerly.
     */
    override fun <T> List<T>.filterFromTheEnd(predicate: (T) -> Boolean): List<T> {

        val filtered = mutableListOf<T>()
        var insideWindow = false
        for (i in (this.size - 1) downTo 0) {
            if (predicate.invoke(this[i])) {
                insideWindow = true
                filtered.add(0, this[i])
            } else {
                if (insideWindow) break
            }
        }

        return filtered
    }

    @OptIn(FlowPreview::class)
    override fun <T> Flow<T>.debounceExceptFirst(timeout: Duration): Flow<T> {
        return this
            .runningFold(0 to null as T?) { (index, _), item ->
                (index + 1) to item
            }
            .drop(1) // drops initial dummy value
            .debounce { (index, _) ->
                // first legitimate item emitted has index == 1
                if (index == 1) 0L else timeout.inWholeMilliseconds
            }
            .map { it.second!! }
    }

}
