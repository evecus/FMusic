package cn.toside.music.compose.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cn.toside.music.compose.data.ThemeMode
import cn.toside.music.compose.player.PlayerUiState
import cn.toside.music.compose.ui.component.MiniPlayer
import cn.toside.music.compose.ui.leaderboard.LeaderboardScreen
import cn.toside.music.compose.ui.leaderboard.LeaderboardViewModel
import cn.toside.music.compose.ui.play.PlayScreen
import cn.toside.music.compose.ui.play.PlayViewModel
import cn.toside.music.compose.ui.search.SearchScreen
import cn.toside.music.compose.ui.search.SearchViewModel
import cn.toside.music.compose.ui.setting.SettingScreen
import cn.toside.music.compose.ui.setting.SettingViewModel
import cn.toside.music.compose.ui.songlist.SongListScreen
import cn.toside.music.compose.ui.songlist.SongListViewModel
import cn.toside.music.compose.ui.theme.LxMusicTheme

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab("search", "搜索"),
    Tab("leaderboard", "排行"),
    Tab("songlist", "歌单"),
    Tab("play", "播放"),
    Tab("setting", "设置"),
)

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "search"

    val searchViewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
    val leaderboardViewModel: LeaderboardViewModel = viewModel(factory = LeaderboardViewModel.Factory)
    val songListViewModel: SongListViewModel = viewModel(factory = SongListViewModel.Factory)
    val playViewModel: PlayViewModel = viewModel(factory = PlayViewModel.Factory)
    val settingViewModel: SettingViewModel = viewModel(factory = SettingViewModel.Factory)

    val playerState by playViewModel.playerRepository.uiState.collectAsStateWithLifecycle()
    val settingState by settingViewModel.uiState.collectAsState()
    val dark = when (settingState.settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    LxMusicTheme(darkTheme = dark) {
        MainScaffold(
            navController = navController,
            currentRoute = currentRoute,
            searchViewModel = searchViewModel,
            leaderboardViewModel = leaderboardViewModel,
            songListViewModel = songListViewModel,
            playViewModel = playViewModel,
            settingViewModel = settingViewModel,
            playerState = playerState,
            modifier = modifier,
        )
    }
}

@Composable
private fun MainScaffold(
    navController: androidx.navigation.NavHostController,
    currentRoute: String,
    searchViewModel: SearchViewModel,
    leaderboardViewModel: LeaderboardViewModel,
    songListViewModel: SongListViewModel,
    playViewModel: PlayViewModel,
    settingViewModel: SettingViewModel,
    playerState: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column {
                MiniPlayer(
                    state = playerState,
                    onToggle = playViewModel::togglePlay,
                    onClick = {
                        navController.navigate("play") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (tab.route) {
                                        "search" -> Icons.Default.Home
                                        "leaderboard" -> Icons.Default.Leaderboard
                                        "songlist" -> Icons.Default.QueueMusic
                                        "play" -> Icons.Default.PlayCircle
                                        else -> Icons.Default.Settings
                                    },
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "search",
            modifier = Modifier.padding(padding),
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
                )
            }
            composable("setting") {
                val state by settingViewModel.uiState.collectAsState()
                SettingScreen(
                    state = state,
                    onImport = settingViewModel::importScript,
                    onSelectScript = settingViewModel::selectScript,
                    onDeleteScript = settingViewModel::deleteScript,
                    onSetQuality = settingViewModel::setQuality,
                    onToggleSearchSource = settingViewModel::toggleSearchSource,
                    onSetThemeMode = settingViewModel::setThemeMode,
                    onConsumeMessage = settingViewModel::consumeMessage,
                )
            }
        }
    }
}
