rootProject.name = "ProtonCalendar"

plugins {
    id("me.proton.core.gradle-plugins.include-core-build") version "1.3.0"
}

includeCoreBuild {
    branch.set("main")
    includeBuild("gopenpgp")
}

include(":app")
include(":week-view-core")
include(":shared-test-code")
