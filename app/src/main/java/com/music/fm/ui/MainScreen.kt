package com.music.fm.ui

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.music.fm.data.model.MusicInfo
import com.music.fm.player.PlayerUiState
import com.music.fm.ui.adaptive.LocalAdaptive
import com.music.fm.ui.adaptive.ProvideAdaptive
import com.music.fm.ui.component.AddToListDialog
import com.music.fm.ui.component.MiniPlayer
import com.music.fm.ui.component.MvLoadingDialog
import com.music.fm.ui.component.MvPlayerDialog
import com.music.fm.ui.component.SongRowActions
import com.music.fm.ui.component.LxIconButton
import com.music.fm.ui.component.lxFocusable
import com.music.fm.ui.download.DownloadScreen
import com.music.fm.ui.download.DownloadViewModel
import com.music.fm.ui.download.DownloadQualityDialog
import com.music.fm.ui.download.MvViewModel
import com.music.fm.ui.leaderboard.LeaderboardScreen
import com.music.fm.ui.leaderboard.LeaderboardViewModel
import com.music.fm.ui.mylist.MyListScreen
import com.music.fm.ui.mylist.MyListViewModel
import com.music.fm.ui.play.PlayScreen
import com.music.fm.ui.play.PlayViewModel
import com.music.fm.ui.search.SearchScreen
import com.music.fm.ui.search.SearchViewModel
import com.music.fm.ui.setting.SettingScreen
import com.music.fm.ui.setting.SettingViewModel
import com.music.fm.ui.songlist.SongListScreen
import com.music.fm.ui.songlist.SongListViewModel
import com.music.fm.ui.theme.FMusicTheme
import kotlinx.coroutines.launch

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

/** 导航项（播放不在其中：手机/平板经迷你播放条进入，TV 经左侧导航栏顶部按钮进入） */
private val tabs = listOf(
    NavTab("mylist", "收藏", Icons.Default.Favorite),
    NavTab("search", "搜索", Icons.Default.Search),
    NavTab("leaderboard", "排行", Icons.Default.FormatListNumbered),
    NavTab("songlist", "歌单", Icons.AutoMirrored.Filled.QueueMusic),
    NavTab("download", "下载", Icons.Default.Download),
    NavTab("setting", "设置", Icons.Default.Settings),
)

/** 播放页为独立全屏页面，不显示导航与迷你播放条 */
private val chromelessRoutes = setOf("play")

/** 无自带顶栏的页面（手机端为其补一个「汉堡 + 标题」栏） */
private val chromeTopTitles = mapOf(
    "search" to "搜索",
    "download" to "下载",
    "setting" to "设置",
)

