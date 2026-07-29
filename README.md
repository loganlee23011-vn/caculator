# COMP1786 Calculator

A native Android calculator built with Java and XML Views. Supports the four basic
arithmetic operations, percentage, sign toggle, decimal input, and an iOS-style
repeat-equals behavior, with input validation and safe handling of division by zero.

## Features

- Addition, subtraction, multiplication, and division on two operands
- Chained operations, e.g. `6 + 3 + 1 =` → `10`
- `AC` (clear), `+/-` (toggle sign), `%` (percent), `.` (decimal point), `=` (evaluate)
- Repeat-equals: pressing `=` again re-applies the last operation (`5 + 5 =` → `10`, `=` → `15`, ...)
- An expression line above the result showing the calculation in progress, with the
  active operator highlighted (Google Calculator style)
- Input validation: single decimal point per number, leading-zero handling, digit limit
- Division by zero and overflow are handled safely and shown as `Error`, without crashing
- Result formatting: integers display without a trailing `.0`; long decimals are
  trimmed; very large/small magnitudes fall back to scientific notation
- All colors, strings, dimensions, and shapes come from resources — no hardcoded
  values in the layout

## Project structure

```
app/src/main/
├── java/com/example/caculator/
│   └── MainActivity.java       # UI wiring + calculator state machine
├── res/
│   ├── layout/activity_main.xml
│   ├── values/{colors,strings,dimens,themes}.xml
│   └── drawable/{bg_circle_number,bg_circle_red,bg_pill_red}.xml
└── AndroidManifest.xml
```

## Architecture

The calculator is implemented as a small state machine inside `MainActivity`:

| Field | Purpose |
|---|---|
| `currentInput` | String currently shown on the display |
| `operandOne` | First operand already committed |
| `pendingOp` | Operator waiting to be applied (`NONE`, `ADD`, `SUB`, `MUL`, `DIV`) |
| `startNewNumber` | Whether the next digit press should start a new number |
| `hasError` | Whether the calculator is in the `Error` state |

Each button press is handled by its own method so the logic stays easy to follow and test:

```
onDigit(d)        onDecimalPoint()   onOperator(op)
onEquals()         onClear()          onToggleSign()   onPercent()
calculate(a, b, op)                  formatResult(value)   updateDisplay()
```

`calculate()` throws `ArithmeticException` on division by zero, which the caller
catches and turns into the `Error` display state — the app never crashes on bad input.

## Requirements

- Android Studio (with JDK 17+)
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
