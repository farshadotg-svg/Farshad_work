# HamrahYar — همراه‌یار

A separate Android project for user-approved website form assistance.

## Project layout

The Android starter project is maintained in this repository under `hamrahyar-android/`.
The application uses Kotlin and Android WebView, with a bundled Persian RTL HTML interface.

## Current scope

- Open user-provided HTTPS websites inside the app.
- Inspect page structure and identify common form controls.
- Suggest mappings for common fields such as first name, last name, phone, and email.
- Keep passwords and one-time verification codes out of activity logs.
- Require the user to handle CAPTCHA, SMS/OTP, identity checks, and final submission.
- Do not bypass queues, anti-bot systems, or website security controls.

## Build

Open `hamrahyar-android/` in Android Studio and allow Gradle sync. Build the debug APK from the app module.

## Status

Starter project only. The APK has not yet been built or device-tested. Form recognition is an initial implementation and needs testing against real websites.
