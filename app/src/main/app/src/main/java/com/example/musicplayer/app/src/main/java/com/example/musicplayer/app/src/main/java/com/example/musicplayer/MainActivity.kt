package com.example.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val Bg = Color(0xFF0F131A); val Card = Color(0xFF131B2E); val Blue = Color(0xFF1F6BFF)
val Line = Color(0xFF22304F); val Dim = Color(0xFF8A94A8); val Accent = Color(0xFF4C9AFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = Bg, surface = Bg)) { App() } }
    }
}

@Composable
fun Art(song: Song?, size: Dp, radius: Dp = 14.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, song?.id) {
        value = if (song == null || Build.VERSION.SDK_INT < 29) null else withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(song.uri, Size(400, 400), null) }.getOrNull()
        }
    }
    Box(modifier.size(size).clip(RoundedCornerShape(radius)).background(Card)
        .border(1.dp, Line, RoundedCornerShape(radius)), Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Icon(Icons.Filled.MusicNote, null, tint = Accent, modifier = Modifier.size(size / 2))
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("p", 0) }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    var songs by remember { mutableStateOf(listOf<Song>()) }
    var favs by remember { mutableStateOf(prefs.getStringSet("favs", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()) }
    var recent by remember { mutableStateOf(listOf<Song>()) }
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var curId by remember { mutableStateOf<Long?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableStateOf(0L) }
    var dur by remember { mutableStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = it[perm] == true || ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }
    fun scan() { songs = scanSongs(ctx) }
    LaunchedEffect(granted) { if (granted) withContext(Dispatchers.IO) { scan() } }

    DisposableEffect(Unit) {
        val f = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync()
        f.addListener({
            val c = f.get(); controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(p: Boolean) { playing = p }
                override fun onMediaItemTransition(m: MediaItem?, r: Int) {
                    curId = m?.mediaId?.toLongOrNull(); dur = c.duration.coerceAtLeast(0)
                }
                override fun onPlaybackStateChanged(s: Int) { dur = c.duration.coerceAtLeast(0) }
            })
            playing = c.isPlaying; curId = c.currentMediaItem?.mediaId?.toLongOrNull()
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { MediaController.releaseFuture(f) }
    }
    LaunchedEffect(controller) { while (true) { controller?.let { pos = it.currentPosition; dur = it.duration.coerceAtLeast(0) }; delay(500) } }
    LaunchedEffect(curId) { songs.find { it.id == curId }?.let { s -> recent = (listOf(s) + recent.filter { it.id != s.id }).take(10) } }

    val current = songs.find { it.id == curId }
    fun playList(list: List<Song>, i: Int) {
        val c = controller ?: return
        c.setMediaItems(list.map { s ->
            MediaItem.Builder().setMediaId(s.id.toString()).setUri(s.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album).build()).build()
        }, i, 0)
        c.prepare(); c.play()
    }
    fun toggleFav(id: Long) {
        favs = if (id in favs) favs - id else favs + id
        prefs.edit().putStringSet("favs", favs.map { it.toString() }.toSet()).apply()
    }
    fun toggle() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }

    if (!granted) {
        Column(Modifier.fillMaxSize().background(Bg).padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.LibraryMusic, null, tint = Accent, modifier = Modifier.size(84.dp))
            Spacer(Modifier.height(20.dp))
            Text("আপনার গান ও অডিও ফাইল অ্যাক্সেস করতে অনুমতি দিন", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                launcher.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(perm, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(perm))
            }, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("Allow") }
        }
        return
    }

    Scaffold(containerColor = Bg, bottomBar = {
        Column {
            if (current != null && tab != 1) MiniPlayer(current, playing, current.id in favs, { toggle() }, { controller?.seekToNextMediaItem() },
                { toggleFav(current.id) }, { tab = 1 })
            NavigationBar(containerColor = Color(0xFF0A0F1E)) {
                listOf("HOME" to Icons.Outlined.Home, "PLAYER" to Icons.Outlined.Album, "LIBRARY" to Icons.Outlined.LibraryMusic,
                    "PLAYLISTS" to Icons.Outlined.QueueMusic, "SETTINGS" to Icons.Outlined.Settings).forEachIndexed { i, (l, ic) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(ic, null) },
                        label = { Text(l, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent,
                            unselectedIconColor = Dim, unselectedTextColor = Dim, indicatorColor = Color.Transparent))
                }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> Home(songs, favs.size, recent, current, playing, { toggle() }, { tab = 1 }, { scan() }) { playList(listOf(it), 0) }
                1 -> PlayerScreen(current, playing, pos, dur, current?.let { it.id in favs } == true, shuffle, repeat,
                    { toggle() }, { controller?.seekToPreviousMediaItem() }, { controller?.seekToNextMediaItem() },
                    { controller?.seekTo(it) }, { current?.let { s -> toggleFav(s.id) } },
                    { shuffle = !shuffle; controller?.shuffleModeEnabled = shuffle },
                    { repeat = !repeat; controller?.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF })
                2 -> Library(songs, query, { query = it }, favs, curId, { scan() }, { toggleFav(it) }) { list, i -> playList(list, i) }
                3 -> Playlists(songs.filter { it.id in favs }) { list, i -> playList(list, i) }
                else -> SettingsScreen { scan() }
            }
        }
    }
}

