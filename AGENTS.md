# Working with Musify as an AI agent

Musify is a 3rd party YouTube Music client written in Kotlin. It follows material 3 design guidelines closely.

## Rules for working on the project

1. Always create a new branch for your feature work. Follow these naming conventions:
   - Bug fixes: `fix/short-description`
   - New features: `feat/short-description`
   - Refactoring: `ref/short-description`
   - Documentation: `docs/short-description`
   - Chores: `chore/short-description`
2. Branch descriptions should be concise yet descriptive enough to understand the purpose of the branch at a glance.
3. Always pull the latest changes from `main` before starting your work to minimize merge conflicts.
4. While working on your feature you should rebase your branch on top of the latest `main` at least once a day to ensure compatibility.
5. Commit names should be clear and follow the format: `type(scope): short description`. For example: `feat(ui): add dark mode support`. Including the scope is optional.
6
7. You are to follow best practices for Kotlin and Android development.

## Building and testing your changes

1. After making changes to the code, you should build the app to ensure that there are no compilation errors. Use the following command from the root directory of the project:

```bash
./gradlew :app:assembleFossDebug
```

2. If the build is not successful, review the error messages, fix the issues in your code, and try building again.
3. Once the build is successful, you can test your changes on an emulator or a physical device. Install the generated APK located at `app/build/outputs/apk/universalFoss/debug/app-universal-foss-debug.apk` and ask a human for help testing the specific features you worked on.