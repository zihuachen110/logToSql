plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.21"
    id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        val platformVersion = providers.gradleProperty("platformVersion").get()
        val platformType = providers.gradleProperty("platformType").get()
        when (platformType) {
            "IC" -> intellijIdeaCommunity(platformVersion)
            else -> intellijIdeaUltimate(platformVersion)
        }
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        bundledPlugin("com.intellij.java")
    }
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName").get()
        ideaVersion {
            sinceBuild = "242"
        }
        changeNotes = """
            <ul>
              <li>Initial release: merge MyBatis / Hibernate / generic JDBC logs into executable SQL</li>
            </ul>
        """.trimIndent()
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    test {
        useJUnitPlatform()
    }
}
