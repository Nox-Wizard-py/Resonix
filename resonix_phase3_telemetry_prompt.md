# Resonix — Profiler Analysis & Phase 3 Targeted Prompt
## Based on Live Android Studio Telemetry

---

## 🔬 PROFILER DATA ANALYSIS

### Image 1 — Idle State (Home Screen, No Interaction)
| Metric | Value | Verdict |
|--------|-------|---------|
| App CPU | 10% | ⚠️ HIGH for idle — should be <2% |
| Others CPU | 40% | 🔴 CRITICAL — system thrashing |
| Total CPU | ~50% | 🔴 App is working hard while doing nothing |
| Threads | 104 | ⚠️ Excessive for idle state |
| Total Memory | 294 MB | 🔴 CRITICAL — target is <150 MB |
| Java Heap | 28.8 MB | ✅ Acceptable |
| Native Heap | 36.7 MB | ✅ Acceptable |
| Graphics (GPU) | 100 MB | 🔴 CRITICAL — this is the main offender |
| Stack | 3.3 MB | ✅ Normal |
| Code | 30.2 MB | ✅ Normal |
| Others | 95 MB | 🔴 CRITICAL — unusually high |

### Image 2 — Active State (Scroll + 2 Refreshes)
| Metric | Value | Delta from Idle | Verdict |
|--------|-------|-----------------|---------|
| App CPU | 12% | +2% | ✅ Reasonable for scroll |
| Others CPU | 19% | -21% | ✅ System stabilized |
| Total CPU | ~35-45% | Spiky | ⚠️ Spikes on refresh |
| Threads | 125 | +21 threads | 🔴 Thread leak suspected |
| Total Memory | 378.1 MB | +84 MB | 🔴 Memory grew 28% during use |
| Java Heap | 67 MB | +38.2 MB | 🔴 GC pressure — objects not released |
| Native Heap | 50.2 MB | +13.5 MB | ⚠️ Growing |
| Graphics (GPU) | 121.1 MB | +21.1 MB | 🔴 GPU memory climbing |
| Stack | 4.9 MB | +1.6 MB | ⚠️ Thread growth confirmed |
| Code | 35.6 MB | +5.4 MB | ✅ Normal (JIT compilation) |
| Others | 99.3 MB | +4.3 MB | 🔴 Still very high |
| GC Events | 3 visible (trash icons) | — | 🔴 GC running during scroll = jank |

---

## 🚨 ROOT CAUSE IDENTIFICATION

### Finding #1 — GPU Memory at 100–121 MB (CRITICAL)
**This is the single biggest problem in the entire app.**

100 MB of GPU memory while IDLE means one of these is true:
- Hardware-accelerated layers are being allocated for off-screen composables
- `Modifier.graphicsLayer {}` or `Modifier.blur()` is running on large bitmaps
- The blur background in `MiniPlayer.kt` or `Thumbnail.kt` is rendering at full resolution
- Animated composables are holding onto GPU textures when they shouldn't be

**The blur variants in MiniPlayer.kt (3 AsyncImage calls) are the prime suspect.**
Even with the 144px fix from Phase 2, if blur rendering uses `RenderEffect` or
`BlurMaskFilter` on the GPU, it allocates GPU texture memory independently of
the bitmap size. This needs to be verified and fixed.

### Finding #2 — 104 Threads Idle, 125 Threads Active (+21 on scroll)
**Thread leak or uncontrolled coroutine dispatchers.**

21 new threads spawned during scroll + 2 refreshes is abnormal.
Normal Compose scroll should create 0 new threads.
The refreshes are likely launching coroutines on `Dispatchers.IO` or
`Dispatchers.Default` without a bounded thread pool, causing thread proliferation.

Each thread carries ~64KB stack memory minimum — 125 threads = ~8 MB stack
(confirmed by Stack: 4.9 MB reading).

### Finding #3 — Java Heap jumped from 28.8 MB → 67 MB (+38 MB on use)
**3 GC events during active use = jank frames guaranteed.**

Each GC pause on Android takes 5–20ms. At 16.67ms per frame budget,
even one GC event causes a dropped frame. You had 3 during scroll + refresh.

The 38 MB heap growth indicates:
- New bitmap objects allocated on scroll without reuse
- ViewModel or repository creating new List copies on every refresh
- Image requests creating intermediate byte arrays

### Finding #4 — "Others" Memory: 95–99 MB
This category includes anonymous mapped memory, ion buffers, and graphics
driver allocations. At 95 MB idle, this suggests the ExoPlayer / media session
is holding large audio buffers, or there are untracked native allocations.

---

## 🎯 PHASE 3 — TARGETED FIXES BASED ON PROFILER DATA

