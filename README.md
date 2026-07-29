# COMP1786 Calculator

A native Android calculator app built with Java (Views + XML), with no external
math libraries.

## Features

- Basic operations: addition, subtraction, multiplication, division
- Chained calculations, e.g. `6 + 3 + 1 =` → `10`
- `AC` (clear), `+/-` (toggle sign), `%` (percent), `.` (decimal point), `=` (evaluate)
- Repeat-equals (iOS-style): pressing `=` again re-applies the last operation,
  e.g. `5 + 5 =` → `10`, `=` → `15`, `=` → `20`
- Expression line above the result, with the active operator highlighted
  (Google Calculator style)
- Division by zero and overflow are shown as `Error` without crashing the app

## Project structure

```
app/src/main/
├── java/com/example/caculator/
│   └── MainActivity.java       # UI wiring + calculator logic
├── res/
│   ├── layout/activity_main.xml
│   ├── values/{colors,strings,dimens,themes}.xml
│   └── drawable/{bg_circle_number,bg_circle_red,bg_pill_red}.xml
└── AndroidManifest.xml
```

## Requirements

- Android Studio (JDK 17+)
- Android SDK: minSdk 24, targetSdk / compileSdk 36

## Build & run

```
./gradlew assembleDebug        # build a debug APK
./gradlew installDebug         # install to a connected device/emulator
```

Or open the project in Android Studio and run the `app` configuration.

## Tests

```
./gradlew test                 # local unit tests
./gradlew connectedAndroidTest # instrumented tests (device/emulator required)
```
