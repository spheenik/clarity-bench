plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":harness"))
    implementation("com.skadistats:clarity:4.0.1")
}

application {
    mainClass.set("spheenik.claritybench.BenchMain")
}

tasks.withType<JavaExec>().configureEach {
    standardInput = System.`in`
}
