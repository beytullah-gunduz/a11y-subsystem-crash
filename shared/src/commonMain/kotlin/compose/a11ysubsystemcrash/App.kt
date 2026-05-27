package compose.a11ysubsystemcrash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class Song(val id: Int, val title: String, val artist: String)

data class ShowMediaForSong(val song: Song)

private val songs: List<Song> = List(30) { i ->
    Song(
        id = i,
        title = "Song ${i + 1}",
        artist = "Artist ${(i % 7) + 1}",
    )
}

// Mirrors the bug-report reproduction context: "contents of one pane are
// conditionally rendered based on a collectAsState(initial = 0) flow that
// flips once room data loads. On every cold start there is a window where
// a focusable subtree is mounted and torn down while the macOS accessibility
// system is polling for the focus owner."
//
// Here, the synthetic Flow flips from 0 → 1 after 500 ms — the same shape as
// a Room/DataStore cold-load.
private val syntheticDataReadyFlow = MutableStateFlow(0)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            ListDetailMvp()
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun ListDetailMvp() {
    val scope = rememberCoroutineScope()

    val navigator: ThreePaneScaffoldNavigator<Any> =
        rememberListDetailPaneScaffoldNavigator<Any>(
            scaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()),
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
        )

    val paneExpansionState = rememberPaneExpansionState(
        keyProvider = navigator.scaffoldValue,
        anchors = listOf(
            PaneExpansionAnchor.Proportion(0.25f),
            PaneExpansionAnchor.Proportion(0.5f),
            PaneExpansionAnchor.Proportion(0.75f),
        ),
        initialAnchoredIndex = 0,
    )

    val content: Any? = navigator.currentDestination?.contentKey
    val isExtraPaneActive = content is ShowMediaForSong

    // Same pattern as the source project: resize first pane proportion only
    // when the navigation state flips, not on every AnimatedPane animation.
    LaunchedEffect(isExtraPaneActive) {
        if (isExtraPaneActive) {
            paneExpansionState.setFirstPaneProportion(0.5f)
        } else {
            paneExpansionState.setFirstPaneProportion(0.25f)
        }
    }

    // Kick the synthetic "data ready" flip once per process. The 500 ms delay
    // gives the AWT accessibility bridge time to start polling getFocusOwner
    // before the focusable subtree below is conditionally mounted.
    LaunchedEffect(Unit) {
        delay(500)
        syntheticDataReadyFlow.value = 1
    }

    ListDetailPaneScaffold(
        paneExpansionState = paneExpansionState,
        paneExpansionDragHandle = { state ->
            val interactionSource = remember { MutableInteractionSource() }
            VerticalDragHandle(
                modifier = Modifier.paneExpansionDraggable(
                    state,
                    LocalMinimumInteractiveComponentSize.current,
                    interactionSource,
                ),
                interactionSource = interactionSource,
            )
        },
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                ListPaneContent(
                    onSongClick = { song ->
                        scope.launch {
                            navigator.navigateTo(
                                pane = ListDetailPaneScaffoldRole.Detail,
                                contentKey = song,
                            )
                        }
                    },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val detailContent = navigator.currentDestination?.contentKey
                val song: Song? = when (detailContent) {
                    is Song -> detailContent
                    is ShowMediaForSong -> detailContent.song
                    else -> null
                }
                DetailPaneContent(
                    song = song,
                    onShowMedia = { s ->
                        scope.launch {
                            navigator.navigateTo(
                                pane = ListDetailPaneScaffoldRole.Extra,
                                contentKey = ShowMediaForSong(s),
                            )
                        }
                    },
                )
            }
        },
        extraPane = {
            if (content is ShowMediaForSong) {
                AnimatedPane {
                    ExtraPaneContent(
                        song = content.song,
                        onClose = {
                            scope.launch {
                                navigator.navigateTo(
                                    pane = ListDetailPaneScaffoldRole.Detail,
                                    contentKey = content.song,
                                )
                            }
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun ListPaneContent(onSongClick: (Song) -> Unit) {
    // Conditional render gated on the synthetic Flow — replicates the
    // "focusable subtree mounted/torn down during cold-start" trigger.
    // Before the flow flips (first 500 ms), show a placeholder Column.
    // After it flips, mount the LazyColumn of focusable items.
    val dataReady by syntheticDataReadyFlow.collectAsState()

    if (dataReady == 0) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Loading…", style = MaterialTheme.typography.titleMedium)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            items(songs, key = { it.id }) { song ->
                SongRow(song = song, onClick = { onSongClick(song) })
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column {
            Text(song.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailPaneContent(song: Song?, onShowMedia: (Song) -> Unit) {
    if (song == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Select a song from the list", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(song.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            "by ${song.artist}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "ID: ${song.id}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { onShowMedia(song) }) {
            Text("Show media")
        }
    }
}

@Composable
private fun ExtraPaneContent(song: Song, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Media", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Media for ${song.title}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onClose) {
            Text("Close")
        }
    }
}
