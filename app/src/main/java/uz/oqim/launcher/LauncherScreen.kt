package uz.oqim.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.AlarmClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.exp

private val shadowed = TextStyle(
    color = Color.White,
    shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 8f),
)
private val panelColor = Color(0xF00E1215)

private enum class Panel { None, Drawer, Picker }

@Composable
fun LauncherScreen(vm: LauncherViewModel, onSetDefault: () -> Unit) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val isDefaultHome by vm.isDefaultHome.collectAsStateWithLifecycle()
    val accent = vm.accent
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val drawerList = rememberLazyListState()
    var panel by remember { mutableStateOf(Panel.None) }
    var homeEpoch by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        vm.homePressed.collect {
            focus.clearFocus()
            panel = Panel.None
            homeEpoch++
        }
    }

    BackHandler(enabled = panel != Panel.None || state.query.isNotEmpty()) {
        if (state.query.isNotEmpty()) vm.setQuery("") else panel = Panel.None
        focus.clearFocus()
    }

    // Drawer list: title at 0, then letter header + apps per section.
    val letterIndex = remember(state.sections) {
        val map = LinkedHashMap<String, Int>()
        var i = 1
        state.sections.forEach { s ->
            map[s.letter] = i
            i += 1 + s.apps.size
        }
        map
    }
    val openDrawerAt: (String) -> Unit = { letter ->
        focus.clearFocus()
        panel = Panel.Drawer
        letterIndex[letter]?.let { scope.launch { drawerList.scrollToItem(it) } }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.55f)))),
    ) {
        // ---------- Home ----------
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -120f) {
                                focus.clearFocus(); panel = Panel.Drawer
                            } else if (total > 120f) {
                                vm.expandNotifications()
                            }
                        },
                    ) { _, dy -> total += dy }
                },
        ) {
            if (!isDefaultHome) DefaultHomeBanner(onSetDefault)
            if (state.query.isBlank()) {
                ClockHeader(accent)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    FavoritesBlock(
                        state = state,
                        vm = vm,
                        accent = accent,
                        epoch = homeEpoch,
                        onEdit = { panel = Panel.Picker },
                        modifier = Modifier.align(Alignment.BottomStart).padding(end = 44.dp),
                    )
                }
            } else {
                Box(Modifier.weight(1f)) { SearchResults(state, vm) }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchBar(
                    query = state.query,
                    placeholder = "Qidirish",
                    onChange = vm::setQuery,
                    onGo = {
                        val first = state.results.firstOrNull()
                        if (first != null) vm.launch(first) else if (state.query.isNotBlank()) vm.webSearch(state.query.trim())
                        focus.clearFocus()
                    },
                    modifier = Modifier.weight(1f),
                )
                AllAppsButton(accent) { focus.clearFocus(); panel = Panel.Drawer }
            }
        }

        // ---------- All apps ----------
        AnimatedVisibility(
            visible = panel == Panel.Drawer,
            enter = fadeIn(tween(220)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 8 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 8 },
        ) {
            AppDrawer(state, vm, drawerList, accent)
        }

        if (panel != Panel.Picker && state.query.isBlank()) {
            AlphabetBar(
                letters = state.sections.map { it.letter },
                accent = accent,
                onSelect = openDrawerAt,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .systemBarsPadding()
                    .fillMaxHeight(0.72f),
            )
        }

        // ---------- Favorites picker ----------
        AnimatedVisibility(
            visible = panel == Panel.Picker,
            enter = fadeIn(tween(220)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it / 6 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 6 },
        ) {
            FavoritePicker(state, vm, accent) { panel = Panel.None; homeEpoch++ }
        }
    }
}

