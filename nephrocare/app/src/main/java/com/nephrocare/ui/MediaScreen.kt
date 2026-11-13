package com.nephrocare.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nephrocare.R
import com.nephrocare.data.VideoItem
import kotlinx.coroutines.launch

private data class IllustrationInfo(val id: String, val title: Int, val caption: Int)

private val ILLUSTRATIONS = listOf(
    IllustrationInfo("kidney", R.string.illus_kidney_title, R.string.illus_kidney_caption),
    IllustrationInfo("fistula", R.string.illus_fistula_title, R.string.illus_fistula_caption),
    IllustrationInfo("dialysis", R.string.illus_dialysis_title, R.string.illus_dialysis_caption),
)

fun videoCategoryLabel(c: String): Int = when (c) {
    "FISTULA" -> R.string.video_cat_fistula
    "NUTRITION" -> R.string.video_cat_nutrition
    "DIALYSIS" -> R.string.video_cat_dialysis
    else -> R.string.video_cat_general
}

@Composable
fun MediaScreen(back: () -> Unit, go: (String) -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    val videos by app.db.videos().observeAll().collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("ALL") }

    ScreenScaffold(stringResource(R.string.section_media), back) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SectionHeader(stringResource(R.string.media_illustrations)) }
            items(ILLUSTRATIONS, key = { it.id }) { ill ->
                AppCard(onClick = { go(Routes.illustration(ill.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(ill.title), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.media_videos)) }
            item { Banner(stringResource(R.string.media_videos_intro)) }
            item {
                ChoiceChips(
                    listOf("ALL" to stringResource(R.string.food_all)) +
                        listOf("GENERAL", "DIALYSIS", "FISTULA", "NUTRITION").map { it to stringResource(videoCategoryLabel(it)) },
                    category, { category = it },
                )
            }
            val shown = videos.filter { category == "ALL" || it.category == category }
            if (shown.isEmpty()) item { EmptyState(stringResource(R.string.media_no_videos)) }
            items(shown, key = { it.id }) { v ->
                AppCard(onClick = { go(Routes.video(v.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOf(stringResource(videoCategoryLabel(v.category)), v.language.uppercase(), if (v.subtitleUri != null) "CC" else "")
                                    .filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        IconButton(onClick = { scope.launch { app.db.videos().delete(v) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                }
            }
            item {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.media_add_video)) }
            }
        }
    }
    if (adding) AddVideoDialog(onDismiss = { adding = false })
}

@Composable
private fun AddVideoDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf<String?>(null) }
    var language by remember { mutableStateOf(app.language()) }
    var category by remember { mutableStateOf("GENERAL") }

    fun persist(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persist(uri); url = uri.toString() }
    }
    val pickSubtitle = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { persist(uri); subtitle = uri.toString() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.media_add_video)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(title, { title = it }, stringResource(R.string.media_title))
                OutlinedButton(onClick = { pickVideo.launch(arrayOf("video/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.media_pick_file))
                }
                TextInput(url, { url = it }, stringResource(R.string.media_or_link), keyboardType = KeyboardType.Uri)
                OutlinedButton(onClick = { pickSubtitle.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Subtitles, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (subtitle == null) stringResource(R.string.media_pick_subtitle) else stringResource(R.string.media_subtitle_added))
                }
                Text(stringResource(R.string.media_language))
                ChoiceChips(listOf("ar", "fr", "en", "es").map { it to it.uppercase() }, language, { language = it })
                ChoiceChips(listOf("GENERAL", "DIALYSIS", "FISTULA", "NUTRITION").map { it to stringResource(videoCategoryLabel(it)) }, category, { category = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && url.isNotBlank(),
                onClick = {
                    scope.launch {
                        app.db.videos().insert(VideoItem(title = title.trim(), uri = url.trim(), subtitleUri = subtitle, language = language, category = category))
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun IllustrationScreen(id: String, back: () -> Unit) {
    val info = ILLUSTRATIONS.firstOrNull { it.id == id } ?: ILLUSTRATIONS.first()
    ScreenScaffold(stringResource(info.title), back) { padding ->
        ScrollColumn(padding) {
            when (info.id) {
                "fistula" -> FistulaIllustration(Modifier.fillMaxWidth())
                "dialysis" -> DialysisIllustration(Modifier.fillMaxWidth())
                else -> KidneyIllustration(Modifier.fillMaxWidth())
            }
            Text(stringResource(info.caption), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun VideoScreen(id: Long, back: () -> Unit) {
    val app = LocalApp.current
    val video by produceState<VideoItem?>(null, id) { value = app.db.videos().byId(id) }
    ScreenScaffold(video?.title ?: stringResource(R.string.section_media), back) { padding ->
        Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            video?.let { VideoPlayer(it) }
            Text(stringResource(R.string.media_player_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
@Composable
private fun VideoPlayer(video: VideoItem) {
    val context = LocalContext.current
    val player = remember(video.id) {
        val builder = MediaItem.Builder().setUri(video.uri)
        video.subtitleUri?.let { sub ->
            val mime = if (sub.lowercase().endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub))
                        .setMimeType(mime)
                        .setLanguage(video.language)
                        .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                        .build(),
                ),
            )
        }
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(builder.build())
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
    )
}
