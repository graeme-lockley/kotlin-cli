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
    runtimeOnly("org.slf4j:slf4j-nop:1.7.36")

    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.jetbrains.kotlin:kotlin-test-junit5:2.0.21")
    implementation("org.junit.platform:junit-platform-launcher:1.13.4")
    implementation("org.junit.platform:junit-platform-engine:1.13.4")
    implementation("org.junit.platform:junit-platform-commons:1.13.4")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.13.4")
    implementation("org.opentest4j:opentest4j:1.3.0")
    implementation("org.apiguardian:apiguardian-api:1.1.2")

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

// Generate version.txt resource
tasks.register("generateVersionResource") {
    doLast {
        val resourceDir = layout.buildDirectory.dir("resources/main").get().asFile
        resourceDir.mkdirs()
        File(resourceDir, "version.txt").writeText(project.version.toString())
    }
}

// Ensure version resource is generated before compilation
tasks.compileKotlin {
    dependsOn("generateVersionResource")
}

// Fat JAR task for kli distribution
tasks.jar {
    archiveBaseName.set("kotlin-cli")
    archiveVersion.set("")
    archiveExtension.set("jar")
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes("Main-Class" to "kli.MainKt")
    }
    
    from(sourceSets.main.get().output)
    
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.exists() }
            .map { if (it.isDirectory) it else zipTree(it) }
    }) {
        exclude("META-INF/MANIFEST.MF")
        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/versions/**")
    }
}
