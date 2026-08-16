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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "plugin-example-build"
include(":plugin-example")

// The SDK is consumed as a published artifact. Point this at a local checkout to work on both at
// once: -Pviper.pluginSdk.dir=../plugin-sdk
providers.gradleProperty("viper.pluginSdk.dir").orNull?.let { dir ->
    includeBuild(dir) {
        dependencySubstitution {
            substitute(module("io.github.viperplayer:plugin-sdk")).using(project(":plugin-sdk"))
        }
    }
}
