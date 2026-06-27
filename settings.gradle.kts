pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "KomRD"

include(":app")
include(":core:model")
include(":core:common")
include(":core:network")
include(":core:datastore")
include(":core:database")
include(":core:cache")
include(":core:data")
include(":core:designsystem")
include(":core:prefetch")
include(":core:sync")
include(":feature:server")
include(":feature:library")
include(":feature:home")
include(":feature:reader")
include(":feature:reader-epub")
include(":feature:settings")