/** 抽屉导航项顺序（对齐 lx-music 侧栏：搜索 / 歌单 / 排行 / 收藏 / 下载 / 设置） */
private val drawerTabOrder = listOf("search", "songlist", "leaderboard", "mylist", "download", "setting")

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
    val downloadViewModel: DownloadViewModel = viewModel(factory = DownloadViewModel.Factory)
    val mvViewModel: MvViewModel = viewModel(factory = MvViewModel.Factory)

    val playerState by playViewModel.playerRepository.uiState.collectAsStateWithLifecycle()
    val settingState by settingViewModel.uiState.collectAsState()

    // 注入设备形态（含用户显示模式覆盖），TV 端同时放大字号
    ProvideAdaptive(displayMode = settingState.settings.displayMode) {
        // 系统栏：手机/平板正常显示状态栏；TV 全屏沉浸，不预留状态栏区域
        val view = LocalView.current
        val adaptive = LocalAdaptive.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = findActivityWindow(view) ?: return@SideEffect
                val controller = WindowCompat.getInsetsController(window, view)
                // 状态栏 / 导航栏图标样式统一在 MainActivity 里按浅色主题设置
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

        FMusicTheme {
            MainScaffold(
                navController = navController,
                currentRoute = currentRoute,
                searchViewModel = searchViewModel,
                leaderboardViewModel = leaderboardViewModel,
                songListViewModel = songListViewModel,
                myListViewModel = myListViewModel,
                playViewModel = playViewModel,
                settingViewModel = settingViewModel,
                downloadViewModel = downloadViewModel,
                mvViewModel = mvViewModel,
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
    downloadViewModel: DownloadViewModel,
    mvViewModel: MvViewModel,
    playerState: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    // “收藏到歌单”弹窗目标（搜索/排行/歌单行的收藏按钮触发）
    var addToListTarget by remember { mutableStateOf<MusicInfo?>(null) }
    val myListsState by myListViewModel.uiState.collectAsState()

    // TV：三个页面的「独立歌曲列表页」激活状态 —— 激活时隐藏左侧导航栏，全屏显示
    val songListUiState by songListViewModel.uiState.collectAsState()
    var myListShowGrid by remember { mutableStateOf(true) }
    var leaderboardShowGrid by remember { mutableStateOf(true) }

    // TV「退出应用」：停止播放服务（不再后台放歌）后结束应用
    val view = LocalView.current
    val onExitApp: () -> Unit = {
        var ctx: android.content.Context = view.context
        while (ctx is ContextWrapper && ctx !is Activity) ctx = ctx.baseContext
        (ctx as? Activity)?.let { activity ->
            val app = activity.application as com.music.fm.MusicApplication
            app.graph.playerRepository.stopPlayback()
            activity.finishAffinity()
        }
    }

    // 下载 / MV
    val downloadTasks by downloadViewModel.tasks.collectAsStateWithLifecycle()
    val mvState by mvViewModel.uiState.collectAsState()
    val settingUiState by settingViewModel.uiState.collectAsState()
    var downloadTarget by remember { mutableStateOf<MusicInfo?>(null) }
    val songActions = remember {
        SongRowActions(
            onDownload = { downloadTarget = it },
            onPlayMv = { mvViewModel.load(it) },
            onDownloadMv = { downloadViewModel.addMvTask(it) },
            onAddToList = { addToListTarget = it },
        )
    }

    val adaptive = LocalAdaptive.current
    // 播放页为独立全屏页面：不显示导航与迷你播放条
    val showChrome = currentRoute !in chromelessRoutes
    val isSideNav = !adaptive.isPhone
    // 歌曲列表独立页（排行榜榜内 / 歌单详情 / 收藏详情）激活：TV 与平板隐藏左侧导航栏
    // （手机无导航栏）；迷你播放条保持显示
    val songPageActive = showChrome && (
        (currentRoute == "leaderboard" && !leaderboardShowGrid) ||
            (currentRoute == "songlist" && songListUiState.detail != null) ||
            (currentRoute == "mylist" && !myListShowGrid)
        )
    val hideSideNav = songPageActive && !adaptive.isPhone

    // ── 抽屉导航（对齐 lx-music 侧栏）：手机所有页面 + 平板歌曲独立页的汉堡按钮打开 ──
    val drawerState = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed,
    )
    val scope = rememberCoroutineScope()
    val openDrawer = {
        scope.launch { drawerState.open() }
        Unit
    }
    // 汉堡按钮仅手机显示（平板用左侧导航栏，TV 用左侧导航栏 + 焦点导航）
    val onOpenDrawer: (() -> Unit)? = if (adaptive.isPhone) openDrawer else null

    // TV 遥控器落焦点起点：进入带导航栏的页面时把焦点放到当前导航项
    val navFocus = remember { FocusRequester() }
    LaunchedEffect(currentRoute, adaptive.deviceType) {
        if (adaptive.isTv && showChrome && !songPageActive) runCatching { navFocus.requestFocus() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !adaptive.isTv,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(
                    (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp / 2).dp,
                ),
            ) {
                NavDrawerContent(
                    currentRoute = currentRoute,
                    showExit = adaptive.isPhone,
                    onSelect = { route ->
                        scope.launch { drawerState.close() }
                        navigateTab(navController, route)
                    },
                    onExitApp = onExitApp,
                )
            }
        },
    ) {
    if (adaptive.isPhone) {
        // ── 手机：无底部导航栏；各页面左上角汉堡按钮打开抽屉导航 + 悬浮迷你播放条 ──
        // 内容区避让手势条；迷你条悬浮在手势区上方，底部不再留一层底色
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                // 搜索/下载/设置无自带顶栏：补一个「汉堡 + 标题」栏
                // （收藏/排行/歌单的汉堡集成在各自顶栏内）
                if (showChrome) {
                    chromeTopTitles[currentRoute]?.let { title ->
                        ChromeTopBar(title = title, onMenu = openDrawer)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
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
                            onKeywordChange = searchViewModel::onKeywordChange,
                            onSearch = searchViewModel::search,
                            onSearchWord = searchViewModel::searchWord,
                            onClearSearch = searchViewModel::clearSearch,
                            onSourceChange = searchViewModel::setSource,
                            onPlay = searchViewModel::playAll,
                            onLoadMore = searchViewModel::loadMore,
                            onRemoveHistory = searchViewModel::removeHistory,
                            onClearHistory = searchViewModel::clearHistory,
                            onAppendChar = searchViewModel::appendChar,
                            onBackspace = searchViewModel::backspace,
                            onClearInput = searchViewModel::clearInput,
                            onAddToList = { addToListTarget = it },
                            actions = songActions,
                            playingKey = playerState.current?.uniqueKey,
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
                            actions = songActions,
                            playingKey = playerState.current?.uniqueKey,
                            tvShowBoardGrid = leaderboardShowGrid,
                            onTvShowBoardGridChange = { leaderboardShowGrid = it },
                            onOpenDrawer = onOpenDrawer,
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
                            onOpenDetailById = songListViewModel::openDetailByText,
                            onCloseDetail = songListViewModel::closeDetail,
                            onPlay = songListViewModel::playAt,
                            onCollectPlaylist = songListViewModel::prepareCollectPlaylist,
                            onLoadMoreLists = songListViewModel::loadMoreLists,
                            onLoadMoreDetail = songListViewModel::loadMoreDetail,
                            onAddToList = { addToListTarget = it },
                            actions = songActions,
                            playingKey = playerState.current?.uniqueKey,
                            onOpenDrawer = onOpenDrawer,
                        )
                    }
                    composable("mylist") {
                        val state by myListViewModel.uiState.collectAsState()
                        MyListScreen(
                            state = state,
                            onRefreshMv = myListViewModel::refreshMvInfo,
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
                            actions = songActions,
                            playingKey = playerState.current?.uniqueKey,
                            tvShowGrid = myListShowGrid,
                            onTvShowGridChange = { myListShowGrid = it },
                            onOpenDrawer = onOpenDrawer,
                        )
                    }
                    composable("play") {
                        PlayContent(
                            navController = navController,
                            playViewModel = playViewModel,
                            onAddToList = { addToListTarget = it },
                            onDownload = { downloadTarget = it },
                        )
                    }
                    composable("download") {
                        DownloadScreen(
                            tasks = downloadTasks,
                            onPause = downloadViewModel::pause,
                            onResume = downloadViewModel::resume,
                            onRetry = downloadViewModel::retry,
                            onRemove = downloadViewModel::remove,
                            onClearCompleted = downloadViewModel::clearCompleted,
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
                            onSetDisplayMode = settingViewModel::setDisplayMode,
                            onSetDownloadQuality = settingViewModel::setDownloadQuality,
                            onSetDownloadLyric = settingViewModel::setDownloadLyric,
                            onSetWriteTags = settingViewModel::setWriteTags,
                            onSetWriteCover = settingViewModel::setWriteCover,
                            onSetWriteEmbeddedLyric = settingViewModel::setWriteEmbeddedLyric,
                            onNotifyMessage = settingViewModel::notifyMessage,
                            onConsumeMessage = settingViewModel::consumeMessage,
                        )
                    }
                    }
                }
            }
            // 悬浮迷你播放条：白底胶囊直接浮于页面之上（圆角与阴影在组件内）
            if (showChrome && playerState.current != null) {
                MiniPlayer(
                    state = playerState,
                    onToggle = playViewModel::togglePlay,
                    onClick = { navController.navigate("play") { launchSingleTop = true } },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 10.dp),
                )
            }
        }
    } else {
        // ── 平板 / TV ──
        if (currentRoute == "play") {
            // 播放页为独立全屏页面，不显示左侧导航栏（与手机一致）；
            // 平板显示状态栏需避让，TV 全屏沉浸不预留
            // 这里不会组合 NavHost（其自带的返回处理随之失效），返回键必须显式处理：
            // 否则遥控器返回键会直接退出应用，而不是回到进入播放页之前的页面。
            BackHandler { navController.popBackStack() }
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (!adaptive.isTv) Modifier.statusBarsPadding() else Modifier),
            ) {
                PlayContent(
                    navController = navController,
                    playViewModel = playViewModel,
                    onAddToList = { addToListTarget = it },
                    onDownload = { downloadTarget = it },
                )
            }
        } else {
        // ── 左侧导航栏 + 右侧内容（歌曲列表独立页时不显示导航栏，内容全屏） ──
        Row(modifier = modifier.fillMaxSize()) {
            if (!hideSideNav) {
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
                    onExitApp = if (adaptive.isTv) onExitApp else null,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // 平板：状态栏与手势条避让（TV 全屏不预留）
                    .then(
                        if (!adaptive.isTv) Modifier.statusBarsPadding().navigationBarsPadding()
                        else Modifier
                    ),
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
                            onKeywordChange = searchViewModel::onKeywordChange,
                            onSearch = searchViewModel::search,
                            onSearchWord = searchViewModel::searchWord,
                            onClearSearch = searchViewModel::clearSearch,
                            onSourceChange = searchViewModel::setSource,
                            onPlay = searchViewModel::playAll,
                            onLoadMore = searchViewModel::loadMore,
                            onRemoveHistory = searchViewModel::removeHistory,
                            onClearHistory = searchViewModel::clearHistory,
                            onAppendChar = searchViewModel::appendChar,
                            onBackspace = searchViewModel::backspace,
                            onClearInput = searchViewModel::clearInput,
                            onAddToList = { addToListTarget = it },
                            actions = songActions,
                            playingKey = playerState.current?.uniqueKey,
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
                            actions = songActions,
                            playingKey = playerState.current?.uniqueKey,
                            tvShowBoardGrid = leaderboardShowGrid,
                            onTvShowBoardGridChange = { leaderboardShowGrid = it },
                            onOpenDrawer = onOpenDrawer,
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
                            onOpenDetailById = songListViewModel::openDetailByText,
                            onCloseDetail = songListViewModel::closeDetail,
                            onPlay = songListViewModel::playAt,
                            onCollectPlaylist = songListViewModel::prepareCollectPlaylist,
                            onLoadMoreLists = songListViewModel::loadMoreLists,
                            onLoadMoreDetail = songListViewModel::loadMoreDetail,
                            onAddToList = { addToListTarget = it },
                            actions = songActions,
                            playingKey = playerState.current?.uniqueKey,
                            onOpenDrawer = onOpenDrawer,
                        )
                    }
                    composable("mylist") {
                        val state by myListViewModel.uiState.collectAsState()
                        MyListScreen(
                            state = state,
                            onRefreshMv = myListViewModel::refreshMvInfo,
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
                            actions = songActions,
                            playingKey = playerState.current?.uniqueKey,
                            tvShowGrid = myListShowGrid,
                            onTvShowGridChange = { myListShowGrid = it },
                            onOpenDrawer = onOpenDrawer,
                        )
                    }
                    composable("play") {
                        // 实际渲染走外层的独立全屏分支，这里仅注册目的地
                        PlayContent(
                            navController = navController,
                            playViewModel = playViewModel,
                            onAddToList = { addToListTarget = it },
                            onDownload = { downloadTarget = it },
                        )
                    }
                    composable("download") {
                        DownloadScreen(
                            tasks = downloadTasks,
                            onPause = downloadViewModel::pause,
                            onResume = downloadViewModel::resume,
                            onRetry = downloadViewModel::retry,
                            onRemove = downloadViewModel::remove,
                            onClearCompleted = downloadViewModel::clearCompleted,
                        )
                    }
                    composable("filepicker") {
                        com.music.fm.ui.filepicker.FilePickerScreen(
                            onFileSelected = { path ->
                                settingViewModel.importScriptFromFile(path)
                                navController.popBackStack()
                            },
                            onDismiss = { navController.popBackStack() },
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
                            onSetDisplayMode = settingViewModel::setDisplayMode,
                            onSetDownloadQuality = settingViewModel::setDownloadQuality,
                            onSetDownloadLyric = settingViewModel::setDownloadLyric,
                            onSetWriteTags = settingViewModel::setWriteTags,
                            onSetWriteCover = settingViewModel::setWriteCover,
                            onSetWriteEmbeddedLyric = settingViewModel::setWriteEmbeddedLyric,
                            onNotifyMessage = settingViewModel::notifyMessage,
                            onOpenFilePicker = if (adaptive.isTv) {
                                {
                                    navController.navigate("filepicker") { launchSingleTop = true }
                                }
                            } else null,
                            onConsumeMessage = settingViewModel::consumeMessage,
                        )
                    }
                }
                // 平板：迷你播放条白底胶囊悬浮在底部（与手机一致）；TV 不显示迷你条
                if (showChrome && !adaptive.isTv && playerState.current != null) {
                    MiniPlayer(
                        state = playerState,
                        onToggle = playViewModel::togglePlay,
                        onClick = { navController.navigate("play") { launchSingleTop = true } },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 64.dp, end = 64.dp, top = 10.dp, bottom = 10.dp),
                    )
                }
            }
        }
        }
    }
    } // ModalNavigationDrawer

    // 下载音质选择弹窗
    downloadTarget?.let { info ->
        DownloadQualityDialog(
            info = info,
            defaultQuality = settingUiState.settings.downloadQuality,
            onDismiss = { downloadTarget = null },
            onConfirm = { quality ->
                downloadViewModel.addSongTask(info, quality)
                downloadTarget = null
            },
        )
    }

    // MV：取链接中 / 全屏播放
    if (mvState.loading) {
        MvLoadingDialog("正在获取MV链接…")
    }
    mvState.url?.let { url ->
        MvPlayerDialog(
            url = url,
            title = mvState.title,
            onDismiss = { mvViewModel.dismiss() },
        )
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

/** 播放页内容（手机 / 平板 / TV 三端共用） */
@Composable
private fun PlayContent(
    navController: NavHostController,
    playViewModel: PlayViewModel,
    onAddToList: (MusicInfo) -> Unit,
    onDownload: (MusicInfo) -> Unit,
) {
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
        onAddToList = onAddToList,
        onDownload = onDownload,
        onPlayQueueAt = playViewModel::playQueueAt,
    )
}

