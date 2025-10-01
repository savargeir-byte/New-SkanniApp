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
    // Ensure plugins resolve even if Gradle Plugin Portal is flaky or blocked
    resolutionStrategy {
        eachPlugin {
            val pid = requested.id.id
            when {
                pid == "com.android.application" -> useModule("com.android.tools.build:gradle:8.5.2")
                pid.startsWith("org.jetbrains.kotlin") -> useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Appið"
include(":app")
