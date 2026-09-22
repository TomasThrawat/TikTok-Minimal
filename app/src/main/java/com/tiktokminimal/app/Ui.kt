
package com.tiktokminimal.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File

private val PureBlack = Color.Black
private val PrimaryWhite = Color.White
private val MutedWhite = Color(0xFFB0B0B0)

@Composable
fun AppRoot(vm: AppViewModel) {
    val authenticated by vm.authenticated.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(ui.error, ui.success) {
        ui.error?.let { snackbarHostState.showSnackbar(it) }
        ui.success?.let { snackbarHostState.showSnackbar(it) }
        vm.clearMessages()
    }

    Box(Modifier.fillMaxSize().background(PureBlack)) {
        when {
            !SupabaseProvider.isConfigured() -> ConfigurationMissingScreen()
            !authenticated -> AuthScreen(vm)
            else -> MainShell(vm)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun ConfigurationMissingScreen() {
    Column(
        Modifier.fillMaxSize().background(PureBlack).padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("TikTok Minimal", color = PrimaryWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to local.properties or the build environment.",
            color = MutedWhite
        )
    }
}

@Composable
private fun AuthScreen(vm: AppViewModel) {
    var signUp by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(PureBlack)
            .padding(horizontal = 28.dp, vertical = 44.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("TikTok Minimal", color = PrimaryWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(if (signUp) "Create your account" else "Sign in", color = MutedWhite)
        Spacer(Modifier.height(28.dp))

        if (signUp) {
            AppField(username, "Username") { username = it }
            Spacer(Modifier.height(10.dp))
        }

        AppField(email, "Email") { email = it }
        Spacer(Modifier.height(10.dp))
        AppField(
            value = password,
            label = "Password",
            visualTransformation = PasswordVisualTransformation(),
            onValueChange = { password = it }
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                if (signUp) vm.signUp(email, password, username)
                else vm.signIn(email, password)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (signUp) "Create account" else "Sign in")
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = vm::signInWithGoogle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue with Google")
        }

        Spacer(Modifier.height(18.dp))

        TextButton(onClick = { signUp = !signUp }) {
            Text(
                if (signUp) "Already have an account? Sign in"
                else "New here? Create an account",
                color = PrimaryWhite
            )
        }
    }
}

@Composable
private fun MainShell(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = PureBlack,
        bottomBar = { BottomNav(tab) { tab = it } },
        contentWindowInsets = WindowInsets.navigationBars
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> HomeScreen(vm)
                1 -> DiscoverScreen(vm)
                2 -> CreateScreen(vm)
                3 -> InboxScreen(vm)
                else -> ProfileScreen(vm)
            }
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelected: (Int) -> Unit) {
    val items = listOf("Home", "Discover", "Create", "Inbox", "Profile")

    Row(
        Modifier
            .fillMaxWidth()
            .background(PureBlack)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEachIndexed { index, label ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelected(index) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    when (index) {
                        0 -> "⌂"
                        1 -> "⌕"
                        2 -> "+"
                        3 -> "✉"
                        else -> "○"
                    },
                    color = if (selected == index) PrimaryWhite else MutedWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    label,
                    color = if (selected == index) PrimaryWhite else MutedWhite
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(vm: AppViewModel) {
    val feed by vm.feed.collectAsStateWithLifecycle()
    val liked by vm.liked.collectAsStateWithLifecycle()
    val following by vm.following.collectAsStateWithLifecycle()
    val loadingMore by vm.loadingMore.collectAsStateWithLifecycle()
    val loading by vm.ui.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { feed.size })
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var commentsVideo by remember { mutableStateOf<String?>(null) }
    var isPlaying by rememberSaveable { mutableStateOf(true) }
    val player = rememberCachedVideoPlayer(context)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { index ->
            feed.getOrNull(index)?.let { vm.checkFollowing(it.userId) }
            if (index >= feed.lastIndex - 2) vm.loadMoreFeed()
        }
    }

    LaunchedEffect(feed, pagerState.currentPage) {
        val index = pagerState.currentPage
        if (index !in feed.indices) return@LaunchedEffect

        val items = buildList {
            add(MediaItem.fromUri(feed[index].storagePath.toPlaybackUrl()))
            feed.getOrNull(index + 1)?.let {
                add(MediaItem.fromUri(it.storagePath.toPlaybackUrl()))
            }
        }

        player.setMediaItems(items)
        player.seekToDefaultPosition(0)
        player.prepare()
        player.playWhenReady = true
        isPlaying = true
    }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.pause()
                Lifecycle.Event.ON_START -> if (feed.isNotEmpty()) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(PureBlack)) {
        if (feed.isEmpty()) {
            EmptyFeed(loading.loading, vm::refreshFeed)
        } else {
            AndroidPlayerView(player, Modifier.fillMaxSize())

            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                val item = feed[page]
                FeedOverlay(
                    item = item,
                    liked = item.id in liked,
                    following = item.userId in following,
                    isPlaying = isPlaying,
                    onToggleLike = { vm.toggleLike(item.id) },
                    onComments = {
                        commentsVideo = item.id
                        vm.openComments(item.id)
                    },
                    onShare = {
                        vm.recordShare(item.id)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, item.storagePath.toPlaybackUrl())
                        }
                        context.startActivity(Intent.createChooser(send, "Share video"))
                    },
                    onFollow = { vm.toggleFollow(item.userId) },
                    onTogglePlay = {
                        if (isPlaying) player.pause() else player.play()
                        isPlaying = !isPlaying
                    }
                )
            }
        }

        if (loadingMore) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                color = PrimaryWhite
            )
        }
    }

    commentsVideo?.let { videoId ->
        CommentsSheet(vm, videoId) { commentsVideo = null }
    }
}

