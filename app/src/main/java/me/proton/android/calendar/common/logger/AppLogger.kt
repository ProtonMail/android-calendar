package me.proton.android.calendar.common.logger

import me.proton.core.util.android.sentry.TimberLogger
import me.proton.core.util.kotlin.Logger

object AppLogger : Logger by TimberLogger
