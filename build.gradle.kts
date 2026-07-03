plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "io.kli"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.github.ajalt.clikt:clikt:5.0.1")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21")

    implementation("org.apache.maven:maven-resolver-provider:3.9.9")
    implementation("org.apache.maven.resolver:maven-resolver-connector-basic:1.9.22")
    implementation("org.apache.maven.resolver:maven-resolver-transport-http:1.9.22")

    implementation("com.google.code.gson:gson:2.13.1")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "kli.MainKt"
}

tasks.test {
    useJUnitPlatform()
}
