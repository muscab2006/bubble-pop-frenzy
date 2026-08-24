# Bubble Pop Frenzy

A colorful, addictive bubble shooter puzzle game — fully offline.

## Features

- Classic bubble shooter gameplay (aim + shoot + match 3+)
- Progressive difficulty across levels
- Daily Challenge mode (same puzzle for everyone each day)
- 5 unlockable themes: Classic, Neon, Ocean, Sunset, Galaxy
- Procedurally generated sound effects (no audio files)
- Combo scoring system with particle effects
- Touch + mouse support
- Fully offline — all progress stored locally

## Play in Browser

Open `www/index.html` in any browser.

## Android APK — Built Automatically in GitHub Cloud

No Android Studio needed. Every push to `main` triggers a GitHub Actions
workflow that compiles a debug APK in the cloud.

**Get the APK:**

1. Go to the [Actions tab](../../actions) and open the latest **Build Android APK** run,
   then download the `bubble-pop-frenzy-debug-apk` artifact; or
2. Grab it directly from the [Latest Build release](../../releases/tag/latest).

Install `app-debug.apk` on your Android device (enable "Install from unknown sources").

### Manual local build (optional)

```bash
npm install
npx cap add android   # first time only
npx cap sync android  # after web changes
cd android && ./gradlew assembleDebug
```

## Project Structure

```
www/            game source (HTML/CSS/JS)
.github/workflows/build-apk.yml   cloud APK build pipeline
capacitor.config.json             Capacitor config (appId com.qeytil.bubblepop)
```

## Developed by Qeytil

## License

MIT