**Priority order re-ranked based on telemetry. Fix GPU memory first.**

---

### FIX #1 — GPU MEMORY: Eliminate or Downscale Blur Rendering
**Target: Reduce GPU from 100 MB → <40 MB**

The blur background (common in music players for the "frosted glass" effect
behind the mini player and full player) is the GPU memory killer.

#### Step 1 — Find all blur usage in the codebase:
Search for:
- `BlurTransformation`
- `RenderEffect`
- `BlurMaskFilter`
- `Modifier.blur(`
- `blur` in `MiniPlayer.kt`, `Thumbnail.kt`, `PlayerScreen.kt`
- Any `AsyncImage` with `blur` in its cache key (from Phase 2: `mini_blur_`, `player_blur_`)

#### Step 2 — Replace GPU blur with pre-blurred downscaled image:

```kotlin
// ❌ CURRENT — loads image then applies GPU blur (expensive, holds GPU texture)
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(song.thumbnailUrl)
        .transformations(BlurTransformation(radius = 25f)) // GPU texture = large
        .memoryCacheKey("player_blur_${song.videoId}")
        .size(1080, 1080) // Blurring at 1080px is extremely expensive
        .build(),
    ...
)

// ✅ FIX — request tiny image (blur is invisible at small sizes) + scale up
// A 50×50 image scaled to fill the screen IS the blur effect — costs ~10KB GPU
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data(song.thumbnailUrl)
        .size(50, 50)              // Tiny! Scaling artifacts = blur effect
        .memoryCacheKey("player_blur_${song.videoId}")
        .diskCacheKey("player_blur_${song.videoId}")
        .build(),
    contentDescription = null,
    modifier = Modifier.fillMaxSize(),
    contentScale = ContentScale.Crop,  // Scaling creates natural blur
    alpha = 0.4f                       // Dim it for the overlay effect
)
```

#### Step 3 — If using `Modifier.blur()` anywhere:

```kotlin
// ❌ EXPENSIVE — Modifier.blur() allocates a RenderEffect on GPU
Box(
    modifier = Modifier
        .fillMaxSize()
        .blur(radius = 20.dp)  // GPU texture = massive memory
) {
    Image(...)
}

// ✅ REPLACE with downscaled image approach above
// OR use a semi-transparent overlay instead of blur:
Box(modifier = Modifier.fillMaxSize()) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(song.thumbnailUrl)
            .size(50, 50) // Tiny = effectively blurred when scaled
            .build(),
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    )
    // Dark scrim overlay — achieves same visual effect as blur, zero GPU cost
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
    )
}
```

#### Step 4 — In Thumbnail.kt, verify player_blur_ image size:

```kotlin
// The Phase 2 fix set player_blur_ to 1080px — this is WRONG for a blur image
// Change it:

// ❌ Phase 2 result (still expensive for blur):
.size(1080, 1080)
.memoryCacheKey("player_blur_${videoId}")

// ✅ Correct for blur background (50px scaled = GPU-efficient blur):
.size(50, 50)
.memoryCacheKey("player_blur_${videoId}")
.diskCacheKey("player_blur_${videoId}")
```

---

### FIX #2 — THREAD PROLIFERATION: Bound Coroutine Dispatchers
**Target: Reduce threads from 104 idle / 125 active → <60 / <70**

#### Step 1 — Find unbounded coroutine usage:
Search for ALL occurrences of:
- `Dispatchers.IO` used directly without a custom dispatcher
- `viewModelScope.launch { }` without explicit dispatcher
- `CoroutineScope(Dispatchers.IO)` anywhere (these are never cancelled!)

#### Step 2 — Create bounded dispatchers module:

```kotlin
// di/DispatcherModule.kt — CREATE THIS FILE

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher =
        // ✅ Bounded — max 8 threads for IO instead of unlimited
        Executors.newFixedThreadPool(8).asCoroutineDispatcher()

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher =
        // ✅ Bounded — CPU core count threads
        Dispatchers.Default

    @Provides
    @Singleton
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher =
        Dispatchers.Main.immediate
}

// Qualifier annotations:
@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier @Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
```

#### Step 3 — Fix ViewModel coroutines:

```kotlin
// ❌ BEFORE — unbounded, creates new threads freely
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { // ← unbounded IO pool
            repository.fetchHomeData()
        }
    }
}

// ✅ AFTER — bounded, predictable thread count
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    fun refresh() {
        viewModelScope.launch(ioDispatcher) { // ← bounded to 8 threads
            repository.fetchHomeData()
        }
    }
}
```

#### Step 4 — Kill any rogue CoroutineScope:

