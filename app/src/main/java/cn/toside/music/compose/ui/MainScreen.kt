package cn.toside.music.compose.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cn.toside.music.compose.data.ThemeMode
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.player.PlayerUiState
import cn.toside.music.compose.ui.adaptive.LocalAdaptive
import cn.toside.music.compose.ui.adaptive.ProvideAdaptive
import cn.toside.music.compose.ui.component.AddToListDialog
import cn.toside.music.compose.ui.component.MiniPlayer
import cn.toside.music.compose.ui.component.lxFocusable
import cn.toside.music.compose.ui.leaderboard.LeaderboardScreen
import cn.toside.music.compose.ui.leaderboard.LeaderboardViewModel
import cn.toside.music.compose.ui.mylist.MyListScreen
import cn.toside.music.compose.ui.mylist.MyListViewModel
import cn.toside.music.compose.ui.play.PlayScreen
import cn.toside.music.compose.ui.play.PlayViewModel
import cn.toside.music.compose.ui.search.SearchScreen
import cn.toside.music.compose.ui.search.SearchViewModel
import cn.toside.music.compose.ui.setting.SettingScreen
import cn.toside.music.compose.ui.setting.SettingViewModel
import cn.toside.music.compose.ui.songlist.SongListScreen
import cn.toside.music.compose.ui.songlist.SongListViewModel
import cn.toside.music.compose.ui.theme.LxMusicTheme

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

/** 导航项（播放不在其中：手机/平板经迷你播放条进入，TV 经左侧导航栏顶部按钮进入） */
private val tabs = listOf(
    NavTab("mylist", "收藏", Icons.Default.Favorite),
    NavTab("search", "搜索", Icons.Default.Search),
    NavTab("leaderboard", "排行", Icons.Default.FormatListNumbered),
    NavTab("songlist", "歌单", Icons.AutoMirrored.Filled.QueueMusic),
    NavTab("setting", "设置", Icons.Default.Settings),
)

/** 播放页为独立全屏页面，不显示导航与迷你播放条 */
private val chromelessRoutes = setOf("play")

/** 悬浮迷你播放条占位（避免列表最后一项被遮住） */
private val miniOverlaySpace = 76.dp

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "mylist"

    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
    val leaderboardViewModel: LeaderboardViewModel = viewModel(factory = LeaderboardViewModel.Factory)
    val songListViewModel: SongListViewModel = viewModel(factory = SongListViewModel.Factory)
    val myListViewModel: MyListViewModel = viewModel(factory = MyListViewModel.Factory)
    val playViewModel: PlayViewModel = viewModel(factory = PlayViewModel.Factory)
    val settingViewModel: SettingViewModel = viewModel(factory = SettingViewModel.Factory)

    val playerState by playViewModel.playerRepository.uiState.collectAsStateWithLifecycle()
    val settingState by settingViewModel.uiState.collectAsState()
    val dark = when (settingState.settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // 注入设备形态（含用户显示模式覆盖），TV 端同时放大字号
    ProvideAdaptive(displayMode = settingState.settings.displayMode) {
        // 系统栏：手机/平板正常显示状态栏；TV 全屏沉浸，不预留状态栏区域
        val view = LocalView.current
        val adaptive = LocalAdaptive.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = findActivityWindow(view) ?: return@SideEffect
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
                if (adaptive.isTv) {
                    // TV：沉浸全屏，隐藏状态栏；从设置切回其他模式时恢复
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.statusBars())
                }
            }
        }

        LxMusicTheme(darkTheme = dark) {
            MainScaffold(
                navController = navController,
                currentRoute = currentRoute,
                searchViewModel = searchViewModel,
                leaderboardViewModel = leaderboardViewModel,
                songListViewModel = songListViewModel,
                myListViewModel = myListViewModel,
                playViewModel = playViewModel,
                settingViewModel = settingViewModel,
                playerState = playerState,
                modifier = modifier,
            )
        }
    }
}

private fun findActivityWindow(view: android.view.View): android.view.Window? {
    var ctx = view.context
    while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
    return (ctx as? Activity)?.window
}

