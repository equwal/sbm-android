// AGP compiles the Kotlin code itself. The Kotlin plugin is here only to set
// the Kotlin version.
plugins {
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.21" apply false
}
