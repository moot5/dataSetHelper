plugins {
    id("com.android.application")
}

android {
    setCompileSdkVersion(rootProject.property("compileSdkVersion") as Int)
    defaultConfig {
        applicationId = "com.otaliastudios.cameraview.demo"
        setMinSdkVersion(24)
        setTargetSdkVersion(24)
        versionCode = 1
        versionName = "1.0"
        vectorDrawables.useSupportLibrary = true
    }
}

dependencies {
    implementation(project(":cameraview"))
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("com.google.android.material:material:1.1.0")
    implementation ("com.arthenica:mobile-ffmpeg-full:4.4")
    //implementation("org.apache.sanselan:sanselan:0.97-incubator")
    implementation("org.apache.commons:commons-imaging:1.0-alpha1")
    implementation("com.google.guava:guava:27.1-android")
}
