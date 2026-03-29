# MTG Deck to Oracle Text Converter

A lightweight, modern Android utility designed to convert Magic: The Gathering (MTG) decklists into a comprehensive Oracle text file. This tool is optimized for preparing deck data for analysis by Large Language Models (LLMs) like ChatGPT and Claude.

## ✨ Features

-   **Material 3 UI**: A beautiful, modern interface with smooth transitions and intuitive configuration.
-   **Seamless Integration**: Handles `ACTION_SEND` intents. Share a decklist (text or file) from apps like **Mana Box** or URLs from **Moxfield** directly to this app.
-   **Direct URL Import**: One-tap import for **Moxfield** and **MTGTop8** URLs with automatic data fetching via internal APIs.
-   **Robust Parsing**:
    -   Regex-based parsing for formats like `1 Sol Ring`, `4x Lightning Bolt`, or `1 Arcane Signet (CLB) 298`.
    -   Automatic extraction of card names and quantities.
    -   Intelligent **Sideboard** and **Maybeboard** detection.
-   **Scryfall API Integration**:
    -   Uses Scryfall's Batch API for efficient data fetching.
    -   Automatically handles large decklists by chunking requests.
-   **Local Caching (Room DB)**:
    -   Oracle text is stored locally after the first fetch to save bandwidth and reduce latency.
-   **LLM-Ready Output**:
    -   Generates a structured `.txt` file with your original list preserved and full Oracle text appended.
    -   Triggers a system "Share" dialog immediately after generation for instant delivery to your preferred LLM.

## 🚀 How it Works

1.  **Import**: Share a deck from Mana Box (as File/Text) or paste a Moxfield/MTGTop8 URL into the app.
2.  **Configure**: Edit the deck name, toggle timestamps, and choose which sections (Sideboard/Maybeboard) to include.
3.  **Process**: The app parses the list, fetches missing Oracle text from Scryfall, and compiles the result.
4.  **Analyze**: Share the generated file directly to ChatGPT or Claude for a deep dive analysis.

## 🛠️ Tech Stack

-   **Language**: Kotlin (Java 17)
-   **Database**: Room (SQLite)
-   **Networking**: Retrofit + OkHttp
-   **UI**: Material Design 3 + ViewBinding + ViewModel + LiveData
-   **Markdown**: Markwon for in-app documentation.
-   **API**: Scryfall & Moxfield APIs
