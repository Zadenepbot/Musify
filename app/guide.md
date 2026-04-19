# How to Change the App Name and Icon

This guide provides instructions on how to customize the application's name and icon for your builds.

## 1. Changing the App Name

There are two main ways to change the app name:

### Method A: Environment Variable (Recommended for CI/CD)
The project supports an environment variable that overrides the app name during the build process.
- **Variable**: `METROLIST_APP_NAME`
- **Example**:
  ```bash
  export METROLIST_APP_NAME="My Custom App"
  ./gradlew :app:assembleFossRelease
  ```

### Method B: Manual Resource Change
Modify the resource file directly to change the default app name.
- **File Path**: `app/src/main/res/values/app_name.xml`
- **Change**: Replace `Musify` with your desired name.
  ```xml
  <string name="app_name">My Custom App</string>
  ```

---

## 2. Changing the App Icon

The app uses **Adaptive Icons** (for modern Android) and **Legacy Icons** (for older versions).

### A. Changing the Adaptive Icon (Vector-based)
Most modern Android devices use adaptive icons defined in XML.

1.  **Background Color/Drawable**:
    - **File Path**: `app/src/main/res/values/ic_launcher_background.xml`
    - **Change**: Modify the hex color code.
      ```xml
      <color name="ic_launcher_background">#YOUR_HEX_CODE</color>
      ```

2.  **Foreground (Vector Graphic)**:
    - **File Path**: `app/src/main/res/drawable/ic_launcher_foreground.xml`
    - **Action**: Replace the vector data in this file with your new icon's vector path.

3.  **Monochrome (Themed Icons)**:
    - **File Path**: `app/src/main/res/drawable/ic_launcher_monochrome.xml`
    - **Action**: Update this for the "Themed Icons" feature on Android 13+.

### B. Changing the Legacy/Static Icon (PNG/WebP)
For older Android versions or some launchers, you must replace the static images in the following folders:

- **HDPI**: `app/src/main/res/mipmap-hdpi/ic_launcher.webp`
- **MDPI**: `app/src/main/res/mipmap-mdpi/ic_launcher.webp`
- **XHDP**: `app/src/main/res/mipmap-xhdpi/ic_launcher.webp`
- **XXHDPI**: `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp`
- **XXXHDPI**: `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp`

*Note: You should also update the `ic_launcher_round.webp` files in the same directories.*

### C. Using Android Studio Resource Manager (Recommended)
The easiest way to generate all required sizes and formats:
1.  Open the project in **Android Studio**.
2.  Right-click the `app` folder -> **New** -> **Image Asset**.
3.  Select your icon and it will automatically generate all files in the correct paths.

---

## 🤖 For AI Agents & Automated Tools

If you are an AI agent or a script tasked with changing the app name or icon, follow this checklist:

### Checklist for App Name Change
1.  **Direct Resource Edit**: Use your `replace` or `write_file` tool to modify `app/src/main/res/values/app_name.xml`.
    - **Target**: `<string name="app_name">Musify</string>`
    - **New Value**: Replace "Musify" with the user's requested name.
2.  **Environment Variable Check**: If requested for a CI/CD pipeline, ensure the `METROLIST_APP_NAME` environment variable is exported before any Gradle command.

### Checklist for App Icon Change
1.  **Identify Assets**: Locate `app/src/main/res/drawable/ic_launcher_foreground.xml` for the adaptive icon.
2.  **Apply Vector Data**: If the user provides a SVG or Android Vector Drawable path, overwrite the content of `ic_launcher_foreground.xml`.
3.  **Apply Background Color**: Modify `app/src/main/res/values/ic_launcher_background.xml` to change the background hex color.
4.  **Legacy Icons**: Be aware that legacy icons are in `.webp` and `.png` formats across several `mipmap-` folders. Changing these requires generating new binary files which may be difficult via text-based tools. Recommend using the Android Studio Resource Manager if binary replacement is not possible.

### Verification
After applying changes, an agent can verify the name change by running:
```bash
./gradlew :app:assembleDebug
```
And then checking the logs or the generated APK metadata if tools are available.