@Composable
private fun EmptyFeed(loading: Boolean, onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No videos yet", color = PrimaryWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Upload the first real video from Create.", color = MutedWhite)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRefresh, enabled = !loading) {
            Text("Refresh")
        }
    }
}

@Composable
private fun FeedOverlay(
    item: FeedVideo,
    liked: Boolean,
    following: Boolean,
    isPlaying: Boolean,
    onToggleLike: () -> Unit,
    onComments: () -> Unit,
    onShare: () -> Unit,
    onFollow: () -> Unit,
    onTogglePlay: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 86.dp, bottom = 22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(item.avatarUrl, 36.dp)
                Spacer(Modifier.width(8.dp))
                Text("@" + item.username, color = PrimaryWhite, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onFollow) {
                    Text(
                        if (following) "Following" else "Follow",
                        color = PrimaryWhite
                    )
                }
            }

            Spacer(Modifier.height(7.dp))

            if (item.caption.isNotBlank()) {
                Text(
                    item.caption,
                    color = PrimaryWhite,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
            }

            if (item.hashtags.isNotEmpty()) {
                Text(
                    item.hashtags.joinToString(" ") { "#" + it },
                    color = PrimaryWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 10.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FeedAction("♡", item.likeCount.toString(), liked, onToggleLike)
            FeedAction("◌", item.commentCount.toString(), false, onComments)
            FeedAction("↗", item.shareCount.toString(), false, onShare)

            TextButton(onClick = onTogglePlay) {
                Text(
                    if (isPlaying) "Pause" else "Play",
                    color = PrimaryWhite
                )
            }
        }
    }
}

@Composable
private fun FeedAction(
    icon: String,
    count: String,
    active: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                icon,
                color = if (active) PrimaryWhite else MutedWhite,
                fontWeight = FontWeight.Bold
            )
            Text(count, color = PrimaryWhite)
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun AndroidPlayerView(player: ExoPlayer, modifier: Modifier) {
    val context = LocalContext.current

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(context).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = player }
    )
}

private data class PlayerHolder(
    val player: ExoPlayer,
    val cache: SimpleCache
)

@Composable
private fun rememberCachedVideoPlayer(context: android.content.Context): ExoPlayer {
    val holder = remember {
        val cacheDir = File(context.cacheDir, "video-cache")
        val database = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(128L * 1024L * 1024L),
            database
        )
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("TikTokMinimal/1.0")

        val sourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(http)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(sourceFactory))
            .build()

        PlayerHolder(player, cache)
    }

    DisposableEffect(holder.player) {
        onDispose {
            holder.player.release()
            holder.cache.release()
        }
    }

    return holder.player
}

@Composable
private fun rememberLocalPreviewPlayer(uri: Uri): ExoPlayer {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = false
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    return player
}

