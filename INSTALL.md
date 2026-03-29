# MTG Deck to Oracle Installation Guide

This guide describes how to build and install the **MTG Deck to Oracle** utility on your Android device.

## 📋 Prerequisites

1.  **Android Studio:** Latest version (Jellyfish or newer recommended).
2.  **JDK 17:** Ensure your Android Studio is configured to use JDK 17 for Gradle.
3.  **Internet Connection:** Required for dependencies and API data.
4.  **USB Cable:** For connecting your device to your computer.

## ⚙️ Step 1: Prepare your Device

1.  Enable **Developer Options** on your phone (Tap "Build number" 7 times in Settings > About phone).
2.  Go to **Settings > System > Developer options** and enable **USB debugging**.
3.  Connect your phone to your computer.

## 🏗️ Step 2: Build and Install

1.  Launch **Android Studio**.
2.  Select **Open** and choose the project directory.
3.  Wait for **Gradle Sync** to finish (it will download Room, Retrofit, Markwon, etc.).
4.  Select your device in the top toolbar.
5.  Click the **Run** button (green play icon).

## 🧪 How to Test

1.  Open **Moxfield** in your browser and copy a deck URL.
2.  Open **MTG Deck to Oracle** and tap **Paste**.
3.  Tap **Load Deck** and verify the configuration appears.
4.  Alternatively, open **Mana Box**, tap a deck, then **Share > File** and choose this app.
5.  Check the generated `.txt` file—it should contain your list followed by full Oracle texts!
