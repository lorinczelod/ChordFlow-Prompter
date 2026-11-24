# ChordFlow Prompter

An offline-first Android lyric teleprompter for church worship teams. It listens to live
acoustic guitar or piano through the phone's microphone, recognises the chords being
played, and scrolls the lyrics when the band reaches the next chord in the chart.

The stage display shows **lyrics only** — every chord and ChordPro directive is stripped
by the parser and kept in memory as an invisible trigger.

---

## 1. What it does

| Area | Behaviour |
|---|---|
| **Offline stage use** | Teleprompter, chord detection, transposition and the song database need no network at all. |
| **Optional online prep** | A single "Download Free Songs" screen fetches open ChordPro/OnSong charts from public GitHub repositories, or from any raw URL you paste. |
| **Local import** | Pulls `.cho`, `.chopro`, `.onsong`, `.pro`, `.crd` and `.txt` chord sheets from Downloads, Drive or anywhere the system file picker reaches. |
| **Display** | Pure black `#000000` with phosphor green `#00FF41` / `#39FF14`, monospace, screen kept awake in teleprompter mode. |
| **Key changing** | Per-song saved transpose (-11..+11), a target-key picker, and `-1 / +1` buttons in the live stage HUD. Slash chords and extensions transpose with correct sharp/flat spelling. |
| **Setlists** | Multiple named setlists, long-press drag-and-drop reordering, per-song key badges. |
| **Seeded content** | Two public-domain hymns are written to the database on first launch, so the app works the moment it installs. |

---

## 2. Getting the APK out of GitHub (no Android Studio needed)

1. **Create an empty repository** on GitHub — for example `ChordFlow-Prompter`. Do not add
   a README, `.gitignore` or licence; this project already has them.

2. **Upload the project.** Either:

   *Web upload:* on the empty repo page click **uploading an existing file**, drag the
   whole unzipped `ChordFlowPrompter` folder's contents in, and commit to `main`.
   Make sure the hidden `.github` folder goes up too — if the web uploader skips it,
   create the file manually with **Add file → Create new file** and the path
   `.github/workflows/build-apk.yml`.

   *Or from a terminal:*
   ```bash
   cd ChordFlowPrompter
   git init -b main
   git add .
   git commit -m "ChordFlow Prompter: initial commit"
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```

3. **The build starts itself.** Pushing to `main` triggers `.github/workflows/build-apk.yml`.
   Open the **Actions** tab to watch it. It takes roughly 5–10 minutes on the first run,
   less afterwards because Gradle's cache is reused.

4. **Download the APK.** Open the finished run, scroll to **Artifacts**, and download
   **ChordFlowPrompter-debug-apk**. It arrives as a `.zip` containing `app-debug.apk`.

5. **Install on the phone.** Move the APK to the device, open it, and allow
   "install unknown apps" for whichever app you opened it from. Grant the microphone
   permission the first time you start listening.

To rebuild at any time without pushing a change: **Actions → Build APK → Run workflow**.

### If the build fails

The workflow uploads a `build-reports` artifact on failure. The two most likely causes:

- **A dependency version was unavailable.** Edit `gradle/libs.versions.toml` — every
  version lives in that one file.
- **Gradle/AGP mismatch.** The workflow pins Gradle 8.9 and the project uses Android
  Gradle Plugin 8.6.1, which are compatible. If you raise one, raise the other.

---

## 3. Project layout

```
ChordFlowPrompter/
├── .github/workflows/build-apk.yml      CI: builds and publishes the APK
├── settings.gradle.kts
├── build.gradle.kts                     root build
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml               every dependency version
│   └── wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── drawable/ic_launcher.xml
        │   └── values/{colors,strings,themes}.xml
        └── java/com/chordflow/prompter/
            ├── ChordFlowApp.kt          Application, seeds the database
            ├── MainActivity.kt          Compose host + screen routing
            ├── audio/
            │   ├── FFT.kt               radix-2 Cooley-Tukey, self-contained
            │   ├── ChromaAnalyzer.kt    pitch-class profile + chord templates
            │   └── AudioChordDetector.kt AudioRecord capture, noise gate, StateFlow
            ├── core/
            │   ├── ChordTransposer.kt   semitone shifting, slash chords, enharmonics
            │   ├── ChordParser.kt       ChordPro / inline / chord-over-lyric parsing
            │   └── ChordMatcher.kt      "did the band play that chord?"
            ├── data/
            │   ├── Song.kt  SongDao.kt  AppDatabase.kt  SeedSongs.kt
            │   ├── SongRepository.kt    local library + file import
            │   └── SettingsStore.kt     DataStore preferences
            ├── net/
            │   └── OnlineSongRepository.kt   the only networking in the app
            ├── ui/
            │   ├── theme/Theme.kt       monochrome green Material 3 theme
            │   ├── Components.kt        shared green widgets
            │   ├── SongListScreen.kt    setlist manager, import, reorder
            │   ├── SongDownloaderScreen.kt
            │   ├── SongEditorScreen.kt  lyrics, key changer, preview
            │   └── TeleprompterScreen.kt stage view + HUD
            └── vm/
                ├── LibraryViewModel.kt  DownloaderViewModel.kt
                ├── EditorViewModel.kt   TeleprompterViewModel.kt
```

---

## 4. How the chord detection works

