# GEMINI.md - MTG Deck to Oracle Text Converter

## Project Overview
This is an Android utility designed to convert Magic: The Gathering (MTG) decklists into comprehensive Oracle text files. Its primary goal is to provide LLMs (like ChatGPT or Claude) with full card context for deck analysis, power level assessment, and optimization suggestions.

### Key Features
-   **Multi-Source Import**: Supports Mana Box (text/file), Moxfield (URL), and MTGTop8 (URL).
-   **Deck Preview & Summary**: Displays a summary (card count) and raw text preview before conversion.
-   **Smart Commander Detection**: Automatically suggests "Game Changers" for decks with 90+ cards.
-   **Oracle Data Fetching**: Retrieves card data (Mana Cost, Type, Stats, Oracle Text) from the Scryfall API.
-   **Local Caching**: Stores card data in a Room database to reduce API calls and improve performance.
-   **History Management**: Maintains a searchable record of previously processed decks.
-   **Material 3 UI**: Modern Android interface with light/dark mode support.

## Tech Stack
-   **Language**: Kotlin (Java 17 target)
-   **UI Framework**: Android XML with Material Design 3 and ViewBinding.
-   **Architecture**: MVVM (Model-View-ViewModel) using `AndroidViewModel` and `LiveData`.
-   **Networking**: Retrofit 2 + OkHttp 4 for API calls; Jsoup 1.17 for HTML parsing.
-   **Database**: Room Persistence Library (SQLite).
-   **Concurrency**: Kotlin Coroutines.
-   **Markdown**: Markwon for rendering help and guides.

## Core Components
-   `MainActivity.kt`: Entry point, handles `ACTION_SEND` intents for sharing decklists into the app.
-   `DeckViewModel.kt`: Orchestrates parsing, API fetching, database caching, and file generation.
-   `utils/DeckParser.kt`: Centralized logic for parsing raw decklist strings and fetching data from URLs.
-   `api/`: Retrofit service interfaces for Scryfall and Moxfield.
-   `data/`: Room entities and DAOs for cards and deck history.

## Building and Running
### Build
To build the project from the command line:
```bash
./gradlew assembleDebug
```

### Testing
-   **Unit Tests**: Located in `app/src/test`. Run with:
    ```bash
    ./gradlew test
    ```
-   **Instrumentation Tests**: Located in `app/src/androidTest`. Run with:
    ```bash
    ./gradlew connectedAndroidTest
    ```

### Run
Install the debug APK to a connected device or emulator:
```bash
./gradlew installDebug
```

## Development Conventions
-   **MVVM**: Keep business logic out of Activities. Use `DeckViewModel` to manage state via `LiveData`.
-   **Asynchronous Work**: Use `viewModelScope.launch` with appropriate dispatchers (`Dispatchers.IO` for DB/Network).
-   **ViewBinding**: Use `ActivityMainBinding.inflate(layoutInflater)` in activities instead of `findViewById`.
-   **Resources**: Define strings in `res/values/strings.xml` and colors/themes in `res/values/themes.xml`.
-   **Parsing**: Any new decklist format should be integrated into `DeckParser.kt`.
-   **Mocking**: Use `MockK` for unit tests (see `DeckViewModelTest.kt`).

## API Usage
-   **Scryfall**: Used for fetching card collections (`/cards/collection`) and searching for "Game Changers".
-   **Moxfield**: Used for fetching deck JSON data via their private API.
-   **MTGTop8**: Parsed using Jsoup by fetching the MTGO export format.
