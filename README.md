# MTG Deck to Oracle Text Converter

A lightweight Android utility designed to convert Magic: The Gathering (MTG) decklists into a single, comprehensive Oracle text file. This tool is optimized for preparing deck data for analysis by Large Language Models (LLMs).

## Features

-   **Seamless Integration**: Handles `ACTION_SEND` intents. Share a decklist (text or URL) from apps like **Moxfield** or **Mana Box** directly to this app.
-   **Robust Parsing**:
    -   Regex-based parsing for formats like `1 Sol Ring`, `4x Lightning Bolt`, or `1 Arcane Signet (CLB) 298`.
    -   Automatic extraction of card names and quantities.
    -   Basic URL scraping for deck names and contents.
-   **Scryfall API Integration**:
    -   Uses Scryfall's Batch API (`POST /cards/collection`) for efficient data fetching.
    -   Automatically handles lists longer than 75 cards by chunking requests.
-   **Local Caching (Room DB)**:
    -   Implements a **Cache-First** strategy.
    -   Oracle text is stored locally after the first fetch to save bandwidth and reduce latency.
-   **LLM-Ready Output**:
    -   Generates a structured `.txt` file with quantities, card names, and full Oracle text.
    -   Triggers a "Share" dialog immediately after generation so you can send the file to ChatGPT, Claude, or other LLM apps.

## How it Works

1.  **Share**: Find a deck in Moxfield/Mana Box and use the system "Share" feature. Choose this app.
2.  **Process**: The app parses the list, checks the local cache, and fetches any missing data from Scryfall.
3.  **Result**: A file named `[DeckName]_[Timestamp].txt` is created.
4.  **Forward**: The app opens a share dialog for the generated file. Pick your favorite LLM app to start your analysis.

## Tech Stack

-   **Language**: Kotlin
-   **Database**: Room (SQLite)
-   **Networking**: Retrofit + OkHttp
-   **UI**: ViewBinding + ViewModel + LiveData
-   **Parsing**: Regex + Jsoup
-   **API**: Scryfall REST API
