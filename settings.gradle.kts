pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "readout"

// Modules will be included here as they're scaffolded, step by step.
include(":app")
include(":core:common")
include(":core:audio")
include(":core:screen")
include(":core:llm")
include(":core:wake")
include(":core:session")
include(":feature:onboarding")
// include(":feature:settings")
