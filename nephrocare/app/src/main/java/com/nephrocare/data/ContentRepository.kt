package com.nephrocare.data

import android.content.Context
import com.nephrocare.core.Article
import com.nephrocare.core.ContentPack
import com.nephrocare.core.DhikrData
import com.nephrocare.core.FoodData
import com.nephrocare.core.Labs
import com.nephrocare.core.SearchDoc
import com.nephrocare.core.SearchIndex
import com.nephrocare.core.contentJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class SurahInfo(
    val id: Int,
    val name: String,
    val transliteration: String,
    val type: String,
    val count: Int,
    val names: Map<String, String> = emptyMap(),
)

/** A verse in Arabic with its translation in the reader's language (null in Arabic). */
data class QuranVerse(val surah: Int, val number: Int, val arabic: String, val translation: String?)

val SUPPORTED_LANGUAGES = listOf("ar", "fr", "en", "es")

/** Reads the offline content shipped in assets/. Everything is cached after the first read. */
class ContentRepository(private val context: Context) {
    private val packs = ConcurrentHashMap<String, ContentPack>()
    private val indexes = ConcurrentHashMap<String, SearchIndex>()
    private val surahCache = ConcurrentHashMap<String, List<String>>()

    fun pack(language: String): ContentPack = packs.getOrPut(language) {
        readAsset("content/$language.json")?.let { contentJson.decodeFromString(ContentPack.serializer(), it) }
            ?: readAsset("content/en.json")?.let { contentJson.decodeFromString(ContentPack.serializer(), it) }
            ?: ContentPack()
    }

    fun article(language: String, id: String): Article? = pack(language).articles.firstOrNull { it.id == id }

    fun articles(language: String, category: String): List<Article> =
        pack(language).articles.filter { it.cat == category }

    val foods: FoodData by lazy {
        readAsset("foods.json")?.let { contentJson.decodeFromString(FoodData.serializer(), it) } ?: FoodData()
    }

    val adhkar: DhikrData by lazy {
        readAsset("adhkar.json")?.let { contentJson.decodeFromString(DhikrData.serializer(), it) } ?: DhikrData()
    }

    val surahs: List<SurahInfo> by lazy {
        readAsset("quran/index.json")?.let { contentJson.decodeFromString(ListSerializer(SurahInfo.serializer()), it) }
            ?: emptyList()
    }

    /** Verses of a surah; [language] other than Arabic adds the translation. */
    fun surah(id: Int, language: String): List<QuranVerse> {
        val arabic = lines("quran/ar/$id.json")
        val translation = if (language == "ar") null else lines("quran/$language/$id.json")
        return arabic.mapIndexed { i, text -> QuranVerse(id, i + 1, text, translation?.getOrNull(i)) }
    }

    /** "2:255" (one verse), "2:285-286" (a range) or "112" (a whole surah). */
    fun quranRef(ref: String, language: String): List<QuranVerse> {
        val parts = ref.split(':')
        val surah = parts[0].trim().toIntOrNull() ?: return emptyList()
        val all = surah(surah, language)
        if (parts.size == 1) return all
        val range = parts[1].split('-').mapNotNull { it.trim().toIntOrNull() }
        val from = range.firstOrNull() ?: return all
        val to = range.getOrNull(1) ?: from
        return all.filter { it.number in from..to }
    }

    fun searchIndex(language: String): SearchIndex = indexes.getOrPut(language) {
        val pack = pack(language)
        val docs = mutableListOf<SearchDoc>()
        pack.articles.forEach { a ->
            docs += SearchDoc("article:${a.id}", a.cat, a.title, a.plainText(), a.kw)
        }
        pack.labs.forEach { l ->
            val test = Labs.byCode(l.code)
            docs += SearchDoc(
                "lab:${l.code}", "lab", l.title,
                listOf(l.what, l.high, l.low, l.tips).joinToString(" "),
                listOfNotNull(test?.name, l.code),
            )
        }
        foods.foods.forEach { f ->
            docs += SearchDoc("food:${f.id}", "food", f.n[language] ?: f.n["en"].orEmpty(), "", f.n.values.toList())
        }
        foods.dishes.forEach { d ->
            docs += SearchDoc("dish:${d.id}", "dish", d.n[language] ?: d.n["en"].orEmpty(), d.tip[language].orEmpty(), d.n.values.toList())
        }
        SearchIndex(docs)
    }

    private fun lines(path: String): List<String> = surahCache.getOrPut(path) {
        readAsset(path)?.let { contentJson.decodeFromString(ListSerializer(String.serializer()), it) } ?: emptyList()
    }

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: Exception) {
        null
    }
}
