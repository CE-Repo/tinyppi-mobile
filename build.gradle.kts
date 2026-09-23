// Top-level build file where you can add configuration options common to all subprojects/modules.

buildscript {
    dependencies {
        // AGP 9 has built-in Kotlin support and ships with KGP 2.2.10. Declaring the
        // classpath here is the documented way to run a newer Kotlin Gradle Plugin.
        // It reads the same `kotlin` version the Kotlin plugins below are
        // declared with, so the compiler and those plugins cannot drift apart.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