```
AudioRecord  44.1 kHz, 16-bit PCM, mono
     ↓  ring buffer, 16384-sample frames, 4096-sample hop (~93 ms between readings)
Hann window  →  FFT magnitude spectrum        (FFT.kt)
     ↓  RMS noise gate, driven by the HUD sensitivity slider
Per-semitone band energy, D2…B6                (ChromaAnalyzer.kt)
     ↓  fold into a 12-bin pitch class profile, normalise
Cosine correlation against maj / min / 7 / maj7 / m7 / sus2 / sus4 / dim / aug / add9
     ↓  penalty for unexplained energy, small bias toward plain triads
Confidence threshold  →  2-frame agreement debounce
     ↓
StateFlow<DetectionState>  →  teleprompter auto-scroll
```

**Why the frame is 16384 samples and not the more usual 4096.** At 44.1 kHz a 4096-point
FFT puts bins ~10.8 Hz apart, which is wider than a semitone below about 370 Hz. The low
fundamentals of open guitar chords then smear across neighbouring semitones and the
chord comes out wrong — in testing, an open G was read as C7. At 16384 the bins are
~2.7 Hz, which resolves cleanly down to the low E string. The cost is ~371 ms of analysis
latency, which is musically irrelevant because chords last much longer than that.

### What was verified, and how

The framework-independent engine was compiled with Kotlin 2.0.21 and run against
synthesised guitar and piano voicings (fundamental plus six to ten harmonics, random
phase, optional detuning and pink noise):

- Transposition, enharmonic spelling, slash chords, key arithmetic — all pass.
- ChordPro, inline-bracket and chord-over-lyric parsing, including chord stripping — all pass.
- Chord recognition: open G, C, D, Em, Am, F, Bb, A, E, Dm, Bm, G7, Dsus4, Am7, Cmaj7
  and close piano triads — recognised correctly, and still correct with ±18 cents of
  detuning, brighter harmonic content, and pink noise at 50% of signal level.
- White noise scores 0.38, below the default 0.62 confidence threshold, so it is gated out.

**Known limitation:** `Csus2` and `Gsus4` contain identical pitch classes (C, D, G), so a
chromagram cannot separate them — nor can any detector working from pitch classes alone.
The teleprompter's matcher treats a sus chord as its parent triad, so this does not affect
scrolling.

The Android-dependent layers (Compose UI, Room, DataStore, OkHttp) could not be compiled
in the environment this was written in, because Google's Maven repository was unreachable
there. **The first GitHub Actions run is therefore the first real compile of those files.**
If something fails, it will almost certainly be a small import or signature fix in the UI
layer rather than anything structural.

---

## 5. Using it on stage

1. **Before the service** (on Wi-Fi): download or import your songs, set each one's key
   in the editor, and drag them into Sunday's order in a setlist.
2. **On stage** (no network needed): open the song, tap **MIC OFF** to start listening and
   grant the microphone permission the first time.
3. The HUD shows `KEY: G`, `MIC: [G] -> NEXT: [D]` and an input-level meter.
4. **Sensitivity slider** (gear icon) raises the noise gate until drums and crowd noise
   stop triggering it. **Match confidence** raises the bar for what counts as a chord.
5. **AUTO / MANUAL** toggles auto-scroll. **PREV / NEXT** always work as a fallback, and
   tapping any line jumps straight to it.
6. **`-1` / `+1`** beside the key change the key live. The lyrics never move — only the
   hidden chord triggers do.

### Tuning tips for a loud room

| Symptom | Fix |
|---|---|
| Scrolls on drums or crowd noise | Raise **mic sensitivity** (noise gate) |
| Scrolls on the wrong chord | Raise **match confidence**, or set matching to **EXACT** |
| Will not scroll at all | Lower sensitivity, or set matching to **ROOT** |
| Scrolls a beat late | Expected — detection needs ~0.4 s of steady sound |

---

## 6. Song sources and copyright

The downloader points at public GitHub repositories of open and public-domain charts, and
at any raw URL you paste. Repository layouts and branch names change, so if a catalog
returns nothing, the **paste a raw URL** box works against any reachable file.

Two public-domain hymns ship with the app: *Amazing Grace* (John Newton, 1779) and
*Holy, Holy, Holy* (Reginald Heber, 1826).

**On "How Great Thou Art":** the familiar English lyric is Stuart K. Hine's 1949
translation and is still under copyright, so it is not bundled. *Holy, Holy, Holy* is
seeded in Bb instead. If your church holds a CCLI licence covering reproduction, add your
own copy through **Import File**. The same applies to any modern worship song — this app
stores and displays whatever you give it, and the licensing of that content is yours to
hold.

---

## 7. Permissions

| Permission | Used for |
|---|---|
| `RECORD_AUDIO` | Live chord detection. Requested at runtime, only when you start listening. Audio is analysed in memory and never recorded, stored or transmitted. |
| `INTERNET` | Only the "Download Free Songs" screen. |
| `ACCESS_NETWORK_STATE` | Showing a clean "you are offline" message instead of a network error. |

---

## 8. Stack

Kotlin · Jetpack Compose (Material 3) · MVVM with StateFlow and Coroutines ·
Room · DataStore Preferences · OkHttp + kotlinx.serialization · AGP 8.6.1 · Gradle 8.9 ·
minSdk 24 · targetSdk 34
