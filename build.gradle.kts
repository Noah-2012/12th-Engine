plugins {
    id("java")
    application
    id("com.diffplug.spotless") version "6.25.0"
}

group = "net.twelfthengine"
version = "1.1.3"
var lwjglVersion = "3.4.1"
var lwjglNatives = "natives-windows"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

// ------------------------------------------------------------------
// Source sets
// ------------------------------------------------------------------
sourceSets {
    // Engine internals  →  src/main/java
    main {
        java.srcDirs("src/main/java")
        resources.srcDirs("src/main/resources")
    }

    // Public API  →  src/api/java
    create("api") {
        java.srcDirs("src/api/java")
        resources.srcDirs("src/api/resources")
        // api can see engine classes (TwelfthApp references World, RenderContext, etc.)
        compileClasspath += sourceSets.main.get().output
    }

    // Your game  →  src/game/java
    create("game") {
        java.srcDirs("src/game/java")
        resources.srcDirs("src/game/resources")
        // game can see both engine + api
        compileClasspath += sourceSets.main.get().output
        compileClasspath += sourceSets["api"].output
        compileClasspath += sourceSets["api"].compileClasspath
    }
}

// ------------------------------------------------------------------
// Dependencies (shared across all source sets)
// ------------------------------------------------------------------
val lwjglDeps = listOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-stb")

dependencies {
    // --- Tests ---
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // --- LWJGL ---
    lwjglDeps.forEach { lib ->
        implementation("org.lwjgl:$lib:$lwjglVersion")
        runtimeOnly("org.lwjgl:$lib:$lwjglVersion:$lwjglNatives")
    }

    // --- Other engine deps ---
    implementation("org.joml:joml:1.10.5")
    implementation("com.github.Vatuu:discord-rpc:1.6.2")

    // javacv — windows only, no cross-platform FFmpeg bloat
    implementation("org.bytedeco:javacv:1.5.8")
    implementation("org.bytedeco:javacpp:1.5.8")
    implementation("org.bytedeco:javacpp:1.5.8:windows-x86_64")
    implementation("org.bytedeco:ffmpeg:5.1.2-1.5.8:windows-x86_64")

    // --- api sourceSet needs the same deps as main ---
    "apiImplementation"("org.joml:joml:1.10.5")
    lwjglDeps.forEach { lib ->
        "apiImplementation"("org.lwjgl:$lib:$lwjglVersion")
    }

    // --- game sourceSet ---
    "gameImplementation"("org.joml:joml:1.10.5")
    lwjglDeps.forEach { lib ->
        "gameImplementation"("org.lwjgl:$lib:$lwjglVersion")
        "gameRuntimeOnly"("org.lwjgl:$lib:$lwjglVersion:$lwjglNatives")
    }
    "gameImplementation"("org.bytedeco:javacv:1.5.8")
    "gameImplementation"("org.bytedeco:javacpp:1.5.8")
    "gameImplementation"("org.bytedeco:javacpp:1.5.8:windows-x86_64")
    "gameImplementation"("org.bytedeco:ffmpeg:5.1.2-1.5.8:windows-x86_64")
    "gameImplementation"("com.github.Vatuu:discord-rpc:1.6.2")
}

// ------------------------------------------------------------------
// Application entry point  →  your game's main()
// ------------------------------------------------------------------
application {
    mainClass.set("com.mygame.MyGame")
    applicationDefaultJvmArgs = listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.library.path=./natives"
    )
}

// ------------------------------------------------------------------
// run task classpath: engine + api + game
// ------------------------------------------------------------------
tasks.named<JavaExec>("run") {
    dependsOn("apiClasses", "gameClasses")
    classpath = sourceSets.main.get().runtimeClasspath +
                sourceSets["api"].output +
                sourceSets["game"].output
    mainClass.set("com.mygame.MyGame")
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.library.path=./natives"
    )
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec> {
    jvmArgs("-Djava.library.path=./natives")
}

tasks.named<ProcessResources>("processResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ------------------------------------------------------------------
// Fat JAR — bundles engine + api + game into one runnable jar
// ------------------------------------------------------------------
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("fat")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.mygame.MyGame"
    }

    // All three source set class outputs
    from(sourceSets.main.get().output.classesDirs)
    from(sourceSets["api"].output.classesDirs)
    from(sourceSets["game"].output.classesDirs)

    // Resources from all three source sets — this is what was missing
    from(sourceSets.main.get().output.resourcesDir)
    from(sourceSets["api"].output.resourcesDir)
    from(sourceSets["game"].output.resourcesDir)

    // All runtime deps
    val allRuntime = configurations.runtimeClasspath.get() +
                     configurations["gameRuntimeClasspath"] +
                     configurations["apiRuntimeClasspath"]

    dependsOn(allRuntime)
    from(allRuntime.filter { it.name.endsWith("jar") }.map { zipTree(it) })
}

// ------------------------------------------------------------------
// jpackage
// ------------------------------------------------------------------
tasks.register<Exec>("packageApp") {
    dependsOn("fatJar")
    val javaHome = System.getProperty("java.home")
    val jpackage = if (System.getProperty("os.name").lowercase().contains("win"))
        "$javaHome/bin/jpackage.exe"
    else
        "$javaHome/bin/jpackage"

    doFirst {
        if (!file(jpackage).exists()) {
            throw GradleException("jpackage not found at $jpackage — make sure you are running Gradle with a full JDK, not just a JRE.")
        }
    }

    commandLine(
        jpackage,
        "--type", "app-image",
        "--input", "build/libs",
        "--main-jar", "12th-Engine-1.1.3-fat.jar",
        "--main-class", "com.mygame.MyGame",
        "--name", "TwelfthEngine",
        "--icon", "src/main/resources/app-icon.ico",
        "--java-options", "--enable-native-access=ALL-UNNAMED",
        "--java-options", "-XX:+UseCompressedOops",
        "--jlink-options", "--strip-debug",
        "--jlink-options", "--no-header-files",
        "--jlink-options", "--no-man-pages",
        "--jlink-options", "--compress=2"
    )
}

tasks.register<Exec>("packageClean") {
    commandLine("rm", "-rf", "TwelfthEngine")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "com.mygame.MyGame")
    }
}

// ------------------------------------------------------------------
// Spotless — format all three source trees
// ------------------------------------------------------------------
spotless {
    java {
        targetExclude("build/**")
        googleJavaFormat()
    }
}
