plugins {
    `java-library`
}

dependencies {
    implementation(project(":maegae-core"))
    testImplementation(libs.archunit)
}

val processorJar = tasks.jar

tasks.test {
    dependsOn(processorJar)
    systemProperty("maegae.processor.jar", processorJar.get().archiveFile.get().asFile.absolutePath)
    // Scan only Processor production output, not test fixtures or Core classes.
    systemProperty("maegae.processor.mainClasses", sourceSets.main.get().output.classesDirs.asPath)
}
