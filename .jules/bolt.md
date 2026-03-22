## 2024-05-19 - Initial Analysis
**Learning:** This is a Jetpack Compose based Android music player app.
**Action:** Look for Jetpack Compose performance optimizations like `remember`, `key`, `derivedStateOf`, minimizing recompositions, etc.

## 2024-05-19 - Initial Analysis (Queue.kt)
**Learning:** `Queue.kt` uses `LaunchedEffect(mutableQueueWindows)` to scroll to the current item whenever the list of items changes. This could be inefficient if `mutableQueueWindows` changes frequently but `currentWindowIndex` hasn't changed. Actually, checking if scrolling to `currentWindowIndex` only when `currentWindowIndex` changes would be better. But maybe it's fine.
**Action:** Investigate `Queue.kt` performance, especially `mutableQueueWindows`.

## 2024-05-19 - Initial Analysis (LazyListState and derivedStateOf)
**Learning:** Many screens use `derivedStateOf` heavily with `lazyListState.firstVisibleItemScrollOffset` to calculate parallax, alpha gradients, etc. For example in `AlbumScreen.kt`, `TopPlaylistScreen.kt`, `AutoPlaylistScreen.kt`, `LocalPlaylistScreen.kt`, `OnlinePlaylistScreen.kt`, `CachePlaylistScreen.kt`. While `derivedStateOf` is good, reading `firstVisibleItemScrollOffset` frequently causes recompositions *only* where the derived state is read.
Wait, let's check where `gradientAlpha`, `headerParallax` are used. If they are used in a Modifier like `Modifier.alpha(gradientAlpha)` directly, it will cause the composable to recompose on every scroll frame. The correct way is `Modifier.graphicsLayer { alpha = gradientAlpha }`.
**Action:** Investigate if `gradientAlpha` and `headerParallax` are used directly in Modifiers or inside `graphicsLayer`.

## 2024-05-19 - Performance Anti-pattern: Reading derived state in `drawBehind`
**Learning:** `gradientAlpha` is defined via `derivedStateOf` based on `lazyListState.firstVisibleItemScrollOffset`. It's read inside the `drawBehind` block of `Modifier.drawBehind { ... }` (and inside the `if` condition). Because `gradientAlpha` is read in the composition phase (the `if` condition `if (!disableBlur && gradientColors.isNotEmpty() && gradientAlpha > 0f)`), it will cause the *entire `AlbumScreen` Box* (or at least the composable containing it) to recompose on every single scroll frame.

Wait, if it's read in the `if` condition, it causes recomposition. If it was only read in `drawBehind`, it would only cause redrawing (which is better). But here it's read in the `if` condition, which is a composition phase read. Furthermore, `Modifier.graphicsLayer { alpha = gradientAlpha }` would skip drawing entirely if alpha=0, and just apply alpha to the layer without causing recomposition or even redrawing the mesh gradient.

Currently, it recreates the `Brush` objects in `drawBehind` with `gradientColors[0].copy(alpha = gradientAlpha * X)`.
**Action:** Replace `gradientAlpha` in the `if` condition and `drawBehind` with a `graphicsLayer { alpha = gradientAlpha }` to avoid composition-phase reads and unnecessary redrawing. Wait, is `gradientAlpha` strictly 0 to 1? Yes. If it's used as `copy(alpha = ... * 0.75f)`, we can just apply `alpha = gradientAlpha` to the `graphicsLayer`, and use static alphas (0.75f, 0.4f, etc.) in the `drawBehind` brushes. This will prevent recomposition *and* prevent recreating `Brush` objects on every frame.

## 2024-05-19 - Wait, `gradientAlpha` is also read inside the `if` block condition.
**Learning:** Checking `gradientAlpha > 0f` forces a composition-phase read. We should change this to `Modifier.graphicsLayer { alpha = gradientAlpha }` and *remove* the `&& gradientAlpha > 0f` check from the `if` condition to prevent recomposition on every scroll.
If `gradientAlpha == 0f`, `graphicsLayer` will just skip drawing, which is very fast.
Let's see the performance impact.

## 2024-05-19 - Wait, `drawBehind` creates `Brush` on every frame?
**Learning:** `drawBehind` creates new `Brush` objects inside `drawRect(brush = Brush.radialGradient(...))` on every frame (if it were drawing). Because `gradientAlpha` changes with scrolling, even if we move it to the `alpha` parameter, the `colors` list would still be recreated. If we use `graphicsLayer { alpha = gradientAlpha }`, the `drawBehind` block will NOT read `gradientAlpha`. It can just use constant alphas like `0.75f, 0.4f, Color.Transparent`.
Wait, the `drawBehind` block doesn't read any state if `gradientAlpha` is removed. It only reads `gradientColors`. So the `drawBehind` lambda will not be re-executed on scroll, but only when `gradientColors` changes. The only thing changing on scroll is the layer alpha. This is a massive win for scroll performance on those screens, saving dozens of object allocations (`Brush.radialGradient`, `listOf`, `Color.copy`) per frame.