@Composable
private fun DefaultHomeBanner(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text("Oqim hali asosiy ekran emas", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            "Bosing va Oqim'ni standart bosh ekran sifatida tanlang. Shunda Home va Orqaga tugmalari to'g'ri ishlaydi.",
            color = Color.Black.copy(alpha = 0.75f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun rememberBatteryLevel(): Int {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(-1) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val s = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                if (l >= 0 && s > 0) level = l * 100 / s
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return level
}

@Composable
private fun ClockHeader(accent: Color) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    val battery = rememberBatteryLevel()
    val time = remember(now.hour, now.minute) { now.format(DateTimeFormatter.ofPattern("HH:mm")) }
    val date = remember(now.toLocalDate()) {
        now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
    Column(Modifier.fillMaxWidth().padding(start = 28.dp, top = 40.dp, bottom = 8.dp)) {
        Text(
            time,
            style = shadowed.copy(fontSize = 84.sp, fontWeight = FontWeight.Thin, letterSpacing = (-2).sp),
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                date,
                style = shadowed.copy(fontSize = 17.sp, color = Color.White.copy(alpha = 0.9f)),
                modifier = Modifier.clickable {
                    runCatching {
                        context.startActivity(
                            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
            if (battery >= 0) {
                Box(Modifier.padding(horizontal = 10.dp).size(4.dp).background(accent, CircleShape))
                Text("$battery%", style = shadowed.copy(fontSize = 17.sp, color = Color.White.copy(alpha = 0.9f)))
            }
        }
    }
}

@Composable
private fun FavoritesBlock(
    state: UiState,
    vm: LauncherViewModel,
    accent: Color,
    epoch: Int,
    onEdit: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
        if (state.loaded && state.favorites.isEmpty()) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .clickable(onClick = onEdit)
                    .padding(20.dp),
            ) {
                Text("+  Ilovalarni tanlash", style = shadowed.copy(fontSize = 20.sp, color = accent, fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bosh ekranda turadigan ilovalarni belgilang. Qolganlari pastdagi tugma yoki o'ngdagi harflar orqali ochiladi.",
                    style = shadowed.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f)),
                )
            }
            return@Column
        }
        state.favorites.forEachIndexed { i, app ->
            key(app.key, epoch) {
                val anim = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    delay(i * 35L)
                    anim.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
                }
                Box(
                    Modifier.graphicsLayer {
                        alpha = anim.value
                        translationX = (1f - anim.value) * 48.dp.toPx()
                    },
                ) {
                    AppRow(app, big = true, isFavorite = true, isHidden = false, vm = vm, onEditFavorites = onEdit)
                }
            }
        }
    }
}

@Composable
private fun AllAppsButton(accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(start = 8.dp)
            .size(50.dp)
            .background(Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val step = size.width / 2f
            for (r in 0..2) for (c in 0..2) {
                drawCircle(
                    color = if (r == 1 && c == 1) accent else Color.White,
                    radius = 2.2.dp.toPx(),
                    center = Offset(c * step, r * step),
                )
            }
        }
    }
}

