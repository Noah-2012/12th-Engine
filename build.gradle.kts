plugins {
    id("java")
    application
    id("com.diffplug.spotless") version "6.25.0"
}

group = "net.twelfthengine"
version = "1.1.3"

// ------------------------------------------------------------
// Java toolchain
// ------------------------------------------------------------
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// ------------------------------------------------------------
// Versions
// ------------------------------------------------------------
val lwjglVersion = "3.4.1"
val lwjglNatives = "natives-windows"

// ------------------------------------------------------------
// Helpers
// ------------------------------------------------------------
fun lwjgl(module: String) = "org.lwjgl:$module:$lwjglVersion"
fun lwjglNative(module: String) = "org.lwjgl:$module:$lwjglVersion:$lwjglNatives"

// ------------------------------------------------------------
// Repositories
// ------------------------------------------------------------
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

// ------------------------------------------------------------
// SourceSets
// ------------------------------------------------------------
fun SourceSet.setup(name: String) {
    java.srcDirs("src/$name/java")
    resources.srcDirs("src/$name/resources")
}

sourceSets {
    main {
        setup("main")
    }

    create("api") {
        setup("api")
    }

    create("game") {
        setup("game")
    }

    named("api") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

    named("game") {
        compileClasspath += sourceSets.main.get().output
        compileClasspath += sourceSets["api"].output

        runtimeClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets["api"].output
    }
}

// ------------------------------------------------------------
// 🔥 CRITICAL FIX (THIS WAS MISSING)
// ------------------------------------------------------------

// THIS is what fixes:
// - GLFW missing
// - GL imports missing
// - ImGui missing in api/game

configurations {
    named("apiImplementation") {
        extendsFrom(configurations.implementation.get())
    }
    named("gameImplementation") {
        extendsFrom(configurations.implementation.get())
    }
}

// ------------------------------------------------------------
// Dependencies
// ------------------------------------------------------------
dependencies {

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.joml:joml:1.10.5")
    implementation("com.github.Vatuu:discord-rpc:1.6.2")
    implementation("org.luaj:luaj-jse:3.0.1")

    implementation("io.github.spair:imgui-java-binding:1.86.11")
    implementation("io.github.spair:imgui-java-lwjgl3:1.86.11")
    runtimeOnly("io.github.spair:imgui-java-natives-windows:1.86.11")

    val lwjglModules = listOf(
        "lwjgl",
        "lwjgl-glfw",
        "lwjgl-opengl",
        "lwjgl-stb"
    )

    lwjglModules.forEach { lib ->
        implementation(lwjgl(lib))
        runtimeOnly(lwjglNative(lib))
    }

    implementation("org.bytedeco:javacv:1.5.8")
    implementation("org.bytedeco:javacpp:1.5.8")
    implementation("org.bytedeco:javacpp:1.5.8:windows-x86_64")
    implementation("org.bytedeco:ffmpeg:5.1.2-1.5.8:windows-x86_64")
}

// ------------------------------------------------------------
// Application
// ------------------------------------------------------------
application {
    mainClass.set("com.mygame.MyGame")
}

// ------------------------------------------------------------
// Run configuration
// ------------------------------------------------------------
tasks.named<JavaExec>("run") {
    dependsOn("apiClasses", "gameClasses")

    classpath =
        sourceSets.main.get().runtimeClasspath +
                sourceSets["api"].output +
                sourceSets["game"].output

    mainClass.set("com.mygame.MyGame")
}

// Global JVM args
tasks.withType<JavaExec>().configureEach {
    jvmArgs("-Djava.library.path=./natives")
}

// ------------------------------------------------------------
// Tests
// ------------------------------------------------------------
tasks.test {
    useJUnitPlatform()
}

// ------------------------------------------------------------
// Resources
// ------------------------------------------------------------
tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

// ------------------------------------------------------------
// JAR
// ------------------------------------------------------------
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.mygame.MyGame"
    }
}

// ------------------------------------------------------------
// Fat JAR
// ------------------------------------------------------------
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("fat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.mygame.MyGame"
    }

    from(sourceSets.main.get().output)
    from(sourceSets["api"].output)
    from(sourceSets["game"].output)

    val runtimeJars = configurations.runtimeClasspath.get()
        .filter { it.name.endsWith("jar") }

    from(runtimeJars.map { zipTree(it) })
}

// ------------------------------------------------------------
// jlink task
// ------------------------------------------------------------
tasks.register<Exec>("jlinkBuild") {
    dependsOn("build", "cleanRuntime")

    val outputDir = file("build/runtime")
    val javaHome = File(System.getProperty("java.home"))

    commandLine(
        "${javaHome}/bin/jlink.exe",
        "--module-path", "${javaHome}/jmods",
        "--add-modules",
        "java.base,java.desktop,java.logging,java.xml,java.management,jdk.unsupported",
        "--output", outputDir.absolutePath,
        "--strip-debug",
        "--compress=2",
        "--no-header-files",
        "--no-man-pages"
    )
}

// ------------------------------------------------------------
// jpackage task
// ------------------------------------------------------------
tasks.register<Exec>("packageApp") {
    dependsOn("jlinkBuild", "fatJar")

    val runtimeDir = file("build/runtime")
    val javaHome = File(System.getProperty("java.home"))
    val jpackage = "${javaHome}/bin/jpackage.exe"

    val jarName = tasks.named<Jar>("fatJar").get().archiveFileName.get()

    doFirst {
        if (!File(jpackage).exists()) {
            throw GradleException("jpackage not found: $jpackage")
        }
    }

    commandLine(
        jpackage,
        "--type", "app-image",
        "--input", "build/libs",
        "--name", "TwelfthEngine",
        "--main-jar", jarName,
        "--main-class", "com.mygame.MyGame",
        "--runtime-image", runtimeDir.absolutePath,
        "--icon", "src/main/resources/app-icon.ico",
        "--java-options", "--enable-native-access=ALL-UNNAMED"
    )
}

// ------------------------------------------------------------
// Clean extra build artifacts
// ------------------------------------------------------------
tasks.register<Exec>("packageClean") {
    commandLine("rm", "-rf", "TwelfthEngine")
}

tasks.register("cleanRuntime") {
    doLast {
        file("build/runtime").deleteRecursively()
    }
}

// ------------------------------------------------------------
// Spotless
// ------------------------------------------------------------
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.17.0")
    }
}