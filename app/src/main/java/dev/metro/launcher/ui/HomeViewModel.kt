package dev.metro.launcher.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.metro.launcher.data.AppInfo
import dev.metro.launcher.data.AppRepository
import dev.metro.launcher.data.GridPacker
import dev.metro.launcher.data.HomeLayoutRepository
import dev.metro.launcher.data.HomeTileItem
import dev.metro.launcher.data.IconCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Держит список приложений и конфигурацию плиток экрана, переживая повороты.
 * Обновляется по broadcast'ам установки/удаления.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository(application)
    val layoutRepo = HomeLayoutRepository(application)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    val tiles: StateFlow<List<HomeTileItem>> = layoutRepo.tiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeLayoutRepository.defaultTiles(),
    )

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED &&
                !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            ) {
                IconCache.evict(pkg)
            }
            refresh()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        runCatching {
            application.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _apps.value = repo.loadApps() }
    }

    fun launch(app: AppInfo) = repo.launch(app.packageName)
    fun launchPackage(packageName: String) = repo.launch(packageName)

    fun addTile(tile: HomeTileItem, targetCol: Int? = null, targetRow: Int? = null) {
        viewModelScope.launch { layoutRepo.addTile(tile, targetCol, targetRow) }
    }

    fun removeTile(id: String) {
        viewModelScope.launch { layoutRepo.removeTile(id) }
    }

    fun updateTileSpan(id: String, colSpan: Int, rowSpan: Int) {
        viewModelScope.launch { layoutRepo.updateTileSpan(id, colSpan, rowSpan) }
    }

    fun applyDropDecision(decision: GridPacker.DropDecision) {
        viewModelScope.launch { layoutRepo.applyDropDecision(decision) }
    }

    fun moveTileToPosition(id: String, col: Int, row: Int) {
        viewModelScope.launch { layoutRepo.moveTileToPosition(id, col, row) }
    }

    fun swapTiles(idA: String, idB: String) {
        viewModelScope.launch { layoutRepo.swapTiles(idA, idB) }
    }

    fun moveTileTo(id: String, toIndex: Int) {
        viewModelScope.launch { layoutRepo.moveTileTo(id, toIndex) }
    }

    fun resetTilesToDefault() {
        viewModelScope.launch { layoutRepo.resetToDefault() }
    }

    override fun onCleared() {
        runCatching {
            getApplication<Application>().unregisterReceiver(packageReceiver)
        }
    }
}