@Composable
private fun AppDrawer(state: UiState, vm: LauncherViewModel, listState: LazyListState, accent: Color) {
    var showHidden by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(panelColor)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(end = 44.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "drawer-title") {
                Row(Modifier.padding(start = 24.dp, top = 28.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Barcha ilovalar", style = shadowed.copy(fontSize = 30.sp, fontWeight = FontWeight.Light))
                    Text(
                        "  ${state.sections.sumOf { it.apps.size }}",
                        style = shadowed.copy(fontSize = 16.sp, color = accent),
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
            }
            state.sections.forEach { section ->
                item(key = "l-" + section.letter) { LetterHeader(section.letter, accent) }
                items(section.apps, key = { "a-" + it.key }) { app ->
                    AppRow(app, big = false, isFavorite = app.key in state.favoriteKeys, isHidden = false, vm = vm)
                }
            }
            if (state.hiddenApps.isNotEmpty()) {
                item(key = "hidden-toggle") {
                    Text(
                        (if (showHidden) "Yashirinlarni yopish" else "Yashirin ilovalar") + " (${state.hiddenApps.size})",
                        style = shadowed.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHidden = !showHidden }
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                    )
                }
                if (showHidden) {
                    items(state.hiddenApps, key = { "h-" + it.key }) { app ->
                        AppRow(app, big = false, isFavorite = false, isHidden = true, vm = vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritePicker(state: UiState, vm: LauncherViewModel, accent: Color, onDone: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    val all = remember(state.sections) { state.sections.flatMap { it.apps } }
    val shown = remember(all, filter) {
        val f = filter.trim()
        if (f.isEmpty()) all else all.filter { it.label.contains(f, ignoreCase = true) }
    }
    val order = remember(state.favorites) { state.favorites.withIndex().associate { it.value.key to it.index + 1 } }
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize().background(panelColor).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Bosh ekran ilovalari", style = shadowed.copy(fontSize = 24.sp, fontWeight = FontWeight.Light), modifier = Modifier.weight(1f))
            Text(
                "Tayyor",
                color = Color.Black,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(accent, RoundedCornerShape(20.dp))
                    .clickable { focus.clearFocus(); onDone() }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            )
        }
        Text(
            "Tanlangan: ${order.size}. Tartibni bosh ekranda ilovani uzoq bosib o'zgartirasiz.",
            style = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SearchBar(
            query = filter,
            placeholder = "Ilova nomi",
            onChange = { filter = it },
            onGo = { focus.clearFocus() },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(shown, key = { "p-" + it.key }) { app ->
                val n = order[app.key]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.toggleFavorite(app) }
                        .padding(horizontal = 24.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(16.dp))
                    Text(
                        app.label,
                        style = TextStyle(color = Color.White, fontSize = 17.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .size(26.dp)
                            .then(
                                if (n != null) Modifier.background(accent, CircleShape)
                                else Modifier.border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (n != null) Text("$n", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterHeader(letter: String, accent: Color) {
    Text(
        letter,
        style = shadowed.copy(fontSize = 15.sp, color = accent, fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    big: Boolean,
    isFavorite: Boolean,
    isHidden: Boolean,
    vm: LauncherViewModel,
    onEditFavorites: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { vm.launch(app) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(horizontal = 24.dp, vertical = if (big) 9.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(if (big) 44.dp else 34.dp))
            Spacer(Modifier.width(if (big) 18.dp else 16.dp))
            Text(
                app.label,
                style = shadowed.copy(fontSize = if (big) 24.sp else 17.sp, fontWeight = if (big) FontWeight.Light else FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!isHidden) {
                MenuItem(if (isFavorite) "Bosh ekrandan olib tashlash" else "Bosh ekranga qo'shish") {
                    menu = false; vm.toggleFavorite(app)
                }
            }
            if (big) {
                MenuItem("Yuqoriga") { menu = false; vm.moveFavorite(app, -1) }
                MenuItem("Pastga") { menu = false; vm.moveFavorite(app, 1) }
            }
            if (onEditFavorites != null) MenuItem("Ro'yxatni tahrirlash") { menu = false; onEditFavorites() }
            MenuItem(if (isHidden) "Ko'rsatish" else "Yashirish") { menu = false; vm.toggleHidden(app) }
            MenuItem("Ilova haqida") { menu = false; vm.openAppInfo(app) }
            MenuItem("O'chirish") { menu = false; vm.uninstall(app) }
        }
    }
}

@Composable
private fun MenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick)
}

@Composable
private fun AlphabetBar(letters: List<String>, accent: Color, onSelect: (String) -> Unit, modifier: Modifier) {
    if (letters.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val select by rememberUpdatedState(onSelect)
    var active by remember { mutableStateOf<String?>(null) }
    var touchY by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableIntStateOf(1) }
    val wave by animateFloatAsState(if (active != null) 1f else 0f, tween(180), label = "wave")

    fun pick(y: Float) {
        touchY = y.coerceIn(0f, heightPx.toFloat())
        val i = (y / heightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[i]
        if (letter != active) {
            active = letter
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            select(letter)
        }
    }

    Box(modifier.width(40.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        pick(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()
                            pick(change.position.y)
                        }
                        active = null
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val slot = heightPx.toFloat() / letters.size
            letters.forEachIndexed { i, l ->
                val center = (i + 0.5f) * slot
                val d = (center - touchY) / (slot * 2.6f)
                val f = exp(-d * d) * wave
                val isActive = l == active
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        l,
                        style = shadowed.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) accent else Color.White.copy(alpha = 0.65f + 0.35f * f),
                        ),
                        modifier = Modifier.graphicsLayer {
                            translationX = -f * 52.dp.toPx()
                            scaleX = 1f + f
                            scaleY = 1f + f
                        },
                    )
                }
            }
        }
        active?.let { letter ->
            Box(
                modifier = Modifier
                    .offset { IntOffset((-124).dp.roundToPx(), touchY.toInt() - 34.dp.roundToPx()) }
                    .size(68.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
private fun SearchResults(state: UiState, vm: LauncherViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true, // best match sits right above the search bar
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(state.results, key = { "r-" + it.key }) { app ->
            AppRow(
                app,
                big = false,
                isFavorite = app.key in state.favoriteKeys,
                isHidden = app.key in state.hiddenKeys,
                vm = vm,
            )
        }
        item(key = "web") {
            Text(
                "Internetda qidirish: \"${state.query.trim()}\"",
                style = shadowed.copy(fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.webSearch(state.query.trim()) }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(25.dp))
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (query.isEmpty()) {
            Text(placeholder, style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp))
        }
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    }
}
