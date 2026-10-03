# MyMoney

A clean, modern, and privacy-focused personal finance and expense manager for Android, built with Jetpack Compose, Room, and Material 3.

## Features

- **Expense & Income Tracking**: Record and categorize daily transactions with ease.
- **Loan & Debt Manager**: Keep track of borrowed and lent money, repayments, and due dates.
- **Budgeting & Insights**: Set category spending limits and monitor monthly budgets.
- **Offline-First & Private**: All data is stored locally in an encrypted Room database.
- **Google Drive Backup & Restore**: Securely sync and restore backups to your private Google Drive app storage.
- **AI Financial Assistant**: Optional Gemini-powered receipt scanning, natural language transaction entry, and spending insights using your own Gemini API key.
- **Bilingual & Currency Flexible**: Full support for English and Bengali (বাংলা), and custom currency formats (৳, $, €, etc.).
- **Automatic Updates**: Check for new releases directly within the app.

---

## Getting Started

### Prerequisites
- Android Studio Ladybug | 2024.2+ (or newer)
- Android SDK 35+
- JDK 17

### Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/Ahammad204/MyMoney.git
   ```
2. Open the project in Android Studio.
3. Sync Gradle and build the project:
   ```bash
   ./gradlew assembleDebug
   ```
4. Run on your Android emulator or physical device.

---

## Architecture & Tech Stack

- **UI**: 100% Jetpack Compose with Material 3 design and dynamic theming (Dark & Light modes).
- **Architecture**: MVVM with Kotlin Coroutines and StateFlow.
- **Database**: Room Database with migrations and offline persistence.
- **Cloud Backup**: Google Drive REST API integration using Credential Manager.
- **Background Tasks**: Android WorkManager for scheduled daily backups and recurring transactions.
- **Security**: Android Keystore encryption for secure API key storage.
- **CI/CD**: Automated GitHub Actions workflow for building and publishing signed APK releases.

---

## License

This project is licensed under the Apache License 2.0.
