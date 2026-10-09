rootProject.name = "maegae"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include(
    "maegae-core",
    "maegae-processor",
    "maegae-transport-netty",
    "maegae-example",
)
