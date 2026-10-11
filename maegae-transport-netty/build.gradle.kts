plugins {
    `java-library`
}

dependencies {
    api(project(":maegae-core"))

    implementation(platform(libs.netty.bom))
    implementation(libs.netty.transport)
    implementation(libs.netty.codec.http)

    testImplementation(libs.archunit)
}

tasks.test {
    // Scan Netty main output only; test fixtures and Core classes are not subjects.
    systemProperty("maegae.netty.mainClasses", sourceSets.main.get().output.classesDirs.asPath)
}
