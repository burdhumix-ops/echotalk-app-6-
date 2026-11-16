# EchoTalks Android app

This repository contains the **native Android application** built with Kotlin and Jetpack Compose. It is not a website or a browser-based React app.

## Important: how to run the project

Do **not** use a GitHub "Preview" or generic web development server to run this project. Those preview servers are for web projects and cannot launch a native Android app.

1. Open this repository in **Android Studio**.
2. Let Android Studio finish the Gradle sync.
3. Add the Android app's `google-services.json` file at `app/google-services.json` when Firebase is configured.
4. Add the client-safe Supabase values to the local `.env` file (copy the names from `.env.example`). Never put a Supabase service-role key, database password, Firebase service-account JSON, or LiveKit API secret in the Android app.
5. Select an Android emulator or connect an Android phone with USB debugging enabled.
6. Run the `app` configuration.

The app source is under `app/src/main/java/co/znxdnd/echotalk/`. The main navigation and screen destinations are wired in `MainActivity.kt`; the screen implementations are in `ui/screens/`.

## Why only three tabs may appear at first

The current bottom navigation intentionally shows three primary tabs in its visible area. The other destinations (Rooms, Arcade, and EchoBot) are in the horizontally scrollable part of the same navigation bar. This is existing app behavior, not evidence that those screen source files are missing.

## Backend setup

- The app's checked-in Supabase configuration and setup guide point to project `tifdmhqhoeiqcbqjcchu`.
- Follow `docs/SUPABASE_SETUP.md` and the ordered SQL files in `supabase/migrations/` to configure the backend.
- Do not apply migrations to a different Supabase project just because it has a similar name.
- Keep all server-only secrets in Supabase Edge Function secrets, never in Git or the Android APK.

## Automated Android build

The GitHub Actions workflow in `.github/workflows/android-build.yml` runs the Android unit tests and assembles a debug APK when code is pushed or a pull request is opened. A successful run uploads the APK as an artifact named `echotalks-debug-apk`.

## Current navigation destinations

Discover, Rooms, DMs (labelled Chats in the current navigation), EchoBot, Arcade, and Profile.
