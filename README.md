# Vroom Buddies

A cartoon racing game for young kids. It's a native Android app in Kotlin, rendered with
[Filament](https://github.com/google/filament), a rendering library rather than a game engine.

Flow: title, then car picker, then paint picker, then 20 themed tracks, then the race, then the win screen.

## Build

```
./gradlew :app:assembleRelease      # app/build/outputs/apk/release/app-release.apk (debug-signed)
./gradlew :app:testDebugUnitTest    # physics/race simulation + mesh winding tests (JVM, no device)
```

You'll need JDK 17 and an Android SDK with platform `android-37.0`. Shaders compile on the phone at first launch
with filamat, and are cached in the app's files dir.

## Rules
- All cars and characters are original. Don't use or imitate any film, toy or game franchise.
- Keep controls kid-simple: left, right and BOOST. The car drives itself forward and the steering assistant keeps it on the road.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the code map and how to add tracks, cars, props and effects.
