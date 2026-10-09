plugins {
    java
}

dependencies {
    implementation(project(":maegae-core"))
    implementation(project(":maegae-transport-netty"))
    annotationProcessor(project(":maegae-processor"))
}
