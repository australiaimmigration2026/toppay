# Welcome to your Expo app 👋

This is an [Expo](https://expo.dev) project created with [`create-expo-app`](https://www.npmjs.com/package/create-expo-app).

## Get started

### Run on a USB-connected Android phone from Windows

Java 17 and the Android SDK are configured as Windows user environment variables.
With USB debugging enabled and the computer authorized on your phone, run:

```powershell
cd D:\web\Toppay
npm.cmd run android:phone
```

This launcher loads the saved environment settings, checks device authorization,
connects port 8081 over USB for a single phone, then builds and launches the app.
Keep its terminal open while using the development build. The first build may
download additional Gradle dependencies. Expo Go does not include this project's
custom native notification module or native Google sign-in module.

For the manual commands instead:

Enable USB debugging on the phone, accept its authorization prompt, then run
these commands in PowerShell from the project directory:

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
$env:ANDROID_HOME = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
npm.cmd run android -- --device
```

The local setup saves `JAVA_HOME` and `ANDROID_HOME` as Windows user environment
variables. Restart your terminal or VS Code after installation, or use the commands
above to load them into an already-open terminal. If `adb devices` shows
`unauthorized`, unlock your phone and accept **Allow USB debugging**. On this
project, Android Studio's Java 25 runtime fails while loading the Gradle
settings plugin with `Error resolving plugin ... > 25.0.3`. Use JDK 17 for
the build. The `.cmd` command works when PowerShell blocks `npm.ps1`.

Keep the Expo development server running while using the debug app. If the
phone cannot reach it over USB, run:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" reverse tcp:8081 tcp:8081
```

### Build a signed Android release APK

From the project root, run:

```powershell
npm.cmd run android:release
```

The release launcher loads the saved JDK 17 `JAVA_HOME` even in an already-open
PowerShell session, then securely prompts for the Android signing store and key
passwords if they are not already set in that session. The APK is written to
`android\app\build\outputs\apk\release\app-release.apk`.

1. Install dependencies

   ```bash
   npm install
   ```

2. Start the app

   ```bash
   npx expo start
   ```

In the output, you'll find options to open the app in a

- [development build](https://docs.expo.dev/develop/development-builds/introduction/)
- [Android emulator](https://docs.expo.dev/workflow/android-studio-emulator/)
- [iOS simulator](https://docs.expo.dev/workflow/ios-simulator/)
- [Expo Go](https://expo.dev/go), a limited sandbox for trying out app development with Expo

You can start developing by editing the files inside the **app** directory. This project uses [file-based routing](https://docs.expo.dev/router/introduction).

## Get a fresh project

When you're ready, run:

```bash
npm run reset-project
```

This command will move the starter code to the **app-example** directory and create a blank **app** directory where you can start developing.

## Learn more

To learn more about developing your project with Expo, look at the following resources:

- [Expo documentation](https://docs.expo.dev/): Learn fundamentals, or go into advanced topics with our [guides](https://docs.expo.dev/guides).
- [Learn Expo tutorial](https://docs.expo.dev/tutorial/introduction/): Follow a step-by-step tutorial where you'll create a project that runs on Android, iOS, and the web.

## Join the community

Join our community of developers creating universal apps.

- [Expo on GitHub](https://github.com/expo/expo): View our open source platform and contribute.
- [Discord community](https://chat.expo.dev): Chat with Expo users and ask questions.