@Composable
private fun MainScaffold(
    navController: NavHostController,
    currentRoute: String,
    searchViewModel: SearchViewModel,
    leaderboardViewModel: LeaderboardViewModel,
    songListViewModel: SongListViewModel,
    myListViewModel: MyListViewModel,
    playViewModel: PlayViewModel,
    settingViewModel: SettingViewModel,
    playerState: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    // “收藏到歌单”弹窗目标（搜索/排行/歌单行的收藏按钮触发）
    var addToListTarget by remember { mutableStateOf<MusicInfo?>(null) }
    val myListsState by myListViewModel.uiState.collectAsState()

    val adaptive = LocalAdaptive.current
    // 播放页为独立全屏页面：不显示导航与迷你播放条
    val showChrome = currentRoute !in chromelessRoutes
    val isSideNav = !adaptive.isPhone

    // TV 遥控器落焦点起点：进入带导航栏的页面时把焦点放到当前导航项
    val navFocus = remember { FocusRequester() }
    LaunchedEffect(currentRoute, adaptive.deviceType) {
        if (adaptive.isTv && showChrome) runCatching { navFocus.requestFocus() }
    }

    if (adaptive.isPhone) {
        // ── 手机：底部导航栏 + 悬浮迷你播放条（与内容重叠，位于导航栏上方） ──
        Scaffold(
            modifier = modifier.fillMaxSize(),
            bottomBar = {
                if (showChrome) {
                    BottomNavBar(
                        currentRoute = currentRoute,
                        navFocus = navFocus,
                        onSelect = { navigateTab(navController, it) },
                    )
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "mylist",
                    modifier = Modifier
                        .fillMaxSize()
                        // 悬浮迷你条占位，防止列表最后一项被遮住
                        .then(if (showChrome && playerState.current != null) Modifier.padding(bottom = miniOverlaySpace) else Modifier),
                ) {
                    composable("search") {
                        val state by searchViewModel.uiState.collectAsState()
                        SearchScreen(
                            state = state,
                            onKeywordChange = searchViewModel::setKeyword,
                            onSearch = searchViewModel::search,
                            onSourceChange = searchViewModel::setSource,
                            onPlay = searchViewModel::playAll,
                            onLoadMore = searchViewModel::loadMore,
                            onAddToList = { addToListTarget = it },
                        )
                    }
                    composable("leaderboard") {
                        val state by leaderboardViewModel.uiState.collectAsState()
                        LeaderboardScreen(
                            state = state,
                            onSourceChange = leaderboardViewModel::setSource,
                            onBoardSelect = leaderboardViewModel::selectBoard,
                            onPlay = leaderboardViewModel::playAt,
                            onLoadMore = leaderboardViewModel::loadMore,
                            onAddToList = { addToListTarget = it },
                        )
                    }
                    composable("songlist") {
                        val state by songListViewModel.uiState.collectAsState()
                        SongListScreen(
                            state = state,
                            onSourceChange = songListViewModel::setSource,
                            onSortChange = songListViewModel::setSort,
                            onTagChange = songListViewModel::setTag,
                            onOpenDetail = songListViewModel::openDetail,
                            onCloseDetail = songListViewModel::closeDetail,
                            onPlay = songListViewModel::playAt,
                            onLoadMoreLists = songListViewModel::loadMoreLists,
                            onLoadMoreDetail = songListViewModel::loadMoreDetail,
                            onAddToList = { addToListTarget = it },
                        )
                    }
                    composable("mylist") {
                        val state by myListViewModel.uiState.collectAsState()
                        MyListScreen(
                            state = state,
                            onSelectList = myListViewModel::selectList,
                            onCreateList = myListViewModel::createList,
                            onRenameList = myListViewModel::renameList,
                            onRemoveList = myListViewModel::removeList,
                            onPlay = myListViewModel::playAt,
                            onPlayAll = myListViewModel::playAll,
                            onRemoveSong = myListViewModel::removeSongAt,
                            onMoveSong = myListViewModel::moveSongAt,
                            onClearList = myListViewModel::clearActiveList,
                            onToastConsumed = myListViewModel::consumeToast,
                        )
                    }
                    composable("play") {
                        val state by playViewModel.playerRepository.uiState.collectAsStateWithLifecycle()
                        val extra by playViewModel.extra.collectAsState()
                        PlayScreen(
                            state = state,
                            extra = extra,
                            onToggle = playViewModel::togglePlay,
                            onNext = playViewModel::next,
                            onPrevious = playViewModel::previous,
                            onSeek = playViewModel::seekTo,
                            onCycleMode = playViewModel::cyclePlayMode,
                            onBack = { navController.popBackStack() },
                            onAddToList = { addToListTarget = it },
                            onPlayQueueAt = playViewModel::playQueueAt,
                        )
                    }
                    composable("setting") {
                        val state by settingViewModel.uiState.collectAsState()
                        SettingScreen(
                            state = state,
                            onImport = settingViewModel::importScript,
                            onImportUrl = settingViewModel::importScriptFromUrl,
                            onRefreshScript = settingViewModel::refreshScript,
                            onSelectScript = settingViewModel::selectScript,
                            onDeleteScript = settingViewModel::deleteScript,
                            onSetQuality = settingViewModel::setQuality,
                            onToggleSearchSource = settingViewModel::toggleSearchSource,
                            onSetThemeMode = settingViewModel::setThemeMode,
                            onSetDisplayMode = settingViewModel::setDisplayMode,
                            onConsumeMessage = settingViewModel::consumeMessage,
                        )
                    }
                }
                if (showChrome && playerState.current != null) {
                    MiniPlayer(
                        state = playerState,
                        onToggle = playViewModel::togglePlay,
                        onClick = { navController.navigate("play") { launchSingleTop = true } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
            }
        }
    } else {
        // ── 平板 / TV：左侧导航栏 + 右侧内容 ──
        Row(modifier = modifier.fillMaxSize()) {
            SideNavBar(
                currentRoute = currentRoute,
                navFocus = navFocus,
                isTv = adaptive.isTv,
                playerState = playerState,
                loading = playerState.loading,
                onSelect = { navigateTab(navController, it) },
                onToggle = playViewModel::togglePlay,
                onPrevious = playViewModel::previous,
                onNext = playViewModel::next,
                onOpenPlay = { navController.navigate("play") { launchSingleTop = true } },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "mylist",
                    modifier = Modifier
                        .fillMaxSize()
                        // 平板悬浮迷你条占位（TV 无迷你条）
                        .then(
                            if (showChrome && !adaptive.isTv && playerState.current != null)
                                Modifier.padding(bottom = miniOverlaySpace) else Modifier
                        ),
                ) {
                    composable("search") {
                        val state by searchViewModel.uiState.collectAsState()
                        SearchScreen(
                            state = state,
                            onKeywordChange = searchViewModel::setKeyword,
                            onSearch = searchViewModel::search,
                            onSourceChange = searchViewModel::setSource,
                            onPlay = searchViewModel::playAll,
                            onLoadMore = searchViewModel::loadMore,
                            onAddToList = { addToListTarget = it },
                        )
                    }
                    composable("leaderboard") {
                        val state by leaderboardViewModel.uiState.collectAsState()
                        LeaderboardScreen(
                            state = state,
                            onSourceChange = leaderboardViewModel::setSource,
                            onBoardSelect = leaderboardViewModel::selectBoard,
                            onPlay = leaderboardViewModel::playAt,
                            onLoadMore = leaderboardViewModel::loadMore,
                            onAddToList = { addToListTarget = it },
                        )
                    }
                    composable("songlist") {
                        val state by songListViewModel.uiState.collectAsState()
                        SongListScreen(
                            state = state,
                            onSourceChange = songListViewModel::setSource,
                            onSortChange = songListViewModel::setSort,
                            onTagChange = songListViewModel::setTag,
                            onOpenDetail = songListViewModel::openDetail,
                            onCloseDetail = songListViewModel::closeDetail,
                            onPlay = songListViewModel::playAt,
                            onLoadMoreLists = songListViewModel::loadMoreLists,
                            onLoadMoreDetail = songListViewModel::loadMoreDetail,
                            onAddToList = { addToListTarget = it },
                        )
                    }
                    composable("mylist") {
                        val state by myListViewModel.uiState.collectAsState()
                        MyListScreen(
                            state = state,
                            onSelectList = myListViewModel::selectList,
                            onCreateList = myListViewModel::createList,
                            onRenameList = myListViewModel::renameList,
                            onRemoveList = myListViewModel::removeList,
                            onPlay = myListViewModel::playAt,
                            onPlayAll = myListViewModel::playAll,
                            onRemoveSong = myListViewModel::removeSongAt,
                            onMoveSong = myListViewModel::moveSongAt,
                            onClearList = myListViewModel::clearActiveList,
                            onToastConsumed = myListViewModel::consumeToast,
                        )
                    }
                    composable("play") {
                        val state by playViewModel.playerRepository.uiState.collectAsStateWithLifecycle()
                        val extra by playViewModel.extra.collectAsState()
                        PlayScreen(
                            state = state,
                            extra = extra,
                            onToggle = playViewModel::togglePlay,
                            onNext = playViewModel::next,
                            onPrevious = playViewModel::previous,
                            onSeek = playViewModel::seekTo,
                            onCycleMode = playViewModel::cyclePlayMode,
                            onBack = { navController.popBackStack() },
                            onAddToList = { addToListTarget = it },
                            onPlayQueueAt = playViewModel::playQueueAt,
                        )
                    }
                    composable("setting") {
                        val state by settingViewModel.uiState.collectAsState()
                        SettingScreen(
                            state = state,
                            onImport = settingViewModel::importScript,
                            onImportUrl = settingViewModel::importScriptFromUrl,
                            onRefreshScript = settingViewModel::refreshScript,
                            onSelectScript = settingViewModel::selectScript,
                            onDeleteScript = settingViewModel::deleteScript,
                            onSetQuality = settingViewModel::setQuality,
                            onToggleSearchSource = settingViewModel::toggleSearchSource,
                            onSetThemeMode = settingViewModel::setThemeMode,
                            onSetDisplayMode = settingViewModel::setDisplayMode,
                            onConsumeMessage = settingViewModel::consumeMessage,
                        )
                    }
                }
                // 平板：迷你播放条悬浮在底部（与手机一致）；TV 不显示迷你条
                if (showChrome && !adaptive.isTv && playerState.current != null) {
                    MiniPlayer(
                        state = playerState,
                        onToggle = playViewModel::togglePlay,
                        onClick = { navController.navigate("play") { launchSingleTop = true } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .navigationBarsPadding()
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
            }
        }
    }

    // “收藏到歌单”弹窗（选择目标歌单即收藏）
    addToListTarget?.let { song ->
        AddToListDialog(
            lists = myListsState.lists,
            songName = "${song.name} - ${song.singer}",
            onDismiss = { addToListTarget = null },
            onSelect = { list ->
                myListViewModel.addTo(list.id, song)
                addToListTarget = null
            },
        )
    }
}

private fun navigateTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 手机底部导航栏 */
@Composable
private fun BottomNavBar(
    currentRoute: String,
    navFocus: FocusRequester,
    onSelect: (String) -> Unit,
) {
    NavigationBar {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                modifier = Modifier.lxFocusable(
                    focusRequester = if (selected) navFocus else null,
                    shape = RoundedCornerShape(10.dp),
                ),
            )
        }
    }
}

/**
 * 平板 / TV 左侧导航栏。
 *  - 平板：导航 + 悬浮迷你条（内容区底部），状态栏/手势条正常避让；
 *  - TV  ：顶部为播放控制区（上一首 / 播放暂停 / 下一首 + 打开播放页），
 *          无迷你播放条；全屏沉浸。
 */
@Composable
private fun SideNavBar(
    currentRoute: String,
    navFocus: FocusRequester,
    isTv: Boolean,
    playerState: PlayerUiState,
    loading: Boolean,
    onSelect: (String) -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width = if (isTv) 240.dp else 200.dp
    Surface(
        modifier = modifier.fillMaxHeight(),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                // 平板避让状态栏；TV 全屏不预留
                .then(if (isTv) Modifier else Modifier.statusBarsPadding())
                .padding(vertical = 12.dp),
        ) {
            // TV：导航栏顶部播放控制区（播放界面入口）
            if (isTv) {
                TvPlayControls(
                    playerState = playerState,
                    loading = loading,
                    onToggle = onToggle,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onOpenPlay = onOpenPlay,
                )
                Spacer(Modifier.height(12.dp))
            }

            tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else androidx.compose.ui.graphics.Color.Transparent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable { onSelect(tab.route) }
                        .lxFocusable(
                            focusRequester = if (selected) navFocus else null,
                            showBorder = false,
                        )
                        .padding(horizontal = 14.dp, vertical = if (isTv) 14.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (isTv) 28.dp else 24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        tab.label,
                        style = if (isTv) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyLarge,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            // 平板：底部手势条避让；TV 全屏不需要
            if (!isTv) Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** TV 左侧导航栏顶部的播放控制区：上一首 / 播放暂停 / 下一首 + 打开播放页 */
@Composable
private fun TvPlayControls(
    playerState: PlayerUiState,
    loading: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 当前曲名（无曲目时显示占位）
        Text(
            playerState.current?.name ?: "未在播放",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cn.toside.music.compose.ui.component.LxIconButton(
                onClick = onPrevious,
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "上一首",
            )
            // 播放/暂停：圆形主色大按钮
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onToggle)
                    .lxFocusable(shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else {
                    Icon(
                        if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            cn.toside.music.compose.ui.component.LxIconButton(
                onClick = onNext,
                imageVector = Icons.Default.SkipNext,
                contentDescription = "下一首",
            )
        }
        Spacer(Modifier.height(10.dp))
        // 打开播放页入口
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpenPlay)
                .lxFocusable()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "播放页",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
