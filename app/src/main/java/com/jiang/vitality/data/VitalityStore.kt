package com.jiang.vitality.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.provider.OpenableColumns
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class Reading(
    val time: Long,
    val value: Int,
    val note: String = "",
    val photoPaths: List<String> = emptyList()
)
data class ImportSummary(val readings: Int, val photos: Int)
data class AiConversation(
    val id: Long,
    val createdAt: Long,
    val title: String,
    val answer: String,
    val tags: List<String> = emptyList(),
    val category: String = "未分类",
    val favorite: Boolean = false
)
data class MediaReview(
    val id: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val kind: String,
    val title: String,
    val creator: String = "",
    val year: String = "",
    val rating: Int,
    val status: String,
    val thoughts: String,
    val quotes: String = "",
    val tags: List<String> = emptyList(),
    val imagePaths: List<String> = emptyList(),
    val quotePhotoPaths: List<String> = emptyList(),
    val favorite: Boolean = false
)
data class MediaReviewDraft(
    val id: Long? = null,
    val kind: String,
    val title: String,
    val creator: String,
    val year: String,
    val rating: Int,
    val status: String,
    val thoughts: String,
    val quotes: String,
    val tags: List<String>,
    val imagePaths: List<String>,
    val quotePhotoPaths: List<String>,
    val favorite: Boolean = false
)
data class Reminder(val id: Int, val title: String, val hour: Int, val minute: Int, val enabled: Boolean = true) {
    val clock: String get() = "%02d:%02d".format(hour, minute)
}
data class Snapshot(
    val value: Int, val baseline: Int, val locked: Boolean, val unlockAt: Long,
    val readings: List<Reading>, val reminders: List<Reminder>, val rests: List<String>,
    val week: List<Int?>
)

object Advice {
    fun label(v: Int) = when { v >= 90 -> "巅峰"; v >= 70 -> "良好"; v >= 40 -> "疲劳"; v >= 20 -> "低能量"; else -> "需要恢复" }
    fun message(v: Int) = when { v >= 80 -> "Ready to create."; v >= 50 -> "Keep the rhythm."; else -> "Recovery matters." }
    fun suggestion(v: Int) = when { v >= 90 -> "适合推进最重要的任务，留意休息间隔。"; v >= 70 -> "适合学习、写代码和处理难题。"; v >= 40 -> "先完成轻量任务，适时走动和补水。"; v >= 20 -> "放慢节奏，散步或闭眼休息一会儿。"; else -> "暂停高负荷任务，优先恢复精力。" }
}

class VitalityStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("vitality_v2", Context.MODE_PRIVATE)
    private val photosDir = File(appContext.filesDir, "memories").apply { mkdirs() }
    private val musicDir = File(appContext.filesDir, "music").apply { mkdirs() }
    private val defaults = listOf("闭眼休息", "拉伸", "散步", "喝水", "深呼吸", "听音乐", "冥想", "远眺")

    init { ensureDemoContent() }

    fun demoActive(): Boolean = prefs.getBoolean("demo_active", false)

    @Synchronized private fun ensureDemoContent() {
        if (prefs.getBoolean("demo_initialized", false)) return
        val now = System.currentTimeMillis()
        val demoPhoto = createDemoImage("A QUIET DAY", 0xFF245880.toInt(), 0xFFFA9C3A.toInt())
        val demoCover = createDemoImage("THE SEA BETWEEN US", 0xFF144BB0.toInt(), 0xFFC3B8F8.toInt())
        val demoQuote = createDemoImage("我们在浪声里，重新学会安静。", 0xFF750C26.toInt(), 0xFFEC6D4A.toInt())
        val readingTimes = listOf(now - 86_400_000L, now - 2 * 86_400_000L, now - 5 * 86_400_000L)
        val demoReadings = listOf(
            Reading(readingTimes[0], 72, "傍晚散步后，注意力慢慢回来了。", listOf(demoPhoto)),
            Reading(readingTimes[1], 48, "今天适合把速度放慢，先照顾好状态。"),
            Reading(readingTimes[2], 86, "完成了最重要的一件事，也留出了休息的时间。")
        )
        prefs.edit().putString("readings", readingsJson((readings() + demoReadings).sortedBy { it.time }).toString()).commit()

        val aiId = now - 11_003L
        saveAiConversations(aiConversations() + AiConversation(
            id = aiId,
            createdAt = now - 3_600_000L,
            title = "怎样判断今天应该继续，还是停下来休息？",
            answer = "## 先看身体给出的证据\n\n- 注意力是否持续飘走\n- 同一件事是否反复出错\n- 是否已经用意志力顶了很久\n\n如果三个信号同时出现，休息通常比硬撑更有效。",
            tags = listOf("状态", "休息"),
            category = "生活",
            favorite = true
        ))

        val reviewId = now - 22_007L
        saveMediaReviews(mediaReviews() + MediaReview(
            id = reviewId,
            createdAt = now - 7_200_000L,
            updatedAt = now - 7_200_000L,
            kind = "电影",
            title = "海岸线以外",
            creator = "示范导演",
            year = "2026",
            rating = 9,
            status = "看过",
            thoughts = "它没有急着给答案，而是让海浪、停顿和人物的呼吸慢慢构成答案。",
            quotes = "我们不是停在原地，只是在学习怎样不耗尽自己。",
            tags = listOf("治愈", "成长", "海洋"),
            imagePaths = listOf(demoCover),
            quotePhotoPaths = listOf(demoQuote),
            favorite = true
        ))

        val demoMusic = "江 · 海边呼吸（示范）.wav"
        createDemoWave(File(musicDir, demoMusic))
        saveMusicNames((musicNames() + demoMusic).distinct())
        prefs.edit()
            .putBoolean("demo_initialized", true)
            .putBoolean("demo_active", true)
            .putString("demo_reading_times", JSONArray(readingTimes).toString())
            .putString("demo_ai_ids", JSONArray(listOf(aiId)).toString())
            .putString("demo_review_ids", JSONArray(listOf(reviewId)).toString())
            .putString("demo_photo_paths", JSONArray(listOf(demoPhoto, demoCover, demoQuote)).toString())
            .putString("demo_music_names", JSONArray(listOf(demoMusic)).toString())
            .commit()
    }

    @Synchronized fun clearDemoContent(): Boolean {
        if (!demoActive()) return true
        val readingTimes = runCatching { JSONArray(prefs.getString("demo_reading_times", "[]")).toLongSet() }.getOrDefault(emptySet())
        val aiIds = runCatching { JSONArray(prefs.getString("demo_ai_ids", "[]")).toLongSet() }.getOrDefault(emptySet())
        val reviewIds = runCatching { JSONArray(prefs.getString("demo_review_ids", "[]")).toLongSet() }.getOrDefault(emptySet())
        val photoPaths = runCatching { JSONArray(prefs.getString("demo_photo_paths", "[]")).toStringList() }.getOrDefault(emptyList())
        val musicNames = runCatching { JSONArray(prefs.getString("demo_music_names", "[]")).toStringList() }.getOrDefault(emptyList())
        val editor = prefs.edit()
            .putString("readings", readingsJson(readings().filterNot { it.time in readingTimes }).toString())
            .putBoolean("demo_active", false)
            .remove("demo_reading_times").remove("demo_ai_ids").remove("demo_review_ids")
            .remove("demo_photo_paths").remove("demo_music_names")
        val aiArray = JSONArray()
        aiConversations().filterNot { it.id in aiIds }.forEach { talk ->
            aiArray.put(JSONObject().put("id", talk.id).put("createdAt", talk.createdAt).put("title", talk.title)
                .put("answer", talk.answer).put("tags", JSONArray(talk.tags)).put("category", talk.category).put("favorite", talk.favorite))
        }
        val reviewArray = JSONArray()
        mediaReviews().filterNot { it.id in reviewIds }.forEach { reviewArray.put(reviewJson(it, includePaths = true)) }
        editor.putString("ai_talks", aiArray.toString()).putString("media_reviews", reviewArray.toString())
        val saved = editor.commit()
        if (saved) {
            photoPaths.forEach(::deletePhoto)
            musicNames.forEach(::deleteMusic)
        }
        return saved
    }

    private fun JSONArray.toLongSet(): Set<Long> = buildSet { for (index in 0 until length()) add(optLong(index)) }

    private fun createDemoImage(label: String, startColor: Int, endColor: Int): String {
        val target = createPhotoFile()
        val bitmap = android.graphics.Bitmap.createBitmap(900, 620, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.LinearGradient(0f, 0f, 900f, 620f, startColor, endColor, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 900f, 620f, paint)
        paint.shader = null
        paint.color = android.graphics.Color.WHITE
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.textSize = if (label.length > 24) 34f else 52f
        paint.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        canvas.drawText(label, 450f, 330f, paint)
        FileOutputStream(target).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return target.absolutePath
    }

    private fun createDemoWave(target: File) {
        val sampleRate = 22_050
        val seconds = 16
        val samples = sampleRate * seconds
        val dataSize = samples * 2
        val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        buffer.putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(dataSize)
        repeat(samples) { index ->
            val t = index.toDouble() / sampleRate
            val envelope = .28 + .12 * kotlin.math.sin(2.0 * Math.PI * .08 * t)
            val wave = kotlin.math.sin(2.0 * Math.PI * 174.0 * t) * .55 + kotlin.math.sin(2.0 * Math.PI * 261.0 * t) * .18
            buffer.putShort((wave * envelope * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        }
        target.writeBytes(buffer.array())
    }

    @Synchronized fun snapshot(now: Long = System.currentTimeMillis()): Snapshot {
        finishIfNeeded(now)
        val readings = readings()
        val start = prefs.getLong("recovery_start", 0L)
        val end = prefs.getLong("recovery_end", 0L)
        val locked = start > 0 && now < end
        val value = if (locked) (prefs.getInt("recovery_base", 100) + (ChronoUnit.HOURS.between(Instant.ofEpochMilli(start), Instant.ofEpochMilli(now)).toInt().coerceAtLeast(0) * 2)).coerceAtMost(100)
                    else prefs.getInt("value", prefs.getInt("baseline", 100))
        val week = MutableList<Int?>(7) { null }
        val today = LocalDate.now()
        readings.forEach { entry ->
            val date = Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault()).toLocalDate()
            val ago = ChronoUnit.DAYS.between(date, today).toInt()
            if (ago in 0..6) week[6 - ago] = entry.value
        }
        if (locked) week[6] = value
        return Snapshot(value, prefs.getInt("baseline", 100), locked, end, readings, reminders(), rests(), week)
    }
    @Synchronized fun record(value: Int, note: String = "", photoPaths: List<String> = emptyList()): Boolean {
        if (snapshot().locked) return false
        val v = value.coerceIn(0, 100)
        val all = readings().takeLast(4999) + Reading(
            System.currentTimeMillis(),
            v,
            note.trim().take(1000),
            photoPaths.filter { it.isNotBlank() }
        )
        prefs.edit().putInt("value", v).putString("readings", readingsJson(all).toString()).commit()
        return true
    }
    @Synchronized fun adjust(delta: Int) = record((snapshot().value + delta).coerceIn(0, 100))

    /** Saves one recovery photo as a journal entry and awards exactly one vitality point. */
    @Synchronized fun recordRecoveryPhoto(path: String): Boolean {
        val current = snapshot()
        if (!current.locked || path.isBlank() || !File(path).isFile) return false
        val base = prefs.getInt("recovery_base", current.value)
        val nextValue = (current.value + 1).coerceAtMost(100)
        val all = readings().takeLast(4999) + Reading(
            time = System.currentTimeMillis(),
            value = nextValue,
            note = "休息时拍下的一刻",
            photoPaths = listOf(path)
        )
        return prefs.edit()
            .putInt("recovery_base", (base + 1).coerceAtMost(100))
            .putString("readings", readingsJson(all).toString())
            .commit()
    }

    /** Removes a single photo from a reading while preserving the reading and its other photos. */
    @Synchronized fun deleteReadingPhoto(readingTime: Long, path: String): Boolean {
        val current = readings()
        val target = current.firstOrNull { it.time == readingTime && path in it.photoPaths } ?: return false
        val updated = current.map { entry ->
            if (entry.time == target.time) entry.copy(photoPaths = entry.photoPaths.filterNot { it == path }) else entry
        }
        val saved = prefs.edit().putString("readings", readingsJson(updated).toString()).commit()
        if (saved) deletePhoto(path)
        return saved
    }
    @Synchronized fun setBaseline(value: Int) {
        val v = value.coerceIn(0, 100)
        val edit = prefs.edit().putInt("baseline", v)
        if (!prefs.contains("readings") && !prefs.contains("recovery_start")) edit.putInt("value", v)
        edit.commit()
    }
    @Synchronized fun beginRecovery(): Boolean {
        val current = snapshot()
        if (current.locked) return false
        val now = System.currentTimeMillis()
        val end = LocalDate.now().plusDays(1).atTime(6, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        prefs.edit().putLong("recovery_start", now).putLong("recovery_end", end).putInt("recovery_base", current.value).commit()
        return true
    }
    @Synchronized fun endRecoveryEarly(): Boolean {
        val now = System.currentTimeMillis()
        val current = snapshot(now)
        if (!current.locked) return false
        val all = readings().takeLast(4999) + Reading(
            time = now,
            value = current.value,
            note = "提前结束休息模式，切回工作状态"
        )
        return prefs.edit()
            .putInt("value", current.value)
            .putString("readings", readingsJson(all).toString())
            .remove("recovery_start")
            .remove("recovery_end")
            .remove("recovery_base")
            .commit()
    }
    @Synchronized private fun finishIfNeeded(now: Long) {
        val start = prefs.getLong("recovery_start", 0L)
        val end = prefs.getLong("recovery_end", 0L)
        if (start == 0L || now < end) return
        val hours = ChronoUnit.HOURS.between(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end)).toInt().coerceAtLeast(0)
        val v = (prefs.getInt("recovery_base", 100) + hours * 2).coerceAtMost(100)
        val all = readings() + Reading(end, v, "休息模式结束")
        prefs.edit().putInt("value", v).putString("readings", readingsJson(all).toString())
            .remove("recovery_start").remove("recovery_end").remove("recovery_base").commit()
    }
    fun readings(): List<Reading> = try {
        val arr = JSONArray(prefs.getString("readings", "[]")); List(arr.length()) { i ->
            val item = arr.getJSONObject(i); Reading(
                item.getLong("time"),
                item.getInt("value"),
                item.optString("note"),
                parsePhotoPaths(item)
            )
        }
    } catch (_: Exception) { emptyList() }
    private fun parsePhotoPaths(item: JSONObject): List<String> {
        val array = item.optJSONArray("photoPaths")
        if (array != null) return List(array.length()) { array.getString(it) }.filter { it.isNotBlank() }
        val legacy = item.optString("photoPath")
        return if (legacy.isNotBlank()) listOf(legacy) else emptyList()
    }
    fun reminders(): List<Reminder> {
        if (!prefs.contains("reminders")) return listOf(Reminder(1,"起床后",8,30), Reminder(2,"午睡后",13,30), Reminder(3,"晚餐后",19,0), Reminder(4,"睡前",23,0))
        return try { val arr = JSONArray(prefs.getString("reminders","[]")); List(arr.length()) { i ->
            val o = arr.getJSONObject(i); Reminder(o.getInt("id"),o.getString("title"),o.getInt("hour"),o.getInt("minute"),o.getBoolean("enabled"))
        } } catch (_: Exception) { emptyList() }
    }
    @Synchronized fun saveReminders(items: List<Reminder>) {
        val arr=JSONArray(); items.forEach { arr.put(JSONObject().put("id",it.id).put("title",it.title).put("hour",it.hour).put("minute",it.minute).put("enabled",it.enabled)) }
        prefs.edit().putString("reminders",arr.toString()).commit()
    }
    fun rests(): List<String> {
        if (!prefs.contains("rests")) return defaults
        return try { val arr = JSONArray(prefs.getString("rests", "[]")); List(arr.length()) { arr.getString(it) } } catch (_: Exception) { defaults }
    }
    @Synchronized fun saveRests(items: List<String>) {
        val arr=JSONArray(); items.distinct().filter { it.isNotBlank() }.forEach { arr.put(it) }
        prefs.edit().putString("rests",arr.toString()).commit()
    }

    fun paleontologyDiscoveries(): List<FossilDiscovery> = try {
        val array = JSONArray(prefs.getString("paleo_discoveries", "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            FossilDiscovery(item.getString("creatureId"), item.getLong("discoveredAt"))
        }.filter { it.creatureId in PaleontologyCatalog.byId }
            .sortedByDescending { it.discoveredAt }
    } catch (_: Exception) { emptyList() }

    fun todayPaleontologyDiscovery(now: Long = System.currentTimeMillis()): FossilDiscovery? {
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        return paleontologyDiscoveries().firstOrNull {
            Instant.ofEpochMilli(it.discoveredAt).atZone(ZoneId.systemDefault()).toLocalDate() == today
        }
    }

    @Synchronized fun discoverPaleontology(now: Long = System.currentTimeMillis()): FossilDiscovery? {
        todayPaleontologyDiscovery(now)?.let { return null }
        val existing = paleontologyDiscoveries()
        val discoveredIds = existing.mapTo(mutableSetOf()) { it.creatureId }
        val available = PaleontologyCatalog.all.filterNot { it.id in discoveredIds }
        val creature = (available.ifEmpty { PaleontologyCatalog.all }).random()
        val discovery = FossilDiscovery(creature.id, now)
        val array = JSONArray()
        (existing + discovery).sortedBy { it.discoveredAt }.takeLast(5_000).forEach {
            array.put(JSONObject().put("creatureId", it.creatureId).put("discoveredAt", it.discoveredAt))
        }
        return discovery.takeIf { prefs.edit().putString("paleo_discoveries", array.toString()).commit() }
    }

    fun aiConversations(): List<AiConversation> = try {
        val array = JSONArray(prefs.getString("ai_talks", "[]"))
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val tags = item.optJSONArray("tags") ?: JSONArray()
            AiConversation(
                id = item.getLong("id"),
                createdAt = item.optLong("createdAt", item.getLong("id")),
                title = item.optString("title"),
                answer = item.optString("answer"),
                tags = List(tags.length()) { tags.getString(it) }.filter { it.isNotBlank() },
                category = item.optString("category", "未分类").ifBlank { "未分类" },
                favorite = item.optBoolean("favorite", false)
            )
        }.sortedByDescending { it.createdAt }
    } catch (_: Exception) { emptyList() }

    @Synchronized fun saveAiConversation(
        title: String,
        answer: String,
        category: String,
        tags: List<String>
    ): AiConversation? {
        val cleanTitle = title.trim().take(500)
        val cleanAnswer = answer.trim().take(500_000)
        if (cleanTitle.isBlank() || cleanAnswer.isBlank()) return null
        val now = System.currentTimeMillis()
        val item = AiConversation(
            id = now,
            createdAt = now,
            title = cleanTitle,
            answer = cleanAnswer,
            tags = tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
                .distinct().take(12),
            category = category.trim().take(40).ifBlank { "未分类" }
        )
        saveAiConversations((aiConversations() + item).sortedByDescending { it.createdAt }.take(2000))
        return item
    }

    @Synchronized fun deleteAiConversation(id: Long): Boolean =
        saveAiConversations(aiConversations().filterNot { it.id == id })

    @Synchronized fun updateAiConversationMetadata(
        id: Long,
        category: String,
        tags: List<String>,
        favorite: Boolean
    ): Boolean {
        val cleanCategory = category.trim().take(40).ifBlank { "未分类" }
        val cleanTags = tags.map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }.distinct().take(12)
        return saveAiConversations(aiConversations().map { talk ->
            if (talk.id == id) talk.copy(category = cleanCategory, tags = cleanTags, favorite = favorite) else talk
        })
    }

    private fun saveAiConversations(items: List<AiConversation>): Boolean {
        val array = JSONArray()
        items.forEach { talk ->
            array.put(JSONObject()
                .put("id", talk.id)
                .put("createdAt", talk.createdAt)
                .put("title", talk.title)
                .put("answer", talk.answer)
                .put("tags", JSONArray(talk.tags))
                .put("category", talk.category)
                .put("favorite", talk.favorite))
        }
        return prefs.edit().putString("ai_talks", array.toString()).commit()
    }

    fun mediaReviews(): List<MediaReview> = try {
        val array = JSONArray(prefs.getString("media_reviews", "[]"))
        List(array.length()) { index -> reviewFromJson(array.getJSONObject(index)) }
            .sortedByDescending { it.updatedAt }
    } catch (_: Exception) { emptyList() }

    @Synchronized fun saveMediaReview(draft: MediaReviewDraft): MediaReview? {
        val cleanTitle = draft.title.trim().take(300)
        val cleanThoughts = draft.thoughts.trim().take(100_000)
        if (cleanTitle.isBlank() || cleanThoughts.isBlank()) return null
        val now = System.currentTimeMillis()
        val current = mediaReviews()
        val previous = draft.id?.let { id -> current.firstOrNull { it.id == id } }
        val saved = MediaReview(
            id = previous?.id ?: now,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
            kind = draft.kind.trim().take(12).ifBlank { "电影" },
            title = cleanTitle,
            creator = draft.creator.trim().take(120),
            year = draft.year.trim().take(12),
            rating = draft.rating.coerceIn(1, 10),
            status = draft.status.trim().take(20).ifBlank { "已看" },
            thoughts = cleanThoughts,
            quotes = draft.quotes.trim().take(100_000),
            tags = draft.tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct().take(20),
            imagePaths = draft.imagePaths.filter { File(it).isFile }.distinct().take(24),
            quotePhotoPaths = draft.quotePhotoPaths.filter { File(it).isFile }.distinct().take(24),
            favorite = draft.favorite
        )
        val updated = (current.filterNot { it.id == saved.id } + saved).sortedByDescending { it.updatedAt }.take(3000)
        val success = saveMediaReviews(updated)
        if (success && previous != null) {
            val retained = (saved.imagePaths + saved.quotePhotoPaths).toSet()
            (previous.imagePaths + previous.quotePhotoPaths).filterNot { it in retained }.forEach(::deletePhoto)
        }
        return saved.takeIf { success }
    }

    @Synchronized fun deleteMediaReview(id: Long): Boolean {
        val current = mediaReviews()
        val target = current.firstOrNull { it.id == id } ?: return false
        val success = saveMediaReviews(current.filterNot { it.id == id })
        if (success) (target.imagePaths + target.quotePhotoPaths).forEach(::deletePhoto)
        return success
    }

    private fun saveMediaReviews(items: List<MediaReview>): Boolean {
        val array = JSONArray()
        items.forEach { array.put(reviewJson(it, includePaths = true)) }
        return prefs.edit().putString("media_reviews", array.toString()).commit()
    }

    private fun reviewFromJson(item: JSONObject) = MediaReview(
        id = item.getLong("id"),
        createdAt = item.optLong("createdAt", item.getLong("id")),
        updatedAt = item.optLong("updatedAt", item.optLong("createdAt", item.getLong("id"))),
        kind = item.optString("kind", "电影"),
        title = item.optString("title"),
        creator = item.optString("creator"),
        year = item.optString("year"),
        rating = item.optInt("rating", 8).coerceIn(1, 10),
        status = item.optString("status", "已看"),
        thoughts = item.optString("thoughts"),
        quotes = item.optString("quotes"),
        tags = item.optJSONArray("tags").toStringList(),
        imagePaths = item.optJSONArray("imagePaths").toStringList(),
        quotePhotoPaths = item.optJSONArray("quotePhotoPaths").toStringList(),
        favorite = item.optBoolean("favorite", false)
    )

    private fun reviewJson(item: MediaReview, includePaths: Boolean): JSONObject = JSONObject()
        .put("id", item.id)
        .put("createdAt", item.createdAt)
        .put("updatedAt", item.updatedAt)
        .put("kind", item.kind)
        .put("title", item.title)
        .put("creator", item.creator)
        .put("year", item.year)
        .put("rating", item.rating)
        .put("status", item.status)
        .put("thoughts", item.thoughts)
        .put("quotes", item.quotes)
        .put("tags", JSONArray(item.tags))
        .put("favorite", item.favorite)
        .also { json ->
            if (includePaths) {
                json.put("imagePaths", JSONArray(item.imagePaths))
                json.put("quotePhotoPaths", JSONArray(item.quotePhotoPaths))
            }
        }

    private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else
        List(length()) { optString(it) }.filter { it.isNotBlank() }

    fun createPhotoFile(): File = File.createTempFile("memory_", ".jpg", photosDir)

    fun finalizePhoto(path: String): String? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null
        optimizePhoto(file)
        return file.absolutePath
    }

    fun importPhoto(uri: Uri): String? = runCatching {
        val target = createPhotoFile()
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use(input::copyTo)
        } ?: error("无法读取照片")
        finalizePhoto(target.absolutePath) ?: error("照片无效")
    }.getOrNull()

    fun deletePhoto(path: String) {
        if (path.isBlank()) return
        val target = File(path).canonicalFile
        if (target.parentFile == photosDir.canonicalFile) target.delete()
    }

    fun musicNames(): List<String> = try {
        val arr = JSONArray(prefs.getString("music_files", "[]"))
        List(arr.length()) { arr.getString(it) }
    } catch (_: Exception) { emptyList() }

    fun musicFile(name: String): File = File(musicDir, name)

    fun musicCoverFile(name: String): File? = File(musicDir, "$name.cover.jpg").takeIf { it.isFile }

    @Synchronized fun importMusic(uri: Uri): String? = runCatching {
        val name = queryAudioName(uri) ?: "music_${System.currentTimeMillis()}.mp3"
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "music.mp3" }
        val target = File(musicDir, safe)
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use(input::copyTo)
        } ?: error("无法读取音频")
        if (target.length() == 0L) {
            target.delete()
            error("音频为空")
        }
        runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(target.absolutePath)
                retriever.embeddedPicture?.takeIf { it.isNotEmpty() }?.let { bytes ->
                    File(musicDir, "$safe.cover.jpg").writeBytes(bytes)
                }
            } finally {
                retriever.release()
            }
        }
        saveMusicNames((musicNames().filter { it != safe } + safe).distinct())
        safe
    }.getOrNull()

    @Synchronized fun deleteMusic(name: String) {
        val target = File(musicDir, name).canonicalFile
        if (target.parentFile == musicDir.canonicalFile) target.delete()
        val cover = File(musicDir, "$name.cover.jpg").canonicalFile
        if (cover.parentFile == musicDir.canonicalFile) cover.delete()
        saveMusicNames(musicNames().filter { it != name })
    }

    private fun saveMusicNames(names: List<String>) {
        val arr = JSONArray()
        names.forEach { arr.put(it) }
        prefs.edit().putString("music_files", arr.toString()).commit()
    }

    private fun queryAudioName(uri: Uri): String? = runCatching {
        var name: String? = null
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = cursor.getString(index)
            }
        }
        name
    }.getOrNull()

    @Synchronized fun exportData(): String {
        val exportedReadings = JSONArray()
        readings().forEach { entry ->
            val item = readingJson(entry)
            val photosData = JSONArray()
            entry.photoPaths.filter { it.isNotBlank() }.forEach { path ->
                val photo = File(path)
                if (photo.isFile) {
                    photosData.put(Base64.encodeToString(photo.readBytes(), Base64.NO_WRAP))
                }
            }
            if (photosData.length() > 0) item.put("photoData", photosData)
            item.remove("photoPaths")
            exportedReadings.put(item)
        }
        val exportedReminders = JSONArray()
        reminders().forEach { item ->
            exportedReminders.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("hour", item.hour)
                    .put("minute", item.minute)
                    .put("enabled", item.enabled)
            )
        }
        return JSONObject()
            .put("format", "vitality-backup")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("baseline", prefs.getInt("baseline", 100))
            .put("value", prefs.getInt("value", prefs.getInt("baseline", 100)))
            .put("recoveryStart", prefs.getLong("recovery_start", 0L))
            .put("recoveryEnd", prefs.getLong("recovery_end", 0L))
            .put("recoveryBase", prefs.getInt("recovery_base", 100))
            .put("readings", exportedReadings)
            .put("reminders", exportedReminders)
            .put("rests", JSONArray(rests()))
            .put("aiTalks", JSONArray().apply {
                aiConversations().forEach { talk ->
                    put(JSONObject()
                        .put("id", talk.id)
                        .put("createdAt", talk.createdAt)
                        .put("title", talk.title)
                        .put("answer", talk.answer)
                        .put("tags", JSONArray(talk.tags))
                        .put("category", talk.category)
                        .put("favorite", talk.favorite))
                }
            })
            .put("reviews", JSONArray().apply {
                mediaReviews().forEach { review ->
                    put(reviewJson(review, includePaths = false)
                        .put("imageData", encodedPhotos(review.imagePaths))
                        .put("quotePhotoData", encodedPhotos(review.quotePhotoPaths)))
                }
            })
            .put("exploration", JSONArray().apply {
                paleontologyDiscoveries().forEach { discovery ->
                    put(JSONObject()
                        .put("creatureId", discovery.creatureId)
                        .put("discoveredAt", discovery.discoveredAt))
                }
            })
            .toString(2)
    }

    private fun encodedPhotos(paths: List<String>): JSONArray = JSONArray().apply {
        paths.forEach { path ->
            File(path).takeIf { it.isFile }?.let { file ->
                put(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            }
        }
    }

    @Synchronized fun importData(raw: String): ImportSummary {
        val root = JSONObject(raw)
        require(root.optString("format") == "vitality-backup") { "不是有效的活力备份" }
        require(root.optInt("version", 0) == 1) { "暂不支持这个备份版本" }

        val createdPhotos = mutableListOf<String>()
        try {
            val inputReadings = root.getJSONArray("readings")
            val importedReadings = List(inputReadings.length()) { index ->
                val item = inputReadings.getJSONObject(index)
                val photoPaths = mutableListOf<String>()
                val photoArray = item.optJSONArray("photoData")
                if (photoArray != null) {
                    for (j in 0 until photoArray.length()) {
                        val encoded = photoArray.optString(j)
                        if (encoded.isNotBlank()) {
                            val file = createPhotoFile()
                            file.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
                            createdPhotos += file.absolutePath
                            photoPaths += file.absolutePath
                        }
                    }
                } else {
                    val legacy = item.optString("photoData")
                    if (legacy.isNotBlank()) {
                        val file = createPhotoFile()
                        file.writeBytes(Base64.decode(legacy, Base64.DEFAULT))
                        createdPhotos += file.absolutePath
                        photoPaths += file.absolutePath
                    }
                }
                Reading(
                    time = item.getLong("time"),
                    value = item.getInt("value").coerceIn(0, 100),
                    note = item.optString("note").take(1000),
                    photoPaths = photoPaths
                )
            }

            val inputReminders = root.optJSONArray("reminders") ?: JSONArray()
            val importedReminders = List(inputReminders.length()) { index ->
                val item = inputReminders.getJSONObject(index)
                Reminder(
                    item.getInt("id"),
                    item.getString("title").take(30),
                    item.getInt("hour").coerceIn(0, 23),
                    item.getInt("minute").coerceIn(0, 59),
                    item.optBoolean("enabled", true)
                )
            }
            val inputRests = root.optJSONArray("rests") ?: JSONArray()
            val importedRests = List(inputRests.length()) { inputRests.getString(it).take(30) }
            val inputAiTalks = root.optJSONArray("aiTalks") ?: JSONArray()
            val importedAiTalks = List(inputAiTalks.length()) { index ->
                val item = inputAiTalks.getJSONObject(index)
                val tags = item.optJSONArray("tags") ?: JSONArray()
                AiConversation(
                    id = item.optLong("id", System.currentTimeMillis() + index),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    title = item.optString("title").take(500),
                    answer = item.optString("answer").take(500_000),
                    tags = List(tags.length()) { tags.getString(it).take(40) }.filter { it.isNotBlank() },
                    category = item.optString("category", "未分类").take(40).ifBlank { "未分类" },
                    favorite = item.optBoolean("favorite", false)
                )
            }.filter { it.title.isNotBlank() && it.answer.isNotBlank() }
            val inputReviews = root.optJSONArray("reviews") ?: JSONArray()
            val importedReviews = List(inputReviews.length()) { index ->
                val item = inputReviews.getJSONObject(index)
                fun restorePhotos(key: String): List<String> {
                    val array = item.optJSONArray(key) ?: return emptyList()
                    return List(array.length()) { photoIndex ->
                        val file = createPhotoFile()
                        file.writeBytes(Base64.decode(array.getString(photoIndex), Base64.DEFAULT))
                        optimizePhoto(file)
                        createdPhotos += file.absolutePath
                        file.absolutePath
                    }
                }
                MediaReview(
                    id = item.optLong("id", System.currentTimeMillis() + index),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", item.optLong("createdAt", System.currentTimeMillis())),
                    kind = item.optString("kind", "电影").take(12),
                    title = item.optString("title").take(300),
                    creator = item.optString("creator").take(120),
                    year = item.optString("year").take(12),
                    rating = item.optInt("rating", 8).coerceIn(1, 10),
                    status = item.optString("status", "已看").take(20),
                    thoughts = item.optString("thoughts").take(100_000),
                    quotes = item.optString("quotes").take(100_000),
                    tags = item.optJSONArray("tags").toStringList().take(20),
                    imagePaths = restorePhotos("imageData"),
                    quotePhotoPaths = restorePhotos("quotePhotoData"),
                    favorite = item.optBoolean("favorite", false)
                )
            }.filter { it.title.isNotBlank() && it.thoughts.isNotBlank() }
            val inputExploration = root.optJSONArray("exploration") ?: JSONArray()
            val importedExploration = List(inputExploration.length()) { index ->
                val item = inputExploration.getJSONObject(index)
                FossilDiscovery(
                    creatureId = item.optString("creatureId"),
                    discoveredAt = item.optLong("discoveredAt", 0L)
                )
            }.filter { it.creatureId in PaleontologyCatalog.byId && it.discoveredAt > 0L }
                .distinctBy { it.discoveredAt }
                .takeLast(5_000)

            val editor = prefs.edit()
                .putInt("baseline", root.optInt("baseline", 100).coerceIn(0, 100))
                .putInt("value", root.optInt("value", 100).coerceIn(0, 100))
                .putString("readings", readingsJson(importedReadings.takeLast(5000)).toString())

            if (importedReminders.isNotEmpty()) {
                val remindersJson = JSONArray()
                importedReminders.forEach { item ->
                    remindersJson.put(
                        JSONObject()
                            .put("id", item.id)
                            .put("title", item.title)
                            .put("hour", item.hour)
                            .put("minute", item.minute)
                            .put("enabled", item.enabled)
                    )
                }
                editor.putString("reminders", remindersJson.toString())
            }
            if (importedRests.isNotEmpty()) {
                editor.putString("rests", JSONArray(importedRests.distinct()).toString())
            }
            if (importedAiTalks.isNotEmpty()) {
                val array = JSONArray()
                importedAiTalks.forEach { talk ->
                    array.put(JSONObject()
                        .put("id", talk.id)
                        .put("createdAt", talk.createdAt)
                        .put("title", talk.title)
                        .put("answer", talk.answer)
                        .put("tags", JSONArray(talk.tags))
                        .put("category", talk.category)
                        .put("favorite", talk.favorite))
                }
                editor.putString("ai_talks", array.toString())
            }
            if (importedReviews.isNotEmpty()) {
                val array = JSONArray()
                importedReviews.forEach { array.put(reviewJson(it, includePaths = true)) }
                editor.putString("media_reviews", array.toString())
            }
            if (root.has("exploration")) {
                val array = JSONArray()
                importedExploration.forEach {
                    array.put(JSONObject().put("creatureId", it.creatureId).put("discoveredAt", it.discoveredAt))
                }
                editor.putString("paleo_discoveries", array.toString())
            }

            val recoveryEnd = root.optLong("recoveryEnd", 0L)
            if (recoveryEnd > System.currentTimeMillis()) {
                editor
                    .putLong("recovery_start", root.optLong("recoveryStart", 0L))
                    .putLong("recovery_end", recoveryEnd)
                    .putInt("recovery_base", root.optInt("recoveryBase", 100).coerceIn(0, 100))
            } else {
                editor.remove("recovery_start").remove("recovery_end").remove("recovery_base")
            }
            check(editor.commit()) { "无法保存导入数据" }
            return ImportSummary(importedReadings.size, createdPhotos.size)
        } catch (error: Exception) {
            createdPhotos.forEach(::deletePhoto)
            throw IllegalArgumentException(error.message ?: "导入失败", error)
        }
    }

    private fun readingsJson(items: List<Reading>): JSONArray = JSONArray().apply {
        items.forEach { put(readingJson(it)) }
    }

    private fun readingJson(item: Reading): JSONObject = JSONObject()
        .put("time", item.time)
        .put("value", item.value)
        .put("note", item.note)
        .put("photoPaths", JSONArray().apply {
            item.photoPaths.filter { it.isNotBlank() }.forEach { put(it) }
        })

    private fun optimizePhoto(file: File) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1920) sample *= 2
            val bitmap = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return
            val temporary = File(file.parentFile, "${file.nameWithoutExtension}_optimized.jpg")
            FileOutputStream(temporary).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }
}
