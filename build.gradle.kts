plugins {
    id("java")
    application
    id("com.diffplug.spotless") version "6.25.0"
}

group = "net.twelfthengine"
version = "1.2"
var lwjglVersion = "3.4.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-stb:$lwjglVersion")
    implementation("org.joml:joml:1.10.5")

    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-stb:${lwjglVersion}:natives-windows")
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("fat")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "net.twelfthengine.TwelfthEngine"
    }

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)

    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

tasks.register<Exec>("packageApp") {
    dependsOn("fatJar")

    commandLine(
        "\"C:/Program Files/Eclipse Adoptium/jdk-21.0.9.10-hotspot/bin/jpackage.exe\"",
        "--type", "app-image",
        "--input", "build/libs",
        "--main-jar", "12th-Engine-1.2-fat.jar",
        "--main-class", "net.twelfthengine.TwelfthEngine",
        "--name", "TwelfthEngine",
        "--java-options", "--enable-native-access=ALL-UNNAMED"
    )
}

tasks.register<Exec>("packageClean") {

    commandLine(
        "rm",
        "-rf",
        "TwelfthEngine"
    )
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "net.twelfthengine.TwelfthEngine"
        )
    }
}

spotless {
    java {
        googleJavaFormat()
    }
}

application {
    mainClass.set("net.twelfthengine.TwelfthEngine")

    // JVM-Argumente für das Runtime
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}