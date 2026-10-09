import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    base
}

subprojects {
    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
            }
        }

        dependencies {
            add("testImplementation", platform(libs.junit.bom))
            add("testImplementation", libs.junit.jupiter)
            add("testRuntimeOnly", libs.junit.platform.launcher)
        }

        tasks.withType<JavaCompile>().configureEach {
            options.release.set(25)
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}
