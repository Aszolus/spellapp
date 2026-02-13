pluginManagement {
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

rootProject.name = "spellapp"

include(":app")
include(":core-data")
include(":core-model")
include(":core-ui")
include(":core-rules")
include(":feature-spells")
include(":feature-character")