```kotlin
// ❌ FIND AND ELIMINATE — never cancelled, leaks threads
val scope = CoroutineScope(Dispatchers.IO)
scope.launch { ... }

// ✅ USE instead — tied to ViewModel lifecycle
viewModelScope.launch(ioDispatcher) { ... }

// ✅ OR for Application-level scope — use a properly managed one:
// In App.kt:
val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
// This is the one in App.kt from Phase 1 — ensure it's the ONLY one
```

---

### FIX #3 — JAVA HEAP: Eliminate List Copy Allocations on Refresh
**Target: Reduce heap growth from +38 MB → <+10 MB on refresh**

#### Step 1 — Find list transformation patterns in ViewModels:

```kotlin
// ❌ BEFORE — creates new list copy on every emission
class HomeViewModel : ViewModel() {
    val songs = repository.getSongs()
        .map { songs ->
            songs.sortedBy { it.title }          // New list copy
                .filter { it.isAvailable }        // Another new list copy
                .map { it.toUiModel() }           // Another new list copy
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

// ✅ AFTER — single transformation, minimize allocations
class HomeViewModel : ViewModel() {
    val songs = repository.getSongs()
        .map { songs ->
            // Single pass — no intermediate collections
            songs.mapNotNullTo(ArrayList(songs.size)) { song ->
                if (song.isAvailable) song.toUiModel() else null
            }.apply { sortBy { it.title } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
```

#### Step 2 — Fix SharingStarted strategy:

```kotlin
// ❌ BEFORE — keeps upstream active forever (memory + CPU waste)
.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

// ✅ AFTER — stops upstream 5 seconds after last subscriber (correct for UI)
.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
    initialValue = emptyList()
)
```

#### Step 3 — Fix refresh causing new collection allocations:

```kotlin
// ❌ BEFORE — refresh triggers new List allocation every time
fun refresh() {
    viewModelScope.launch {
        val newSongs = repository.fetchSongs() // Allocates new ArrayList
        _songs.value = newSongs               // Old list GC'd → GC event!
    }
}

// ✅ AFTER — use diffing to minimize allocations
fun refresh() {
    viewModelScope.launch(ioDispatcher) {
        repository.getSongsFlow()  // Flow-based — Room/cache handles diffing
            .collect { songs ->
                _songs.value = songs
            }
    }
}
// Better: use Room's Flow directly — it only emits on actual DB changes
// No polling, no allocation, no GC pressure
```

---

### FIX #4 — IDLE CPU 10%: Stop Background Work When Idle
**Target: Reduce idle CPU from 10% → <2%**

#### Step 1 — Find what's running at idle:
The pink dots in Image 1 (before the main CPU spike) suggest periodic callbacks.
Search for:
- `Timer` or `TimerTask` usage
- `Handler.postDelayed` loops
- `delay()` in infinite loops
- `TickerFlow` or similar

```kotlin
// ❌ COMMON OFFENDER — polling loop that never stops
fun startPositionUpdater() {
    viewModelScope.launch {
        while (true) {
            _position.value = player.currentPosition
            delay(250) // ← Updates 4×/sec even when paused/idle!
        }
    }
}

// ✅ FIX — only poll when actually playing
fun startPositionUpdater() {
    viewModelScope.launch {
        while (true) {
            if (player.isPlaying) {  // ← Only update when playing
                _position.value = player.currentPosition
                delay(250)
            } else {
                delay(1000) // ← Sleep longer when paused
            }
        }
    }
}

// ✅ EVEN BETTER — event-driven, zero polling
// Use MediaController.Listener or ExoPlayer.Listener instead of polling
player.addListener(object : Player.Listener {
    override fun onPositionDiscontinuity(...) {
        _position.value = player.currentPosition
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }
})
```

#### Step 2 — Find and fix refresh polling:

```kotlin
// ❌ IF THIS EXISTS — auto-refresh on a timer
init {
    viewModelScope.launch {
        while (true) {
            fetchHomeData()
            delay(30_000) // Fetches every 30 seconds in background
        }
    }
}

// ✅ FIX — only refresh on user action or app foreground
init {
    loadHomeData() // Load once on creation
    // Let user pull-to-refresh manually
}

// Or use lifecycle-aware refresh:
fun onResume() {
    val timeSinceLastFetch = System.currentTimeMillis() - lastFetchTime
    if (timeSinceLastFetch > 10 * 60 * 1000) { // Only if >10 min stale
        loadHomeData()
    }
}
```

---

### FIX #5 — GRAPHICS LAYER OPTIMIZATION
**Reduce unnecessary GPU layer allocations in Compose**

