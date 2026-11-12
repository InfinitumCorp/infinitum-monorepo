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

rootProject.name = "throne-os"

include(
    ":libs:throne-core",
    ":libs:throne-ui",
    ":libs:nether-core",
    ":libs:nether-ui",
    ":apps:throne-launcher",
    ":apps:throne-guard",
    ":apps:throne-vault-notes",
    ":apps:throne-suite",
    ":apps:nether",
    ":apps:eidetic-cam",
    ":apps:hrvst-cam",
)
