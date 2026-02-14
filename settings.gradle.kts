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
include(":core-rules")
include(":core-ui")
include(":feature-spells")
include(":feature-character")
