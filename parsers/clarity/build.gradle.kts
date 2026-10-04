plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":harness"))
    implementation("com.skadistats:clarity:5.0.0")
}

application {
    mainClass.set("spheenik.claritybench.parsers.ClarityParse")
}
