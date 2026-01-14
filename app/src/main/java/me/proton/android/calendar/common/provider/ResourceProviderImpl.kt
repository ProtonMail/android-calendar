package me.proton.android.calendar.common.provider

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.content.res.ResourcesCompat
import me.proton.android.calendar.domain.ResourceProvider

class ResourceProviderImpl(
    private val resources: Resources
): ResourceProvider {

    override fun provideString(@StringRes resId: Int, vararg formatArgs: Any?): String {
        return resources.getString(resId, *formatArgs)
    }

    override fun provideColor(resId: Int): Int {
        return ResourcesCompat.getColor(resources, resId, null)
    }
}
