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
        // Só pelo NewPipeExtractor, que não publica no Maven Central. É a única
        // dependência daqui, e a versão dela é fixa: o JitPack constrói a partir de
        // uma tag do GitHub, e um repositório aberto a mais é superfície a mais.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "Alvorada"
include(":app")
