package me.proton.android.calendar.uitest.rule

import freemarker.template.utility.DateUtil.UTC
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.TimeZone


class TimeZoneRule(private val timeZoneId: TimeZone = UTC) : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() =
                TimeZone
                    .getDefault()
                    .let {
                        try {
                            TimeZone.setDefault(timeZoneId)
                            base.evaluate()
                        } finally {
                            TimeZone.setDefault(it)
                        }
                    }
        }
    }
}