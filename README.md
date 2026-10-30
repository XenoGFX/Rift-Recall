# Rift Recall

An Android app (Kotlin + Jetpack Compose) for memorising Riftbound cards. It shows you **only the artwork** and asks you to recall the details.

## Build and run

1. Install Android Studio (Ladybug or newer) and open this folder. It will create the Gradle wrapper and download the Android SDK pieces it needs.
2. Run on a device or emulator (Android 8.0 / API 26 or newer). Needs internet on first launch to download the card list (about 800 cards via the open Riftcodex API, then cached for offline use). Art is streamed and cached by the image loader.
3. **Open Settings > Art crop on first run** and drag the sliders until the name, cost and rules text sit outside the gold outline. The default crop is an estimate.

Unit tests for the learning logic: `./gradlew :app:testDebugUnitTest`.

## How it helps you learn

- **Learn, then recall.** A brand-new card is shown in full first, then quizzed a few cards later in the same session.
- **Spaced repetition.** Every card has a separate schedule per thing you can be asked (name, ability, cost, stats, type & domain). Correct answers push it further out (10 min, 1 d, 3 d, 7 d, 16 d, 35 d); a miss brings it back soon.
- **Retry misses in-session.** Missed cards return a few cards later until you get them right.
- **Active recall, not recognition.** Names, abilities and numbers are typed; nothing is multiple choice except type & domain.
- **Forgiving grading.** Small typos in names are accepted. Abilities are graded on key words (reminder text in brackets is ignored). Anything the grader gets wrong you can override with "I actually knew this".
- **Hints that cost you.** Two hint levels per question; using one means the card doesn't advance a box.
- **Modes:** Name, Ability, Energy cost, Might & Power, Type & Domain, and Full card (every field in sequence).
- **Chunking.** New cards come in set / collector-number order, and you can restrict to one or more sets on the home screen.
- **Flashcards** (art -> reveal), a searchable **Collection** with a mastery bar per card, and a per-session summary of what you missed.

## Data and credits

Card data comes from the community Riftcodex API. Card art is loaded from the official image host at runtime and is not bundled in the app. This is an unofficial fan project, not affiliated with Riot Games.
