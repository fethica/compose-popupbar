# compose-popupbar

An [LNPopupController](https://github.com/LeoNatan/LNPopupController)-style popup bar for Jetpack Compose: a persistent popup bar docked above a bottom bar, with an interactive, interruptible transition to full-screen popup content. Package `com.fethica.popupbar`, published as `com.github.fethica.compose-popupbar:popupbar`.

During development, consume it as a composite build from a sibling checkout:

```kotlin
// settings.gradle.kts
if (file("../compose-popupbar").exists()) includeBuild("../compose-popupbar")
```

```kotlin
// app/build.gradle.kts
implementation("com.github.fethica.compose-popupbar:popupbar:0.1.0")
```

Gradle substitutes the coordinate with the included project, so app code is already written against the published artifact.
