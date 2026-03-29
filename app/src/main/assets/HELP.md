# MTG Deck to Oracle - Help Guide

This utility converts Magic: The Gathering decklists into a comprehensive text file containing full Oracle text for every card. This format is optimized for analysis by LLMs (ChatGPT, Claude, etc.).

## ⚠️ Supported Sources & Disclaimer

This app is designed specifically for:
1.  **Mana Box**: Exported deck files (`.txt`) or shared text.
2.  **Moxfield**: Public deck URLs.
3.  **MTGTop8**: Deck/Event URLs.

> **Note**: Other websites or apps are **not** officially supported. Since we rely on third-party formats, updates to those platforms may occasionally break our parser.

## 🚀 Workflows

### 1. Share from Mana Box (Best)
1.  Open your deck in **Mana Box**.
2.  Tap the menu (three dots) -> **Share**.
3.  Choose **File** (recommended) or **Text**.
4.  Select **MTG Deck to Oracle** from the share menu.

### 2. URL Import (Moxfield / MTGTop8)
1.  Copy the URL of a deck from your browser.
2.  Open this app and tap the **Paste** button.
3.  Tap **Load URL**.

---

## 🛠️ Configuration Options

*   **Deck Name**: Pre-filled automatically. You can edit this.
*   **Sideboard/Maybeboard**: Choose whether to include these sections in the final Oracle dump.
*   **Auto-share**: Can be enabled in the **Options** (Gear icon) to skip the manual share step on success.

---

## 🤖 LLM Analysis
Once the file is generated and shared to your LLM:
*   **Prompt Idea**: *"Analyze this decklist for power level, mana curve, and suggest 3 improvements based on the current meta."*

---

## 💡 Troubleshooting
*   **"Card not found"**: Ensure the card name is correct.
*   **Partial Success**: If some cards fail, the app will list them and allow you to share the partial result manually.