@Composable
private fun LocalVideoPreview(uri: Uri) {
    val player = rememberLocalPreviewPlayer(uri)
    Box(
        Modifier
            .fillMaxWidth()
            .height(250.dp)
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
    ) {
        AndroidPlayerView(player, Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheet(
    vm: AppViewModel,
    videoId: String,
    onDismiss: () -> Unit
) {
    val comments by vm.comments.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var body by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PureBlack
    ) {
        Column(Modifier.fillMaxWidth().imePadding().padding(16.dp)) {
            Text("Comments", color = PrimaryWhite, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(comments, key = { it.id }) { comment ->
                    Row(verticalAlignment = Alignment.Top) {
                        ProfileAvatar(comment.avatarUrl, 30.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                "@" + comment.username,
                                color = PrimaryWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(comment.body, color = MutedWhite)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = body,
                    onValueChange = { if (it.length <= 2000) body = it },
                    label = { Text("Add a comment") }
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        vm.addComment(videoId, body) { body = "" }
                    }
                ) {
                    Text("Post")
                }
            }
        }
    }
}

@Composable
private fun DiscoverScreen(vm: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val search by vm.search.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = query,
            onValueChange = {
                query = it
                vm.search(it)
            },
            singleLine = true,
            label = { Text("Search people, captions, or #hashtags") }
        )

        Spacer(Modifier.height(16.dp))

        if (query.isBlank()) {
            Text("Discover", color = PrimaryWhite, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Search live backend content by username, caption, or hashtag.",
                color = MutedWhite
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(search.profiles, key = { "p-" + it.id }) { profile ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileAvatar(profile.avatarUrl, 42.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "@" + profile.username,
                                color = PrimaryWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(profile.bio, color = MutedWhite, maxLines = 1)
                        }
                    }
                }

                items(search.videos, key = { "v-" + it.id }) { video ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "@" + video.username,
                                color = PrimaryWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(video.caption, color = MutedWhite, maxLines = 2)
                            if (video.hashtags.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    video.hashtags.joinToString(" ") { "#" + it },
                                    color = PrimaryWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateScreen(vm: AppViewModel) {
    var selectedUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var caption by rememberSaveable { mutableStateOf("") }
    var hashtagsText by rememberSaveable { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var uploading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        selectedUri = uri
        progress = 0f
    }

    Column(
        Modifier.fillMaxSize().background(PureBlack).padding(16.dp)
    ) {
        Text("Create", color = PrimaryWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = { picker.launch("video/*") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uploading
        ) {
            Text(
                if (selectedUri == null) "Choose video"
                else vm.selectedFileName(selectedUri!!)
            )
        }

        Spacer(Modifier.height(12.dp))

        selectedUri?.let {
            LocalVideoPreview(it)
            Spacer(Modifier.height(12.dp))
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = caption,
            onValueChange = { if (it.length <= 500) caption = it },
            label = { Text("Caption") },
            enabled = !uploading
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = hashtagsText,
            onValueChange = { hashtagsText = it },
            label = { Text("Hashtags, separated by spaces") },
            enabled = !uploading
        )

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                val hashtags = hashtagsText
                    .split(Regex("\\s+"))
                    .map { it.removePrefix("#").trim().lowercase() }
                    .filter { it.matches(Regex("^[a-z0-9_.-]{1,64}$")) }
                    .distinct()
                    .take(50)

                uploading = true
                vm.uploadVideo(
                    uri = selectedUri!!,
                    caption = caption.trim(),
                    hashtags = hashtags,
                    onProgress = { progress = it },
                    onComplete = {
                        selectedUri = null
                        caption = ""
                        hashtagsText = ""
                        progress = 0f
                    },
                    onFinished = {
                        uploading = false
                        if (progress >= 1f) progress = 0f
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedUri != null && !uploading
        ) {
            Text(if (uploading) "Publishing..." else "Publish")
        }

        if (uploading) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Upload " + (progress * 100f).toInt() + "%",
                color = PrimaryWhite
            )
            CircularProgressIndicator(
                progress = { progress },
                color = PrimaryWhite
            )
        }
    }
}

@Composable
private fun InboxScreen(vm: AppViewModel) {
    val notifications by vm.notifications.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.loadNotifications()
    }

    Column(Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("Inbox", color = PrimaryWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (notifications.isEmpty()) {
            Text("No notifications yet.", color = MutedWhite)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notifications, key = { it.id }) { n ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            when (n.type) {
                                "follow" -> "Someone followed you"
                                "like" -> "Someone liked your video"
                                "comment" -> "Someone commented on your video"
                                "share" -> "Someone shared your video"
                                else -> "New notification"
                            },
                            color = PrimaryWhite
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(vm: AppViewModel) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(profile?.id) {
        profile?.let {
            username = it.username
            bio = it.bio
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let(vm::uploadAvatar)
    }

    Column(Modifier.fillMaxSize().background(PureBlack).padding(16.dp)) {
        Text("Profile", color = PrimaryWhite, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(profile?.avatarUrl, 74.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "@" + (profile?.username ?: ""),
                    color = PrimaryWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(profile?.bio.orEmpty(), color = MutedWhite)
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = true }) { Text("Edit") }
            Button(onClick = { avatarPicker.launch("image/*") }) { Text("Avatar") }
            Button(onClick = vm::signOut) { Text("Sign out") }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateProfile(username, bio)
                    editing = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Edit profile") },
            text = {
                Column {
                    AppField(username, "Username") { username = it }
                    Spacer(Modifier.height(8.dp))
                    AppField(
                        value = bio,
                        label = "Bio",
                        singleLine = false,
                        onValueChange = { bio = it }
                    )
                }
            }
        )
    }
}

@Composable
private fun ProfileAvatar(url: String?, size: Dp) {
    Box(
        Modifier
            .size(size)
            .background(Color(0xFF1D1D1D), CircleShape)
            .border(1.dp, Color(0xFF444444), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("○", color = MutedWhite)
        }
    }
}

@Composable
private fun AppField(
    value: String,
    label: String,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        visualTransformation = visualTransformation
    )
}

private fun String.toPlaybackUrl(): String {
    return BuildConfig.SUPABASE_URL.trimEnd('/') +
        "/storage/v1/object/public/videos/" + this
}