/** 手机端无自带顶栏页面的「汉堡 + 标题」栏 */
@Composable
private fun ChromeTopBar(title: String, onMenu: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LxIconButton(
            onClick = onMenu,
            imageVector = Icons.Default.Menu,
            contentDescription = "打开导航",
        )
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 抽屉导航内容（对齐 lx-music 侧栏）：
 * 顶部品牌区 + 导航项（点击进入对应页面并关闭抽屉）+ 底部退出应用（手机）。
 */
@Composable
private fun NavDrawerContent(
    currentRoute: String,
    showExit: Boolean,
    onSelect: (String) -> Unit,
    onExitApp: (() -> Unit)?,
) {
    Column(Modifier.fillMaxHeight()) {
        // 品牌头部
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Album,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "FMusic",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }

        // 导航项（lx-music 顺序）
        val drawerTabs = drawerTabOrder.mapNotNull { route -> tabs.firstOrNull { it.route == route } }
        drawerTabs.forEach { tab ->
            val selected = currentRoute == tab.route
            val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else androidx.compose.ui.graphics.Color.Transparent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onSelect(tab.route) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    tab.icon,
                    contentDescription = tab.label,
                    tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    tab.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // 退出应用（手机端，对齐 lx-music 侧栏底部）
        if (showExit && onExitApp != null) {
            var showExitDialog by remember { mutableStateOf(false) }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showExitDialog = true }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "退出",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    "退出应用",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showExitDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showExitDialog = false },
                    title = { Text("退出应用") },
                    text = { Text("确定退出吗？退出后将停止歌曲播放。") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showExitDialog = false
                                onExitApp()
                            },
                        ) { Text("退出") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showExitDialog = false },
                        ) { Text("取消") }
                    },
                )
            }
            Spacer(Modifier.navigationBarsPadding())
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
    onExitApp: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val width = if (isTv) 160.dp else 200.dp
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
                TvPlayEntry(onOpenPlay = onOpenPlay)
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
                        .lxFocusable(
                            focusRequester = if (selected) navFocus else null,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { onSelect(tab.route) }
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

            // TV：底部退出应用（遥控器没有返回桌面再杀后台的便利操作，
            // 退出时停止播放服务，不在后台继续放歌）
            if (isTv && onExitApp != null) {
                var showExitDialog by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .lxFocusable(shape = RoundedCornerShape(12.dp))
                        .clickable { showExitDialog = true }
                        .padding(horizontal = 14.dp, vertical = if (isTv) 14.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "退出",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(if (isTv) 28.dp else 24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "退出",
                        style = if (isTv) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showExitDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { Text("退出应用") },
                        text = { Text("确定退出吗？退出后将停止歌曲播放。") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    showExitDialog = false
                                    onExitApp()
                                },
                                modifier = Modifier.lxFocusable(shape = CircleShape),
                            ) { Text("退出") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(
                                onClick = { showExitDialog = false },
                                modifier = Modifier.lxFocusable(shape = CircleShape),
                            ) { Text("取消") }
                        },
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            // 平板：底部手势条避让；TV 全屏不需要
            if (!isTv) Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/** TV 左侧导航栏顶部的播放页入口：一个小圆形图标（不含播放控制按键） */
@Composable
private fun TvPlayEntry(onOpenPlay: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .lxFocusable(shape = CircleShape)
                .clickable(onClick = onOpenPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Album,
                contentDescription = "播放页",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}
