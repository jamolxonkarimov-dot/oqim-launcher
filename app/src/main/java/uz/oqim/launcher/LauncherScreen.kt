package uz.oqim.launcher

import android.content.Intent
import android.provider.AlarmClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

private val shadowed = TextStyle(
    color = Color.White,
    shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 8f),
)

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var showHidden by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.homePressed.collect {
            focus.clearFocus()
            showHidden = false
            listState.animateScrollToItem(0)
        }
    }

    BackHandler(enabled = state.query.isNotEmpty()) {
        vm.setQuery("")
        focus.clearFocus()
    }

    // Index of each letter header inside the LazyColumn: clock, favorites title, favorites, then sections.
    val letterIndex = remember(state.favorites, state.sections) {
        val map = LinkedHashMap<String, Int>()
        var i = 2 + state.favorites.size
        state.sections.forEach { s ->
            map[s.letter] = i
            i += 1 + s.apps.size
        }
        map
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Black.copy(alpha = 0.45f)))),
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.query.isBlank()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(end = 36.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        item(key = "clock") { ClockHeader() }
                        item(key = "fav-title") {
                            SectionLabel(
                                if (state.favorites.isEmpty() && state.loaded) "Ilovani uzoq bosib sevimlilarga qo'shing" else "Sevimlilar",
                            )
                        }
                        items(state.favorites, key = { "f-" + it.key }) { app ->
                            AppRow(app, big = true, isFavorite = true, isHidden = false, vm = vm)
                        }
                        state.sections.forEach { section ->
                            item(key = "l-" + section.letter) { LetterHeader(section.letter) }
                            items(section.apps, key = { "a-" + it.key }) { app ->
                                AppRow(app, big = false, isFavorite = app.key in state.favoriteKeys, isHidden = false, vm = vm)
                            }
                        }
                        if (state.hiddenApps.isNotEmpty()) {
                            item(key = "hidden-toggle") {
                                Text(
                                    text = (if (showHidden) "Yashirinlarni yopish" else "Yashirin ilovalar") + " (${state.hiddenApps.size})",
                                    style = shadowed.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showHidden = !showHidden }
                                        .padding(horizontal = 24.dp, vertical = 16.dp),
                                )
                            }
                            if (showHidden) {
                                items(state.hiddenApps, key = { "h-" + it.key }) { app ->
                                    AppRow(app, big = false, isFavorite = false, isHidden = true, vm = vm)
                                }
                            }
                        }
                    }
                    AlphabetBar(
                        letters = state.sections.map { it.letter },
                        onSelect = { letter -> letterIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } } },
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 24.dp),
                    )
                } else {
                    SearchResults(state, vm)
                }
            }
            SearchBar(
                query = state.query,
                onChange = vm::setQuery,
                onGo = {
                    val first = state.results.firstOrNull()
                    if (first != null) vm.launch(first) else if (state.query.isNotBlank()) vm.webSearch(state.query.trim())
                    focus.clearFocus()
                },
            )
        }
    }
}

@Composable
private fun ClockHeader() {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000 - System.currentTimeMillis() % 1000)
        }
    }
    val time = remember(now.hour, now.minute) { now.format(DateTimeFormatter.ofPattern("HH:mm")) }
    val date = remember(now.toLocalDate()) {
        now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 48.dp, bottom = 24.dp)) {
        Text(
            time,
            style = shadowed.copy(fontSize = 68.sp, fontWeight = FontWeight.Light),
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
        Text(
            date,
            style = shadowed.copy(fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f)),
            modifier = Modifier.clickable {
                runCatching {
                    context.startActivity(
                        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = shadowed.copy(fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Medium),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun LetterHeader(letter: String) {
    Text(
        letter,
        style = shadowed.copy(fontSize = 14.sp, color = Color.White.copy(alpha = 0.55f), fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(app: AppEntry, big: Boolean, isFavorite: Boolean, isHidden: Boolean, vm: LauncherViewModel) {
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
                .padding(horizontal = 24.dp, vertical = if (big) 10.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(if (big) 42.dp else 34.dp))
            Spacer(Modifier.width(16.dp))
            Text(
                app.label,
                style = shadowed.copy(fontSize = if (big) 22.sp else 17.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (!isHidden) {
                MenuItem(if (isFavorite) "Sevimlilardan olib tashlash" else "Sevimlilarga qo'shish") {
                    menu = false; vm.toggleFavorite(app)
                }
            }
            if (big) {
                MenuItem("Yuqoriga") { menu = false; vm.moveFavorite(app, -1) }
                MenuItem("Pastga") { menu = false; vm.moveFavorite(app, 1) }
            }
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
private fun AlphabetBar(letters: List<String>, onSelect: (String) -> Unit, modifier: Modifier) {
    if (letters.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val select by rememberUpdatedState(onSelect)
    var active by remember { mutableStateOf<String?>(null) }
    var activeY by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableIntStateOf(1) }

    fun pick(y: Float) {
        activeY = y.coerceIn(0f, heightPx.toFloat())
        val i = (y / heightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[i]
        if (letter != active) {
            active = letter
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            select(letter)
        }
    }

    Box(modifier) {
        Column(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight()
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
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { l ->
                val isActive = l == active
                Text(
                    l,
                    style = shadowed.copy(
                        fontSize = if (isActive) 15.sp else 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.65f),
                    ),
                )
            }
        }
        active?.let { letter ->
            Box(
                modifier = Modifier
                    .offset { IntOffset((-88).dp.roundToPx(), activeY.toInt() - 32.dp.roundToPx()) }
                    .size(64.dp)
                    .background(Color.White.copy(alpha = 0.92f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.Black)
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
private fun SearchBar(query: String, onChange: (String) -> Unit, onGo: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (query.isEmpty()) {
            Text("Qidirish", style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp))
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
