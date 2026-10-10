plugins {
    `java-library`
}

dependencies {
    implementation(project(":maegae-core"))
    testImplementation(libs.archunit)
}

tasks.test {
    // Scan only Processor production output, not test fixtures or Core classes.
    systemProperty("maegae.processor.mainClasses", sourceSets.main.get().output.classesDirs.asPath)
}
