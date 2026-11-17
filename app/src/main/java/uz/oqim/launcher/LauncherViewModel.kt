package uz.oqim.launcher

import android.app.Application
import android.app.SearchManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator

data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
)

data class Section(val letter: String, val apps: List<AppEntry>)

data class UiState(
    val loaded: Boolean = false,
    val favorites: List<AppEntry> = emptyList(),
    val sections: List<Section> = emptyList(),
    val hiddenApps: List<AppEntry> = emptyList(),
    val favoriteKeys: Set<String> = emptySet(),
    val hiddenKeys: Set<String> = emptySet(),
    val query: String = "",
    val results: List<AppEntry> = emptyList(),
)

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val context = app
    private val launcherApps = app.getSystemService(LauncherApps::class.java)
    private val userManager = app.getSystemService(UserManager::class.java)
    private val prefs = Prefs(app)
    private val collator = Collator.getInstance()

    private val apps = MutableStateFlow<List<AppEntry>?>(null)
    private val favorites = MutableStateFlow(prefs.favorites)
    private val hidden = MutableStateFlow(prefs.hidden)
    private val query = MutableStateFlow("")

    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePressed: SharedFlow<Unit> = _homePressed

    val uiState: StateFlow<UiState> =
        combine(apps, favorites, hidden, query) { list, favs, hid, q -> buildState(list, favs, hid, q) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    private var reloadJob: Job? = null

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(callback)
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(Dispatchers.Default) {
            val iconPx = (48 * context.resources.displayMetrics.density).toInt()
            val list = userManager.userProfiles.flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                launcherApps.getActivityList(null, user)
                    .filter { it.applicationInfo.packageName != context.packageName }
                    .map { info ->
                        AppEntry(
                            key = "${info.componentName.flattenToString()}#$serial",
                            label = info.label.toString(),
                            packageName = info.applicationInfo.packageName,
                            component = info.componentName,
                            user = user,
                            icon = info.getBadgedIcon(0).toBitmap(iconPx, iconPx).asImageBitmap(),
                        )
                    }
            }
            apps.value = list.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }

    private fun buildState(list: List<AppEntry>?, favs: List<String>, hid: Set<String>, q: String): UiState {
        if (list == null) return UiState(query = q)
        val byKey = list.associateBy { it.key }
        val visible = list.filter { it.key !in hid }
        val sections = visible
            .groupBy { letterOf(it.label) }
            .map { (letter, apps) -> Section(letter, apps) }
            .sortedWith { a, b ->
                when {
                    a.letter == "#" -> -1
                    b.letter == "#" -> 1
                    else -> collator.compare(a.letter, b.letter)
                }
            }
        val trimmed = q.trim()
        val results = if (trimmed.isEmpty()) emptyList() else {
            list.filter { matches(it.label, trimmed) }
                .sortedBy { if (it.label.startsWith(trimmed, ignoreCase = true)) 0 else 1 }
        }
        return UiState(
            loaded = true,
            favorites = favs.mapNotNull { byKey[it] },
            sections = sections,
            hiddenApps = list.filter { it.key in hid },
            favoriteKeys = favs.toSet(),
            hiddenKeys = hid,
            query = q,
            results = results,
        )
    }

    private fun matches(label: String, q: String): Boolean {
        if (label.contains(q, ignoreCase = true)) return true
        // "gm" matches "Google Maps"
        val initials = label.split(' ', '-', '_').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
        return q.length > 1 && initials.startsWith(q, ignoreCase = true)
    }

    private fun letterOf(label: String): String {
        val c = label.firstOrNull { it.isLetterOrDigit() } ?: return "#"
        return if (c.isLetter()) c.uppercaseChar().toString() else "#"
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun onHomePressed() {
        query.value = ""
        _homePressed.tryEmit(Unit)
    }

    fun launch(app: AppEntry) {
        try {
            launcherApps.startMainActivity(app.component, app.user, null, null)
        } catch (_: Exception) {
            reload()
        }
        query.value = ""
    }

    fun openAppInfo(app: AppEntry) {
        runCatching { launcherApps.startAppDetailsActivity(app.component, app.user, null, null) }
    }

    fun uninstall(app: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun webSearch(q: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, q)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { runCatching { context.startActivity(fallback) } }
        query.value = ""
    }

    fun toggleFavorite(app: AppEntry) {
        val current = favorites.value
        val updated = if (app.key in current) current - app.key else current + app.key
        favorites.value = updated
        prefs.favorites = updated
    }

    fun moveFavorite(app: AppEntry, delta: Int) {
        val list = favorites.value.toMutableList()
        val i = list.indexOf(app.key)
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        favorites.value = list
        prefs.favorites = list
    }

    fun toggleHidden(app: AppEntry) {
        val current = hidden.value
        val updated = if (app.key in current) current - app.key else current + app.key
        hidden.value = updated
        prefs.hidden = updated
        if (app.key in updated && app.key in favorites.value) toggleFavorite(app)
    }
}
