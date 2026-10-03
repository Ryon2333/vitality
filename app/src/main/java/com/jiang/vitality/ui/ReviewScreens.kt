package com.jiang.vitality.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jiang.vitality.data.MediaReview
import com.jiang.vitality.data.MediaReviewDraft
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ReviewKinds = listOf("电影", "剧集", "书籍")
private val ReviewStatuses = mapOf(
    "电影" to listOf("想看", "在看", "看过"),
    "剧集" to listOf("想看", "在看", "看过"),
    "书籍" to listOf("想读", "在读", "读过")
)

@Composable
fun ReviewLibrary(
    reviews: List<MediaReview>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (MediaReview) -> Unit,
    onCollapse: () -> Unit
) {
    BackHandler(onBack = onBack)
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    val filtered = reviews.filter { review ->
        (kind == null || review.kind == kind) &&
            (!favoritesOnly || review.favorite) &&
            (query.isBlank() || review.title.contains(query, true) || review.creator.contains(query, true) ||
                review.thoughts.contains(query, true) || review.tags.any { it.contains(query, true) })
    }
    val scroll = rememberAutoCollapseScrollState(onCollapse)
    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ReviewHeader("我评", "电影、剧集与书，留下真正属于你的评价。", onBack)
        ReviewStats(reviews)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(100) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("搜索片名、书名、作者或标签") },
            singleLine = true,
            shape = GlassControlShape,
            colors = glassTextFieldColors()
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassActionButton(if (kind == null && !favoritesOnly) "● 全部" else "全部", { kind = null; favoritesOnly = false })
            ReviewKinds.forEach { item ->
                GlassActionButton(if (kind == item) "● $item" else item, { kind = if (kind == item) null else item; favoritesOnly = false })
            }
            GlassActionButton(if (favoritesOnly) "★ 收藏" else "☆ 收藏", { favoritesOnly = !favoritesOnly })
        }
        if (filtered.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    if (reviews.isEmpty()) "还没有评价。把第一部触动你的作品留在这里。" else "没有符合当前筛选的作品。",
                    color = Muted,
                    modifier = Modifier.padding(22.dp)
                )
            }
        } else {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                maxItemsInEachRow = 2
            ) {
                filtered.forEach { ReviewPosterCard(it, Modifier.weight(1f), onOpen) }
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GlassCard(Modifier.size(68.dp).clickable(onClick = onAdd), shape = CircleShape) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("＋", color = Blue, fontSize = 28.sp) }
            }
        }
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
private fun ReviewStats(reviews: List<MediaReview>) {
    val average = if (reviews.isEmpty()) "—" else "%.1f".format(reviews.map { it.rating }.average())
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceAround) {
            Stat("${reviews.size}", "全部")
            Stat("${reviews.count { it.kind == "电影" || it.kind == "剧集" }}", "影视")
            Stat("${reviews.count { it.kind == "书籍" }}", "书籍")
            Stat(average, "均分")
        }
    }
}

