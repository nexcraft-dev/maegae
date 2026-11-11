import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    java
}

dependencies {
    annotationProcessor(project(":maegae-processor"))
    implementation(project(":maegae-core"))
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

/**
 * Builds the smoke app as a native image and compares its output with the JVM output.
 * The task is not part of build or check. It needs GraalVM: set -PgraalvmHome=<dir>, GRAALVM_HOME or JAVA_HOME.
 * The module has no hand-written native-image configuration on purpose.
 */
abstract class NativeSmokeTest : DefaultTask() {
    @get:Inject
    abstract val exec: ExecOperations

    @get:InputFiles
    abstract val classpath: ConfigurableFileCollection

    @get:Input
    @get:Optional
    abstract val graalvmHome: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val home = listOfNotNull(graalvmHome.orNull, System.getenv("GRAALVM_HOME"), System.getenv("JAVA_HOME"))
            .map { File(it) }
            .firstOrNull { File(it, "bin/native-image").canExecute() }
            ?: throw GradleException(
                "native-image not found. Set -PgraalvmHome=<GraalVM JDK 25 dir>, GRAALVM_HOME or JAVA_HOME " +
                    "to a GraalVM installation that contains bin/native-image.")
        val nativeImage = File(home, "bin/native-image").absolutePath
        val java = File(home, "bin/java").absolutePath
        val cp = classpath.asPath
        val out = outputDir.get().asFile.also { it.deleteRecursively(); it.mkdirs() }

        fun capture(vararg command: String): Pair<Int, String> {
            val stdout = ByteArrayOutputStream()
            val result = exec.exec {
                commandLine(*command)
                standardOutput = stdout
                isIgnoreExitValue = true
            }
            return result.exitValue to stdout.toString(Charsets.UTF_8)
        }

        val (jvmExit, jvmOut) = capture(java, "-cp", cp, "smoke.SmokeMain")
        File(out, "jvm.out").writeText(jvmOut)

        val flags = mutableListOf("--no-fallback")
        val help = capture(nativeImage, "--help").second
        if (help.contains("--exact-reachability-metadata")) {
            flags += "--exact-reachability-metadata"
        } else {
            logger.lifecycle("--exact-reachability-metadata is not supported by this native-image.")
        }
        val executable = File(out, "smoke-native")
        val started = System.nanoTime()
        val build = exec.exec {
            commandLine(listOf(nativeImage) + flags + listOf("-cp", cp, "smoke.SmokeMain", "-o", executable.absolutePath))
            isIgnoreExitValue = true
        }
        val seconds = (System.nanoTime() - started) / 1_000_000_000
        if (build.exitValue != 0) {
            throw GradleException("native-image failed with exit code ${build.exitValue}.")
        }
        logger.lifecycle("native-image flags: $flags; build time: ${seconds}s; size: ${executable.length()} bytes")

        val (nativeExit, nativeOut) = capture(executable.absolutePath)
        File(out, "native.out").writeText(nativeOut)

        if (jvmExit != 0 || nativeExit != 0 || jvmOut != nativeOut) {
            throw GradleException(
                "Native smoke test failed. JVM exit=$jvmExit, native exit=$nativeExit, " +
                    "outputs ${if (jvmOut == nativeOut) "equal" else "differ"}.\n" +
                    "--- JVM output ---\n$jvmOut--- native output ---\n$nativeOut")
        }
        logger.lifecycle("Native smoke test passed. JVM and native outputs are identical.")
    }
}

tasks.register<NativeSmokeTest>("nativeSmokeTest") {
    group = "verification"
    description = "Builds the smoke app as a GraalVM native image and compares its output with the JVM output."
    classpath.from(sourceSets.main.get().runtimeClasspath)
    graalvmHome.set(providers.gradleProperty("graalvmHome"))
    outputDir.set(layout.buildDirectory.dir("native-smoke"))
}