```kotlin
// ❌ AVOID — graphicsLayer on every item in a list
items(songs, key = { it.id }) { song ->
    SongItem(
        modifier = Modifier.graphicsLayer {  // GPU layer per item!
            shadowElevation = 4f
            shape = RoundedCornerShape(8.dp)
            clip = true
        }
    )
}

// ✅ USE — shadow via Surface (single GPU layer for the list, not per item)
items(songs, key = { it.id }) { song ->
    Surface(
        tonalElevation = 1.dp,  // Much cheaper than graphicsLayer
        shape = RoundedCornerShape(8.dp)
    ) {
        SongItem(song)
    }
}

// ❌ AVOID — animateContentSize on every list item
SongItem(
    modifier = Modifier.animateContentSize() // GPU layer per item!
)

// ✅ ONLY use animateContentSize on expandable items that actually animate
```

---

### FIX #6 — MEMORY: Reduce "Others" Category (95 MB)
**Investigate ExoPlayer buffer sizes**

The 95 MB "Others" strongly suggests ExoPlayer is holding large audio/video buffers.

```kotlin
// Find ExoPlayer initialization — likely in MusicService.kt or PlayerManager.kt
// Search for: ExoPlayer.Builder, DefaultLoadControl, or MediaPlayer setup

// ❌ DEFAULT — ExoPlayer allocates large buffers by default
val player = ExoPlayer.Builder(context).build()

// ✅ OPTIMIZE — reduce buffer sizes for a music streaming app
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,  // 50s min buffer
        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,  // 50s max buffer
        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,  // 2.5s to start
        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
    )
    // ✅ For music streaming — we don't need video buffers
    .setTargetBufferBytes(
        8 * 1024 * 1024  // 8 MB instead of default 16+ MB
    )
    .setPrioritizeTimeOverSizeThresholds(true)
    .build()

val player = ExoPlayer.Builder(context)
    .setLoadControl(loadControl)
    .build()
```

---

## 📊 EXPECTED PROFILER RESULTS AFTER PHASE 3

Run the profiler again after implementing all fixes above.
Compare against these targets:

| Metric | Before Phase 3 | Target After Phase 3 |
|--------|---------------|----------------------|
| App CPU (idle) | 10% | <2% |
| Total Memory (idle) | 294 MB | <180 MB |
| Graphics/GPU Memory | 100 MB | <45 MB |
| Java Heap (idle) | 28.8 MB | <25 MB |
| Java Heap (after scroll) | 67 MB | <35 MB |
| Threads (idle) | 104 | <65 |
| Threads (active) | 125 | <75 |
| GC Events during scroll | 3 | 0–1 |
| "Others" Memory | 95 MB | <50 MB |

---

## 🗂️ FILE MODIFICATION ORDER (Ranked by Impact)

### Round 1 — GPU Memory (Most Impactful):
1. `Thumbnail.kt` — Change `player_blur_` image size from 1080px → **50px**
2. `MiniPlayer.kt` — Change `mini_blur_` image size from 144px → **50px**
3. Search entire project for `BlurTransformation`, `Modifier.blur(`, `RenderEffect` — remove/replace all
4. Replace any GPU blur with downscaled image + dark scrim overlay pattern

### Round 2 — Thread Control:
5. Create `di/DispatcherModule.kt` with bounded `@IoDispatcher`
6. Inject `@IoDispatcher` into all ViewModels that use `Dispatchers.IO`
7. Find and kill any rogue `CoroutineScope(Dispatchers.IO)` instantiations

### Round 3 — Idle CPU:
8. Find position polling loop in PlayerViewModel / PlayerConnection — add `isPlaying` guard
9. Find any auto-refresh loops — convert to on-demand

### Round 4 — Heap / GC:
10. Change all `.stateIn(... SharingStarted.Eagerly/Lazily ...)` → `WhileSubscribed(5_000)`
11. Reduce list transformation allocations in HomeViewModel, LibraryViewModel

### Round 5 — ExoPlayer Buffers:
12. Find ExoPlayer initialization — apply `DefaultLoadControl` with reduced buffer sizes

### Round 6 — Previously planned (still needed):
13. State flow splitting (PlayerViewModel position isolation)
14. Skeleton loaders (HomeScreen, LibraryScreen)
15. `collectAsState()` → `collectAsStateWithLifecycle()` global sweep
16. `remember(key)` antipattern sweep

---

## ⚡ VERIFICATION — RUN PROFILER AGAIN AFTER EACH ROUND

Do NOT run all rounds at once. After Round 1 (GPU fix), re-open the profiler.
The Graphics memory bar should drop visibly. If it does not drop significantly,
there is another blur source that was missed — find it before proceeding.

**Attach updated profiler screenshots after Round 1 and Round 2 for further analysis.**