@Composable private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ReviewPosterCard(review: MediaReview, modifier: Modifier, onOpen: (MediaReview) -> Unit) {
    val cover = rememberReviewBitmap(review.imagePaths.firstOrNull(), 420)
    GlassCard(modifier.height(252.dp).clickable { onOpen(review) }, shape = RoundedCornerShape(30.dp), elevation = 11.dp) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))) {
                if (cover != null) Image(cover, review.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (review.kind == "书籍") "BOOK" else "FRAME", color = Blue.copy(.66f), letterSpacing = 2.sp)
                }
                GlassCard(Modifier.align(Alignment.TopEnd).padding(10.dp), shape = CircleShape) {
                    Text("${review.rating}.0", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(review.title, color = Ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(review.kind, review.year, review.status).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 11.sp)
                Text(review.thoughts, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ReviewEditor(
    review: MediaReview?,
    onBack: () -> Unit,
    onSave: (MediaReviewDraft) -> Unit,
    createPhotoFile: () -> File,
    finalizePhoto: (String) -> String?,
    importPhoto: (android.net.Uri) -> String?,
    deletePhoto: (String) -> Unit
) {
    val context = LocalContext.current
    var kind by rememberSaveable(review?.id) { mutableStateOf(review?.kind ?: "电影") }
    var title by rememberSaveable(review?.id) { mutableStateOf(review?.title ?: "") }
    var creator by rememberSaveable(review?.id) { mutableStateOf(review?.creator ?: "") }
    var year by rememberSaveable(review?.id) { mutableStateOf(review?.year ?: "") }
    var rating by rememberSaveable(review?.id) { mutableStateOf(review?.rating ?: 8) }
    var status by rememberSaveable(review?.id) { mutableStateOf(review?.status ?: "想看") }
    var thoughts by rememberSaveable(review?.id) { mutableStateOf(review?.thoughts ?: "") }
    var quotes by rememberSaveable(review?.id) { mutableStateOf(review?.quotes ?: "") }
    var tags by rememberSaveable(review?.id) { mutableStateOf(review?.tags?.joinToString(" ") ?: "") }
    var favorite by rememberSaveable(review?.id) { mutableStateOf(review?.favorite ?: false) }
    val images = remember(review?.id) { mutableStateListOf<String>().apply { addAll(review?.imagePaths.orEmpty()) } }
    val quotePhotos = remember(review?.id) { mutableStateListOf<String>().apply { addAll(review?.quotePhotoPaths.orEmpty()) } }
    val addedPaths = remember(review?.id) { mutableSetOf<String>() }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }

    fun cancel() {
        addedPaths.forEach(deletePhoto)
        onBack()
    }
    BackHandler(onBack = ::cancel)
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.take(12 - images.size).forEach { uri -> importPhoto(uri)?.let { images += it; addedPaths += it } }
    }
    val quoteCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (path != null && ok) {
            val finalized = finalizePhoto(path)
            if (finalized != null) { quotePhotos += finalized; addedPaths += finalized }
            else deletePhoto(path)
        } else if (path != null) deletePhoto(path)
    }
    val quoteGallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.take(12 - quotePhotos.size).forEach { uri -> importPhoto(uri)?.let { quotePhotos += it; addedPaths += it } }
    }
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        ReviewHeader(if (review == null) "写评价" else "编辑评价", "记录作品，也记录当时的你。", ::cancel)
        Selector("作品类型", ReviewKinds, kind) { kind = it; status = ReviewStatuses[it]?.first() ?: "想看" }
        OutlinedTextField(title, { title = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("片名或书名") }, shape = GlassControlShape, colors = glassTextFieldColors())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(creator, { creator = it.take(120) }, Modifier.weight(1f), label = { Text(if (kind == "书籍") "作者" else "导演 / 主创") }, shape = GlassControlShape, colors = glassTextFieldColors())
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, Modifier.width(106.dp), label = { Text("年份") }, shape = GlassControlShape, colors = glassTextFieldColors())
        }
        Selector("进度", ReviewStatuses[kind].orEmpty(), status) { status = it }
        Text("我的评分  $rating / 10", color = Ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..10).forEach { score -> GlassActionButton(if (rating == score) "● $score" else "$score", { rating = score }) }
        }
        OutlinedTextField(
            thoughts, { thoughts = it.take(100_000) }, Modifier.fillMaxWidth().heightIn(min = 190.dp),
            label = { Text("我的感悟") }, placeholder = { Text("哪些镜头、人物或句子留在了你心里？") },
            shape = RoundedCornerShape(28.dp), colors = glassTextFieldColors()
        )
        OutlinedTextField(
            quotes, { quotes = it.take(100_000) }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text(if (kind == "书籍") "书中文摘" else "台词摘录") }, placeholder = { Text("每段摘录可以换行分隔") },
            shape = RoundedCornerShape(28.dp), colors = glassTextFieldColors()
        )
        OutlinedTextField(tags, { tags = it.take(400) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("标签") }, placeholder = { Text("剧情  成长  女性") }, shape = GlassControlShape, colors = glassTextFieldColors())
        Text("剧照与封面", color = Ink, fontWeight = FontWeight.Bold)
        PhotoStrip(images, "导入剧照 / 封面", { gallery.launch("image/*") }) { path -> images.remove(path); if (path in addedPaths) { deletePhoto(path); addedPaths.remove(path) } }
        Text(if (kind == "书籍") "拍下书中文摘" else "拍下台词或画面", color = Ink, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassActionButton("相机", {
                val file = createPhotoFile()
                pendingCameraPath = file.absolutePath
                quoteCamera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
            })
            GlassActionButton("相册", { quoteGallery.launch("image/*") })
        }
        PhotoStrip(quotePhotos, "还没有摘录照片", {}, showAdd = false) { path -> quotePhotos.remove(path); if (path in addedPaths) { deletePhoto(path); addedPaths.remove(path) } }
        GlassOutlinedButton({ favorite = !favorite }, Modifier.fillMaxWidth()) { Text(if (favorite) "★ 已收藏" else "☆ 加入收藏", color = if (favorite) RecoveryCoral else Blue) }
        RecordStateButton(
            text = "保存评价",
            enabled = title.isNotBlank() && thoughts.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onSave(MediaReviewDraft(
                    id = review?.id, kind = kind, title = title, creator = creator, year = year,
                    rating = rating, status = status, thoughts = thoughts, quotes = quotes,
                    tags = tags.split(Regex("[，,\\s]+")), imagePaths = images.toList(),
                    quotePhotoPaths = quotePhotos.toList(), favorite = favorite
                ))
                addedPaths.clear()
            }
        )
        Spacer(Modifier.height(130.dp))
    }
}

