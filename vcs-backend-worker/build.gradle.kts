plugins {
    java
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.4"
}

group = "com.root"
version = "0.0.1-SNAPSHOT"
description = "vcs-backend-worker"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.github.java-diff-utils:java-diff-utils:4.12")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    // AWS SDK BOM keeps s3 + url-connection-client on the same version
    implementation(platform("software.amazon.awssdk:bom:2.32.26"))
    implementation("software.amazon.awssdk:s3")
    // Explicit sync HTTP client — lighter than Apache/Netty, required for native image
    implementation("software.amazon.awssdk:url-connection-client")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("vcs-backend-worker")
            buildArgs.addAll(
                // Fail the build if any class cannot be compiled to native — never fall back to JVM
                "--no-fallback",
                // Initialise SLF4J/Logback at build time to avoid runtime class-init issues
                "--initialize-at-build-time=org.slf4j,ch.qos.logback",
                "-H:+ReportExceptionStackTraces",
                // Statically link all C libraries (zlib, libgcc, libstdc++) into the binary.
                // Only glibc remains as a dynamic dependency, which distroless/base already provides.
                // This eliminates "cannot open shared object file: libz.so.1" errors at runtime.
                "-H:+StaticExecutableWithDynamicLibC",
                // Give the native-image compiler JVM more heap. The native-image process is
                // separate from the Gradle daemon, so org.gradle.jvmargs doesn't cover it.
                // 4 GB avoids GC pressure during the static analysis phase.
                "-J-Xmx4g"
            )
        }
    }
}