@Composable
fun Home(songs: List<Song>, favCount: Int, recent: List<Song>, cur: Song?, playing: Boolean,
         onToggle: () -> Unit, onExpand: () -> Unit, onScan: () -> Unit, onPlay: (Song) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Favourites", "$favCount songs", Icons.Filled.Favorite, Color(0xFF4A1620), Color(0xFFFF5470), Modifier.weight(1f))
                StatCard("Playlists", "1 lists", Icons.Outlined.QueueMusic, Color(0xFF0F3B45), Color(0xFF2DD4BF), Modifier.weight(1f))
                StatCard("Recent", "${recent.size} tracks", Icons.Outlined.History, Color(0xFF2A1A55), Color(0xFFA78BFA), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("NOW PLAYING", color = Dim, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("Expand ›", color = Accent, modifier = Modifier.clickable { onExpand() })
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Blue.copy(.5f), RoundedCornerShape(20.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Art(cur, 64.dp)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(cur?.title ?: "কিছু চলছে না", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cur?.artist ?: "", color = Dim, maxLines = 1)
                    Text(if (playing) "Playing" else "Paused", color = Accent, fontSize = 13.sp)
                }
                PlayBtn(playing, 48.dp, onToggle)
            }
        }
        if (recent.isNotEmpty()) {
            item { Text("RECENTLY PLAYED", color = Dim, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recent) { s ->
                        Column(Modifier.width(90.dp).clickable { onPlay(s) }) {
                            Art(s, 90.dp)
                            Text(s.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                            Text(s.artist, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Scan Device Audio", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${songs.size} songs found", color = Dim, fontSize = 13.sp)
                }
                Button(onClick = onScan, colors = ButtonDefaults.buttonColors(containerColor = Blue)) { Text("Scan") }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun StatCard(t: String, s: String, ic: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, tint: Color, m: Modifier) {
    Column(m.clip(RoundedCornerShape(18.dp)).background(bg).padding(14.dp)) {
        Icon(ic, null, tint = tint); Spacer(Modifier.height(10.dp))
        Text(t, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(s, color = Dim, fontSize = 12.sp)
    }
}

@Composable
fun PlayBtn(playing: Boolean, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(Blue).clickable { onClick() }, Alignment.Center) {
        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size / 2))
    }
}
private val CircleShape = androidx.compose.foundation.shape.CircleShape

@Composable
fun MiniPlayer(s: Song, playing: Boolean, fav: Boolean, onToggle: () -> Unit, onNext: () -> Unit, onFav: () -> Unit, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp)).background(Card)
        .border(1.dp, Line, RoundedCornerShape(20.dp)).clickable { onOpen() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Art(s, 48.dp, 10.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(s.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.artist, color = Dim, fontSize = 13.sp, maxLines = 1)
        }
        IconButton(onFav) { Icon(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (fav) Color.Red else Dim) }
        PlayBtn(playing, 44.dp, onToggle)
        IconButton(onNext) { Icon(Icons.Filled.SkipNext, null, tint = Color.White) }
    }
}

@Composable
fun PlayerScreen(s: Song?, playing: Boolean, pos: Long, dur: Long, fav: Boolean, shuffle: Boolean, repeat: Boolean,
                 onToggle: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onSeek: (Long) -> Unit,
                 onFav: () -> Unit, onShuffle: () -> Unit, onRepeat: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("NOW PLAYING", color = Dim, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(20.dp))
        Art(s, 300.dp, 28.dp, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s?.title ?: "—", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s?.artist ?: "", color = Accent, fontSize = 18.sp)
            }
            IconButton(onFav) { Icon(if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (fav) Color.Red else Dim, modifier = Modifier.size(30.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Slider(value = if (dur > 0) pos.toFloat() / dur else 0f, onValueChange = { onSeek((it * dur).toLong()) },
            colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = Line))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(fmt(pos), color = Dim); Text(fmt(dur), color = Dim) }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            IconButton(onShuffle) { Icon(Icons.Filled.Shuffle, null, tint = if (shuffle) Accent else Dim) }
            IconButton(onPrev) { Icon(Icons.Filled.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            PlayBtn(playing, 72.dp, onToggle)
            IconButton(onNext) { Icon(Icons.Filled.SkipNext, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            IconButton(onRepeat) { Icon(Icons.Filled.Repeat, null, tint = if (repeat) Accent else Dim) }
        }
    }
}

@Composable
fun Library(songs: List<Song>, q: String, onQ: (String) -> Unit, favs: Set<Long>, curId: Long?, onScan: () -> Unit,
            onFav: (Long) -> Unit, onPlay: (List<Song>, Int) -> Unit) {
    val list = songs.filter { q.isBlank() || it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("AUDIO COLLECTION", color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("LIBRARY", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
            }
            OutlinedButton(onScan) { Text("+ Scan Audio") }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(q, onQ, Modifier.fillMaxWidth(), placeholder = { Text("Search songs, artists or albums...") }, singleLine = true,
            shape = RoundedCornerShape(16.dp))
        Text("Total ${list.size} songs", color = Dim, modifier = Modifier.padding(vertical = 10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(list, key = { _, s -> s.id }) { i, s ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Card)
                    .border(1.dp, if (s.id == curId) Blue else Line, RoundedCornerShape(18.dp))
                    .clickable { onPlay(list, i) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Art(s, 56.dp, 12.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(s.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.artist} • ${fmt(s.durationMs)}", color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton({ onFav(s.id) }) { Icon(if (s.id in favs) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (s.id in favs) Color.Red else Dim) }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
fun Playlists(favSongs: List<Song>, onPlay: (List<Song>, Int) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("CUSTOM COLLECTIONS", color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("PLAYLISTS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.width(180.dp).clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(20.dp))
            .clickable { if (favSongs.isNotEmpty()) onPlay(favSongs, 0) }.padding(12.dp)) {
            Art(favSongs.firstOrNull(), 156.dp)
            Text("Favourites", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text("${favSongs.size} Songs", color = Accent)
        }
    }
}

@Composable
fun SettingsScreen(onScan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("SETTINGS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 30.sp)
        listOf("FILE MANAGEMENT" to "Local Device Scan", "BACKGROUND & LOCK SCREEN" to "Notification Bar & Lock Screen").forEachIndexed { i, (a, b) ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).border(1.dp, Line, RoundedCornerShape(20.dp))
                .clickable { if (i == 0) onScan() }.padding(16.dp)) {
                Text(a, color = Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(b, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (i == 0) "Rescan phone storage for music files" else "Audio continues when app is closed or screen is off", color = Dim)
            }
        }
    }
}