@Composable
private fun Selector(label: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Ink, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { GlassActionButton(if (it == selected) "● $it" else it, { onSelect(it) }) }
        }
    }
}

@Composable
private fun PhotoStrip(
    paths: List<String>,
    emptyLabel: String,
    onAdd: () -> Unit,
    showAdd: Boolean = true,
    onRemove: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        paths.forEach { path ->
            val bitmap = rememberReviewBitmap(path, 280)
            Box(Modifier.size(108.dp).clip(RoundedCornerShape(24.dp))) {
                if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                GlassCard(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clickable { onRemove(path) }, shape = CircleShape) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("×", color = Ink) }
                }
            }
        }
        if (showAdd) GlassCard(Modifier.size(108.dp).clickable(onClick = onAdd), shape = RoundedCornerShape(24.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (paths.isEmpty()) emptyLabel else "＋", color = Blue, fontSize = if (paths.isEmpty()) 11.sp else 25.sp) }
        }
        if (paths.isEmpty() && !showAdd) Text(emptyLabel, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun ReviewDetail(
    review: MediaReview,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    BackHandler(onBack = onBack)
    var confirmDelete by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val cover = rememberReviewBitmap(review.imagePaths.firstOrNull(), 900)
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        ReviewHeader("我评", review.updatedAt.reviewDate(), onBack)
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(36.dp)) {
            Column {
                if (cover != null) Image(cover, review.title, Modifier.fillMaxWidth().aspectRatio(1.55f).clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(review.title, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(listOf(review.kind, review.creator, review.year, review.status).filter { it.isNotBlank() }.joinToString(" · "), color = Muted)
                    Text("${review.rating}.0", color = Blue, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    if (review.tags.isNotEmpty()) Text(review.tags.joinToString("  ") { "#$it" }, color = Blue, fontSize = 12.sp)
                }
            }
        }
        ReviewTextCard("我的感悟", review.thoughts)
        if (review.quotes.isNotBlank()) ReviewTextCard(if (review.kind == "书籍") "书中文摘" else "台词摘录", review.quotes)
        if (review.imagePaths.size > 1) {
            Text("剧照", color = Ink, fontWeight = FontWeight.Bold)
            DetailPhotoRail(review.imagePaths.drop(1))
        }
        if (review.quotePhotoPaths.isNotEmpty()) {
            Text("摘录影像", color = Ink, fontWeight = FontWeight.Bold)
            DetailPhotoRail(review.quotePhotoPaths)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassOutlinedButton(onEdit, Modifier.weight(1f)) { Text("编辑", color = Blue) }
            GlassOutlinedButton({ confirmDelete = true }, Modifier.weight(1f)) { Text("删除", color = RecoveryCoral) }
        }
        Spacer(Modifier.height(130.dp))
    }
    if (confirmDelete) GlassDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("删除这条评价？") },
        text = { Text("评价、剧照和摘录照片都会被永久删除。") },
        actions = {
            GlassActionButton("取消", { confirmDelete = false })
            GlassActionButton("确认删除", onDelete)
        }
    )
}

@Composable private fun ReviewTextCard(title: String, body: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(body, color = Ink, lineHeight = 24.sp)
        }
    }
}

@Composable private fun DetailPhotoRail(paths: List<String>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        paths.forEach { path ->
            rememberReviewBitmap(path, 520)?.let { bitmap ->
                Image(bitmap, null, Modifier.width(220.dp).height(150.dp).clip(RoundedCornerShape(26.dp)), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
private fun ReviewHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(Modifier.size(48.dp).clickable(onClick = onBack), shape = CircleShape) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("‹", color = Ink, fontSize = 32.sp) }
        }
        GlassPageHeader(title, Modifier.weight(1f), subtitle)
    }
}

@Composable
private fun rememberReviewBitmap(path: String?, max: Int) = remember(path, max) {
    if (path.isNullOrBlank()) null else runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / sample > max * 2 || bounds.outHeight / sample > max * 2) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()
}

private fun Long.reviewDate(): String = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("yyyy年M月d日 · HH:mm"))
