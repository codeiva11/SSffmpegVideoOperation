# SSffmpegVideoOperation — file-by-file deep research

Research date: 2026-11-04. Repo: `codeiva11/SSffmpegVideoOperation` (fork, created in Sunil's account 2026-11-01).
Local clone: `~/workspace/ssffmpeg-research/repo` (shallow, main @ `1f5505b1` + develop FETCH_HEAD @ `3127376c`).
Researcher note: library sources (10 files) read in full; all 30 app activities verified by grep for their query call + 4 read in full (MainActivity, Utils, BaseActivity, CompressVideoActivity); every build/CI/config file read in full.

## 1. Identity — it is a pure mirror, zero own commits

- Fork of `SimformSolutionsPvtLtd/SSffmpegVideoOperation` (upstream: 383 stars, 90 forks, Apache-2.0).
- Fork `main` vs upstream `main`: **identical** (compare API: 0 ahead, 0 behind, 0 commits).
- Fork `develop` vs upstream `develop`: **identical** (same single commit `3127376c`).
- Fork `main` HEAD: `1f5505b1` "Update README.md", 2022-07-27, by Yashwant Gowla. Upstream main has not moved since.
- Fork has: 0 stars, 0 forks, issues disabled, no releases, no own branches beyond upstream's two.
- GitHub reports repo size 128 MB, but the **current main tree is only ~640 KB of files** (1.3 MB with .git). The 128 MB lives in git history (old blobs — not inspected; shallow clone has 1 commit only). A fresh `--depth 1` clone is small.

## 2. What it is

An Android library that wraps **mobile-ffmpeg (Arthenica, FFmpeg 4.4, "full" package)** and ships ready-made FFmpeg command builders for common video/audio operations, plus a runner with progress callbacks, plus a demo app that exercises every operation.

Published artifact (per `SSffmpegVideoOperation/build.gradle`): `com.simform.videooperations:videooperations:1.0.8`, consumed via JitPack as `com.github.SimformSolutionsPvtLtd:SSffmpegVideoOperation:1.0.8` (README).

### File inventory (main branch, ~120 files)

| Area | Files | Notes |
|---|---|---|
| Root | `build.gradle`, `settings.gradle`, `gradle.properties`, `jitpack.yml`, `README.md`, `LICENSE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `gradlew[.bat]`, `gradle/wrapper/*` | Gradle 7.0.2, AGP 7.0.4, Kotlin 1.7.0 |
| `.github/` | `workflows/github-actions.yml`, `ISSUE_TEMPLATE/bug_report.md`, `feature_request.md` | CI is a demo template (see §5) |
| `SSffmpegVideoOperation/` (library) | `build.gradle`, `proguard-rules.pro`, `consumer-rules.pro` (empty), `src/main/AndroidManifest.xml`, 10 Kotlin files | The actual library |
| `app/` (demo) | `build.gradle`, `proguard-rules.pro`, `src/main/AndroidManifest.xml`, 30 Kotlin files (3,190 lines), ~30 layouts, `assets/little_lord.ttf`, launcher icons, `screenshots/*.svg` | Demo app, package `com.simform.videoimageeditor` |

No native (C/C++) code, no `.so`/`.aar` in the tree — FFmpeg arrives as the `com.arthenica:mobile-ffmpeg-full:4.4` Maven dependency.

## 3. Library, file by file (`com.simform.videooperations`, 10 files)

### `FFmpegQueryExtension.kt` (17.5 KB) — the core, the value of the repo
One class, `FRAME_RATE` var (default 25), ~27 public functions. Each builds an FFmpeg argv `Array<String>` and returns it. Every query uses `-preset ultrafast`.

| Function | What it builds |
|---|---|
| `cutVideo` | `-i in -ss start -to end -r $FRAME_RATE -preset ultrafast out` (re-encodes; no `-c copy`) |
| `imageToVideo` | `-loop 1 -i img -s WxH -vf format=yuv420p,fade=in/out -t sec out` |
| `addVideoWaterMark` | `-i video -i image -filter_complex overlay=x:y out` |
| `addTextOnVideo` | `-vf drawtext=text=…:fontfile=…:x:y:fontsize:fontcolor[:box…] -c:a copy out` |
| `combineImagesAndVideos` / `combineVideos` | per-input scale+setdar, then `concat=n=N:v=1:a=1` with a silent `anullsrc` audio track |
| `compressor` | `-y -i in -s WxH -r (FRAME_RATE-5) -vcodec mpeg4 -b:v 150k -b:a 48000 -ac 2 -ar 22050 out` |
| `extractImages` | `-vf fps=N out` (frame extraction) |
| `extractAudio` | `-vn -ar 44100 -ac 2 -b:a 256k -f mp3 out` |
| `videoMotion` | `setpts` + `atempo`, `-b:v 2097k -r 60 -vcodec mpeg4` |
| `videoReverse` | `-vf reverse` (+ `-af areverse` with audio) |
| `videoFadeInFadeOut` | `fade=t=in/out` filters, `-acodec copy` |
| `convertVideoToGIF` | plain `-i in -preset ultrafast out.gif` (no palette/scale — low-quality GIF) |
| `rotateVideo` | only sets metadata `rotate=N` + `-codec copy` (no re-encode; many players ignore the tag) |
| `flipVideo` | `-vf transpose=N` |
| `mergeAudioVideo` | `[1:a]volume=0.15,apad + amerge`, `-c:v copy -shortest` |
| `removeAudioFromVideo` | `-an` |
| `mergeImageAndAudio` | `-loop 1 -i img -i audio -shortest -c:a copy` |
| `applyRatio` | `-aspect ratio` |
| `mergeGIF` | `-ignore_loop 0`, scale + overlay second GIF |
| `mergeAudios` | `amix=inputs=N:duration=…`, `-codec:a libmp3lame -q:a 0` |
| `audioVolumeUpdate` | `-af volume=V` |
| `audioMotion` | `-filter:a atempo` + `-vn` |
| `cutAudio` | `-ss/-to` |
| `compressAudio` | `-ab bitrate` |

### `CallBackOfQuery.kt` — the runner
- `callQuery(query, FFmpegCallBack)`: spawns a Thread, rendezvous via `CyclicBarrier(2)` (caller returns once the worker starts, not when it finishes), then `FFmpeg.execute(query)`; log + statistics callbacks are re-posted to the main thread via `Handler(Looper.getMainLooper())`; maps return code to `success()` / `cancel()` / `failed()` (+ `Config.printLastCommandOutput(Log.INFO)` on failure).
- `cancelProcess(executionId)` / `cancelProcess()`: wrappers over `FFmpeg.cancel`.
- **Bug (minor):** `cancelProcess(executionId)` does `if (!executionId.equals(0))`. `executionId` is `Long`, `0` is `Int` — `Long.equals(Int)` is always false, so the `else` branch (`FFmpeg.cancel()` — cancel all) is dead code; id 0 calls `FFmpeg.cancel(0)`.

### `Common.kt` — helpers + constants (object)
- Constants: request codes (PERM 111, VIDEO 112, IMAGE 113, AUDIO 114), `OUT_PUT_DIR = "Output"`, ratios (16:9, 4:3, 16:10, 5:4, 2:21:1, 2:35:1, 2:39:1), bitrates (96–320 kbps), durations (longest/shortest/first).
- `stringForTime(timeMs)` — ms → HH:MM:SS.
- `getFrameRate(file)` — reads video track frame rate via `MediaExtractor`.
- `getFileSize(file)` — formats B/KB/MB.
- `selectFile(activity, maxSelection, isImage/isAudio)` — launches `FilePickerActivity` (jaiselrahman FilePicker) with the right config.
- `getFilePath(context, ext)` — output under `getExternalFilesDir("Output")`, name `Output<epoch-seconds>.<ext>`.
- `getFileFromAssets(context, fileName)` — copies an asset (the font) to cacheDir.
- **Bug (real):** `getFrameRate()` writes the detected rate into a *throwaway* instance — `FFmpegQueryExtension().FRAME_RATE = …` — which is immediately discarded. The query builders use `this.FRAME_RATE` on the caller's own instance (default 25). **Frame-rate detection has no effect on any query; every query runs at 25 fps unless the caller sets FRAME_RATE on the same instance.** (`cutVideo`, `imageToVideo`, `compressor`, `cutAudio` all call `Common.getFrameRate(...)` and then use their own default 25.)
- **Bug (cosmetic):** `getFileSize()` — the final else branch (file < 1 KB) appends `" GB"`: a 500-byte file reports as `"500 GB"`.

### `FFmpegCallBack.kt` — callback interface
`process(logMessage)`, `statisticsProcess(statistics)`, `success()`, `cancel()`, `failed()` — all with default empty bodies.

### `Statistics.kt` / `LogMessage.kt`
Thin data-class mirrors of mobile-ffmpeg's own `Statistics` / `LogMessage` (Statistics even credits Taner Sener, `@since v2.1`). `Statistics.update()` merges non-zero fields.

### `FileSelection.kt`, `ISize.kt`, `Paths.kt`, `SizeOfImage.kt`
- `FileSelection`: `selectedFiles(mediaFiles, requestCode)` interface (implemented by BaseActivity).
- `ISize`: `width()` / `height()`.
- `Paths`: `{ filePath: String, isImageFile: Boolean }` — input list item for combine/merge.
- `SizeOfImage(path) : ISize`: `BitmapFactory.Options.inJustDecodeBounds` to read image dimensions lazily.

### `AndroidManifest.xml` (library)
Only one line of substance: `<uses-sdk tools:overrideLibrary="com.arthenica.mobileffmpeg" />`.

## 4. Demo app, file by file (`com.simform.videoimageeditor`, 30 Kotlin files, 3,190 lines)

Pattern (verified by grep across all 30 files): every activity = pick file(s) via `Common.selectFile` → build query via one `FFmpegQueryExtension` method → `CallBackOfQuery().callQuery(query, FFmpegCallBack)` → progress view + output path + file size. 25 call sites, all 27 query functions exercised (rotate/flip are called as statements, not assigned).

- `MainActivity.kt` — two buttons: Video Operation → `VideoProcessActivity`, Image/GIF Operation → `OtherFFMPEGProcessActivity`.
- `BaseActivity.kt` — abstract; holds a shared `FFmpegQueryExtension()` instance, `Utils()`, `MediaMetadataRetriever`; `onActivityResult` forwards picked files to `selectedFiles()`.
- `Utils.kt` — `addSupportActionBar`, `openActivity`.
- `middlewareActivity/VideoProcessActivity.kt`, `OtherFFMPEGProcessActivity.kt` — menus listing the operations.
- `videoProcessActivity/` (17): AddTextOnVideo, AddWaterMarkOnVideo, AspectRatio, CombineImageAndVideo, CombineImages, CombineVideos, CompressVideo, CutVideoUsingTime, ExtractAudio, ExtractImages, FastAndSlowVideoMotion, ImageToVideoConvert, MergeAudioVideo, MergeImageAndMP3, RemoveAudioFromVideo, ReverseVideo, VideoFadeInFadeOut, VideoRotateFlip, VideoToGif. (Count: 19 files listed in tree; 17 named here + CombineImages/CombineVideos counted — the directory holds the full set; each maps 1:1 to a query function.)
- `otherFFMPEGProcessActivity/` (7): AudiosMerge, ChangeAudioVolume, CompressAudio, CropAudio, FastAndSlowAudio, MergeGIF.
- All 28 activity files use `kotlinx.android.synthetic` imports and `startActivityForResult` — both deprecated/removed in modern Kotlin/Android.
- `app/build.gradle`: compileSdk 31, targetSdk 31, minSdk 24; `kotlin-android-extensions` + `kotlin-kapt` with a Room schema dir configured but **no Room dependency** (leftover); Glide 4.12.0, `timePickerWithSeconds:1.0.1` (JitPack), FilePicker 1.3.2.
- `app/src/main/AndroidManifest.xml`: `requestLegacyExternalStorage="true"` (ignored on targetSdk 31 / Android 11+ — scoped storage applies; FilePicker file paths may fail on Android 11+), `READ/WRITE_EXTERNAL_STORAGE` + CAMERA permissions, every activity portrait, MainActivity exported/launcher.
- `assets/little_lord.ttf` (87 KB) — font used by `drawtext`.
- No tests anywhere in the repo (no `src/test`, no `src/androidTest`).

## 5. Build system — main branch will NOT build as-is in 2026

- Root `build.gradle`: `jcenter()` in both `buildscript` and `allprojects` repositories — **JCenter was shut down in 2022**; `gradle-bintray-plugin:1.8.0` (Bintray dead); `android-maven-gradle-plugin:2.1` (dead). These must be removed before any build.
- AGP 7.0.4 / Kotlin 1.7.0 / Gradle 7.0.2 / JDK 11 (`jitpack.yml`: openjdk11).
- Library: compileSdk 30, targetSdk 30, buildTools 29.0.3, ABIs `armeabi-v7a, arm64-v8a, x86` (**no x86_64**), minSdk 24, multiDex.
- Core dependency `com.arthenica:mobile-ffmpeg-full:4.4` = FFmpeg 4.4 (released 2021). It **does exist on Maven Central** (search index: versions `4.4`, `4.4.LTS`) — but Arthenica's mobile-ffmpeg is unmaintained; the maintained successor is **ffmpeg-kit** (latest 6.0.LTS on Central). Note: this sandbox's egress proxy returns 404 for *all* `repo1.maven.org` file fetches (verified against androidx core-ktx too), so the AAR bytes themselves were not verified here.
- CI (`.github/workflows/github-actions.yml`) is a **demo template**: triggers only when a `**.yml` file changes, echoes greetings, runs `./gradlew test` + `./gradlew assemble`. There is no real CI, no test job on code changes, and no tests to run.
- JitPack is the distribution path (README); JitPack would hit the same dead `jcenter()`/bintray lines when building.

## 6. Develop branch — the 2025 update (commit `3127376c`, 2025-06-23, Rajan-p-simform)

Single orphan commit (no shared history with main). "Provide local support to mobile-ffmpeg and time picker." Diff vs main (11 files, +720/−10):

- `SSffmpegVideoOperation/build.gradle`: `com.arthenica:mobile-ffmpeg-full:4.4` → `implementation(files("libs/mobile-ffmpeg.aar"))` via a `flatDir` repo. **But `libs/` is gitignored (`SSffmpegVideoOperation/.gitignore:11`) and the AAR is not in the repo — develop does NOT build from a clean clone.** (This matches upstream PR #65 "Support aar dependency", still open.)
- `app/build.gradle`: drops the JitPack `timePickerWithSeconds:1.0.1` dependency; the time picker is vendored locally instead (`app/.../ikovac/timepickerwithseconds/TimePicker.java` 420 lines + `MyTimePickerDialog.java` 172 lines).
- Root `build.gradle`: `jcenter()` removed → `groovy.jfrog.io` + `gradlePluginPortal`; AGP 7.0.4 → 7.4.1. (Bintray + android-maven plugins still present.)
- Gradle wrapper 7.0.2 → 7.5.
- `CropAudioActivity` / `CutVideoUsingTimeActivity` adjusted to the local time picker (+ layouts `time_picker_dialog.xml`, `time_picker_widget.xml`, strings).

## 7. Upstream issues & PRs (open, all unmerged)

Upstream: 36 open issues, 10 open PRs. The recent cluster (July–Aug 2025, all by Rajan-p-simform except #72):

- #72 (2025-08-28, kunaljaypatel): **"16 KB page size"** — Android 15's 16 KB page-size requirement; the old mobile-ffmpeg `.so` files are not 16 KB-aligned, so the library can fail to load on 16 KB-page devices. This is the single most important 2026 blocker.
- #62 (2025-07-01): "Upgrade AGP and replace kotlin-android-extensions with viewbinding" (PR, open).
- #65 (2025-07-07): "Support aar dependency" (PR, open) — the develop-branch work.
- #63 (2025-07-07): "Implement photo picker" (PR, open).
- #64, #66, #67, #68, #69, #70, #71: image-to-video query update, edge-to-edge, combine-videos query update, combine image+video frame duplication (>1000 frames), watermark positions, compress-query optimisation, encoding-quality option. All open, none merged.
- Upstream main itself untouched since 2022-07-27.

## 8. Bugs and quality findings (verified in code)

1. **Frame-rate detection is dead code** — `Common.getFrameRate()` sets `FRAME_RATE` on a throwaway `FFmpegQueryExtension()` instance (`Common.kt`). Every query runs at 25 fps. (Evidence: `Common.kt` `getFrameRate`; `FFmpegQueryExtension.kt` uses `this.FRAME_RATE`.)
2. **`getFileSize()` says "GB" for bytes** — files under 1 KB report e.g. "500 GB" (`Common.kt`).
3. **`cancelProcess(executionId)` dead branch** — `Long.equals(Int 0)` is always false (`CallBackOfQuery.kt`).
4. **`compressor()` hardcodes 2000s-era settings** — `-vcodec mpeg4 -b:v 150k -ar 22050 -b:a 48000`, and drops frame rate by 5. Low quality by 2026 standards; upstream issue #70 asks to optimise this query.
5. **`cutVideo()` re-encodes** instead of `-c copy` stream copy — slow for a plain trim.
6. **`convertVideoToGIF()` has no palette/scale** — poor-quality GIFs.
7. **`rotateVideo()` only writes metadata** — players that ignore the rotation tag show the video unrotated.
8. **No tests, no real CI.** The workflow only runs on `.yml` changes.
9. **No x86_64 ABI** (library `abiFilters`: armeabi-v7a, arm64-v8a, x86).
10. **Scoped-storage breakage**: `requestLegacyExternalStorage=true` with targetSdk 31 is a no-op on Android 11+; the FilePicker path-based flow is pre-scoped-storage.

## 9. Verdict — 2026 use

**As-is, this is not usable in a 2026 app.** Not because the idea is bad — the query-builder logic is small, readable, and the demo covers every function — but because:

- the build is broken (dead `jcenter()`/bintray), the toolchain is 2021–22 (AGP 7.0.4, Kotlin 1.7.0, Gradle 7.0.2),
- the engine is FFmpeg 4.4 via an unmaintained wrapper (mobile-ffmpeg; successor ffmpeg-kit 6.0),
- targetSdk 30 is below Play Store requirements (34/35 in 2025–26),
- the 16 KB page-size issue (#72) can stop the native libs loading on current devices,
- synthetics / `startActivityForResult` / `kotlin-android-extensions` are removed in current toolchains.

**Realistic 2026 path (if Sunil wants this):** keep `FFmpegQueryExtension`'s query logic (it is the asset), replace `mobile-ffmpeg-full:4.4` with `ffmpeg-kit-full:6.0` (same `FFmpeg.execute` API shape, maintained, 16 KB-aligned), modernise the build (AGP 8.x, Kotlin 2.x, targetSdk 34/35, ViewBinding, Activity Result API, Photo Picker, MediaStore/SAF file access), fix bugs 1–3 above, and add the local-AAR path properly if the AAR route is wanted (commit the AAR or fetch it in CI — the develop branch's approach is currently broken from a clean clone).

**Open question for Sunil:** what does he want this for — use the library as-is in an app, modernise it, or just extract the FFmpeg query builders into his own project? The answer decides the work.

## 10. What was NOT verified

- The AAR bytes of `mobile-ffmpeg-full:4.4` (sandbox proxy 404s all of repo1.maven.org; Central search index confirms the artifact exists).
- Whether JitPack currently builds tag 1.0.8 (would need to trigger a JitPack build; not attempted).
- The 128 MB of git history (shallow clone only; old blobs not inspected).
- Runtime behaviour — no Android SDK in the sandbox, so nothing was compiled or run; all findings are from source.
