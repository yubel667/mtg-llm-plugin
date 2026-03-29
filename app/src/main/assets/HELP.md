# MTG Deck to Oracle - Help Guide

This utility converts Magic: The Gathering decklists into a comprehensive text file containing full Oracle text for every card. This format is optimized for analysis by LLMs (ChatGPT, Claude, etc.).

## 🚀 Workflows

### 1. Share from App (Recommended)
This is the fastest way to process a deck from **Mana Box** or other apps.
1.  Open your deck in your favorite MTG app.
2.  Tap **Share** and choose **File** or **Text**.
3.  Select **MTG Deck to Oracle** from the share menu.
4.  The app will open, analyze the list, and let you configure the output.

### 2. URL Import
Supported sites: **Moxfield**, **MTGTop8**.
1.  Copy the URL of a public deck from your browser.
2.  Open this app and tap the **Paste** button.
3.  Tap **Load Deck from URL**.
4.  The app will fetch the deck data directly from the site's API.

---

## 🛠️ Configuration Options

*   **Deck Name**: Pre-filled with the filename or the first card name (Commander). You can change this to anything you like.
*   **Append date/time**: Adds a unique timestamp to the filename so you don't overwrite previous versions.
*   **Sideboard**: Toggle this to include or exclude cards in the Sideboard section.
*   **Maybeboard**: Toggle this to include or exclude cards in the Maybeboard/Considering section.

---

## 🕒 History & Management

*   **Past Decks**: Tap the **Clock** icon in the toolbar to see your last processed decks. You can search by name, quickly re-share them, or save them to your local storage again.
*   **Options & Stats**: Tap the **Gear** icon to customize your experience:
    *   **Auto-share**: Enable or disable the automatic share menu.
    *   **History Limit**: Control how many past decks are kept (default is 100).
    *   **Cache Stats**: See how many cards are stored in your local database.
    *   **Cleanup**: Buttons to clear your card cache or wipe your history.

---

## 📄 Output Format
The app generates a single `.txt` file with:
1.  **Deck Info**: Name, card counts, and generation date.
2.  **Original List**: Your decklist exactly as it was imported.
3.  **Oracle Appendix**: Full Oracle text for every card, categorized by section (Main, Sideboard, etc.).

---

## 🤖 LLM Analysis
Once the file is generated:
1.  A system **Share** menu will appear automatically.
2.  Choose your preferred LLM app (e.g., ChatGPT).
3.  The text file will be attached.
4.  **Prompt Idea**: *"Analyze this decklist for power level, mana curve, and suggest 3 improvements based on the current meta."*

---

## 💡 Troubleshooting
*   **"Card not found"**: Ensure the card name is correct. We use the Scryfall API for data.
*   **Cache**: Card data is saved locally after the first fetch to make future imports instant.
