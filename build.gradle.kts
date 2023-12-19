import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/*
 * This file was generated by the Gradle 'init' task.
 */

plugins {
    `java-library`
    `maven-publish`
    kotlin("jvm")
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repository.jboss.org/nexus/content/groups/public/")

        // isAllowInsecureProtocol = true
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
    mavenCentral()
}

dependencies {
    api(libs.org.jboss.netty.netty)
    runtimeOnly(libs.hsqldb.hsqldb)
    testImplementation(libs.junit.junit)
    implementation(kotlin("stdlib"))
}

group = "net.sf.opensmus"
version = "2.0.0-SNAPSHOT"
description = "OpenSMUS server"
//java.sourceCompatibility = JavaVersion.VERSION_1_5
java.sourceCompatibility = JavaVersion.VERSION_1_8

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}