package cn.toside.music.compose.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cn.toside.music.compose.data.model.MusicInfo
import cn.toside.music.compose.data.model.Sources
import cn.toside.music.compose.ui.adaptive.LocalAdaptive
import cn.toside.music.compose.ui.component.LxIconButton
import cn.toside.music.compose.ui.component.lxFocusable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSourceChange: (String) -> Unit,
    onPlay: (Int) -> Unit,
    onLoadMore: () -> Unit,
    onAddToList: (MusicInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = LocalAdaptive.current
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.results.size) {
        if (shouldLoadMore && state.results.isNotEmpty()) onLoadMore()
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = adaptive.pagePadding, vertical = 8.dp),
            placeholder = { Text("搜索歌曲 / 歌手") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.keyword.isNotEmpty()) {
                    LxIconButton(
                        onClick = { onKeywordChange("") },
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清空",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = adaptive.pagePadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.enabledSources.forEach { source ->
                FilterChip(
                    selected = state.source == source,
                    onClick = { onSourceChange(source) },
                    label = { Text(Sources.label(source)) },
                    modifier = Modifier.lxFocusable(shape = CircleShape),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.results.isEmpty() && state.error == null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("输入关键词开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant) }

                state.results.isEmpty() && state.error != null -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error, color = MaterialTheme.colorScheme.error) }

                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.uniqueKey }) { info ->
                        MusicRow(
                            info = info,
                            onClick = { onPlay(state.results.indexOf(info)) },
                            onAddToList = { onAddToList(info) },
                        )
                    }
                    if (state.loadingMore) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }

        // TV：屏上键盘（遥控器无法呼出输入法，内置 26 字母 + 数字键盘）
        if (adaptive.isTv) {
            TvKeyboard(
                keyword = state.keyword,
                onKeywordChange = onKeywordChange,
                onSearch = onSearch,
            )
        }
    }
}

/** TV 屏上键盘：数字行 + 三行字母 + 删除/清空/搜索 */
@Composable
private fun TvKeyboard(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val numberRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    val row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
    val row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
    val row3 = listOf("Z", "X", "C", "V", "B", "N", "M")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KeyboardRow(numberRow) { onKeywordChange(keyword + it) }
        KeyboardRow(row1) { onKeywordChange(keyword + it) }
        KeyboardRow(row2) { onKeywordChange(keyword + it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            row3.forEach { key ->
                TvKey(label = key, onClick = { onKeywordChange(keyword + key) }, modifier = Modifier.weight(1f))
            }
            TvKey(
                label = "⌫",
                onClick = { onKeywordChange(keyword.dropLast(1)) },
                modifier = Modifier.weight(3f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TvKey(
                label = "清空",
                onClick = { onKeywordChange("") },
                modifier = Modifier.weight(2f),
            )
            TvKey(
                label = "搜索",
                onClick = onSearch,
                modifier = Modifier.weight(8f),
            )
        }
    }
}

@Composable
private fun KeyboardRow(keys: List<String>, onKey: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keys.forEach { key ->
            TvKey(label = key, onClick = { onKey(key) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun TvKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(if (label.length > 1) 44.dp else 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .lxFocusable(shape = RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (label.length > 1) MaterialTheme.typography.labelLarge
            else MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun MusicRow(info: MusicInfo, onClick: () -> Unit, onAddToList: () -> Unit) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(info.name, maxLines = 1) },
        supportingContent = {
            Text(
                buildString {
                    append(info.singer)
                    if (!info.albumName.isNullOrEmpty()) append(" - ").append(info.albumName)
                    if (!info.interval.isNullOrEmpty()) append(" · ").append(info.interval)
                },
                maxLines = 1,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(Sources.label(info.source)) })
                LxIconButton(
                    onClick = onAddToList,
                    imageVector = Icons.Default.PlaylistAdd,
                    contentDescription = "收藏到歌单",
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .lxFocusable(),
    )
}
