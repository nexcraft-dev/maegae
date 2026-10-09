plugins {
    `java-library`
}

dependencies {
    api(project(":maegae-core"))

    implementation(platform(libs.netty.bom))
    implementation(libs.netty.transport)
    implementation(libs.netty.codec.http)
}
