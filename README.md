# MTG Deck to Oracle Text Converter

A lightweight, modern Android utility designed to convert Magic: The Gathering (MTG) decklists into a comprehensive Oracle text file. This tool is optimized for preparing deck data for analysis by Large Language Models (LLMs) like ChatGPT and Claude.

## ⚠️ Supported Formats & Disclaimer

This app **ONLY** supports the following sources:
1.  **Mana Box**: Exported deck files (`.txt`) or shared text content.
2.  **Moxfield**: Public deck URLs.
3.  **MTGTop8**: Deck/Event URLs.

> **Disclaimer**: Because this tool relies on parsing third-party exports and internal APIs, it may fail to correctly process decklists if these platforms update their formats or layouts. Use the in-app **Preview** feature to verify results before sharing.

## ✨ Features

-   **Material 3 UI**: A beautiful, modern interface with high-contrast support for Light and Dark modes.
-   **Direct URL Import**: One-tap import for **Moxfield** and **MTGTop8** URLs with automatic data fetching via APIs.
-   **Robust Parsing**:
    -   Intelligent **Sideboard**, **Maybeboard**, and **Commander** detection.
    -   Automatic normalization of double-sided card names (DFC/Adventure).
-   **Rich Oracle Data**: Every card includes Mana Cost, Type, Stats (P/T), and full Oracle text.
-   **History Management**: Keeps a searchable record of your last 100 decks for quick re-sharing.
-   **Local Export**: Save your generated Oracle files to any folder on your device.

## 🚀 How it Works

1.  **Import**: Share a deck from Mana Box (as File/Text) or paste a Moxfield/MTGTop8 URL into the app.
2.  **Configure**: Edit the deck name, toggle timestamps, and choose which sections to include.
3.  **Process**: The app fetches Oracle data from Scryfall and compiles the result.
4.  **Analyze**: Share the generated file directly to your preferred LLM app.

## 🛠️ Tech Stack

-   **Language**: Kotlin (Java 17)
-   **Database**: Room (SQLite)
-   **Networking**: Retrofit + OkHttp
-   **UI**: Material Design 3
-   **API**: Scryfall, Moxfield, and MTGTop8
