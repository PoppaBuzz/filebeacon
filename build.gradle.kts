plugins {
    alias(libs.plugins.androidApplication) apply false
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}

tasks.register("projectInfo") {
    group = "help"
    description = "Displays project information"

    doLast {
        println("=".repeat(60))
        println("FileBeacon - Project Information")
        println("=".repeat(60))
        println("Project Name: ${rootProject.name}")
        println("Gradle Version: ${gradle.gradleVersion}")
        println("Android Gradle Plugin: 9.2.1")
        println("Java Compatibility: VERSION_21")
        println("=".repeat(60))
    }
}

tasks.named("wrapper", Wrapper::class.java) {
    gradleVersion = "9.6.1"
    distributionType = Wrapper.DistributionType.ALL
}