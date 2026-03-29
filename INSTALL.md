# MTG Deck to Oracle Installation Guide

This guide describes how to build and install the **MTG Deck to Oracle** utility on your Android device.

## Prerequisites

1.  **Android Studio:** [Download and install](https://developer.android.com/studio) the latest version.
2.  **Internet Connection:** Required for downloading Gradle dependencies and fetching card data from Scryfall.
3.  **USB Cable:** To connect your Pixel to your computer.

## Step 1: Prepare your Android Device

1.  Open **Settings** on your phone.
2.  Go to **About phone**.
3.  Scroll to the bottom and tap **Build number** 7 times until you see "You are now a developer!".
4.  Go back to **Settings > System > Developer options**.
5.  Enable **USB debugging**.

## Step 2: Open and Configure the Project

1.  Launch **Android Studio**.
2.  Select **Open** and choose the `/Users/yiwei/code/mtg-llm-plugin` directory.
3.  Wait for Gradle to sync. This may take a few minutes as it downloads Room, Retrofit, and other dependencies.

## Step 3: Build and Install

1.  Connect your phone to your computer via USB.
2.  In the toolbar, select your device from the target device dropdown.
3.  Click the **Run** button (green play icon).
4.  The app will build and install on your phone.

## How to Test

1.  Open **Moxfield** or **Mana Box** on your phone.
2.  Select a deck and tap **Share** (usually as text or via the system share menu).
3.  Select **MTG Deck to Oracle** from the list of apps.
4.  Watch the progress bar. Once finished, a new share menu will appear allowing you to send the generated `.txt` file to your LLM of choice.
