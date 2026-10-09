plugins {
    `java-library`
}

dependencies {
    implementation(libs.jackson.core)
    implementation(libs.slf4j.api)
    testImplementation(libs.archunit)
}

tasks.test {
    // Restrict architecture scans to Core production output, including future classes.
    systemProperty("maegae.core.mainClasses", sourceSets.main.get().output.classesDirs.asPath)
}
