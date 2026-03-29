# Android Hello World Installation Guide

This guide describes how to build and install this "Hello World" app on your Pixel phone (or any Android device).

## Prerequisites

1.  **Android Studio:** [Download and install](https://developer.android.com/studio) the latest version. This will install the Android SDK and Gradle.
2.  **USB Cable:** To connect your Pixel to your computer.

## Step 1: Prepare your Pixel Phone

1.  Open **Settings** on your Pixel.
2.  Go to **About phone**.
3.  Scroll to the bottom and tap **Build number** 7 times until you see "You are now a developer!".
4.  Go back to **Settings > System > Developer options**.
5.  Enable **USB debugging**.

## Step 2: Open and Configure the Project

1.  Launch **Android Studio**.
2.  Select **Open** and choose the `/Users/yiwei/code/mtg-llm-plugin` directory.
3.  Wait for Gradle to sync. If prompted to install missing SDK platforms or build tools, click the links to do so.
4.  If `local.properties` is missing, Android Studio should create it for you automatically. If not, create a file named `local.properties` in the root directory and add:
    ```properties
    sdk.dir=/Users/YOUR_USER/Library/Android/sdk
    ```
    *(Replace with your actual SDK path, usually shown in Android Studio Settings > Languages & Frameworks > Android SDK)*

## Step 3: Build and Install

### Method A: Using Android Studio (Recommended)

1.  Connect your Pixel to your computer via USB.
2.  In the toolbar, select your Pixel device from the target device dropdown.
3.  Click the **Run** button (green play icon).
4.  The app will build and automatically launch on your phone.

### Method B: Using Command Line (Terminal)

1.  Open Terminal in the project root.
2.  If you don't have a Gradle wrapper, you might need to install `gradle` via Homebrew: `brew install gradle`.
3.  Run the build command:
    ```bash
    ./gradlew installDebug
    ```
    *(If you don't have the wrapper, you can run `gradle installDebug` if you have gradle installed locally)*

## Troubleshooting

- **Device not found:** Run `adb devices` in your terminal. If your device isn't listed, check your cable and ensure USB debugging is enabled.
- **Gradle sync failed:** Ensure you have an internet connection for the first build so Gradle can download dependencies.
