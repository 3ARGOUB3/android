package com.nephrocare.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer

val contentJson = Json { ignoreUnknownKeys = true }

@Serializable
data class Section(val h: String? = null, val p: List<String> = emptyList(), val b: List<String> = emptyList())

/** An educational article. [cat] groups articles (encyclopedia, nutrition, fistula, emergency, ...). */
@Serializable
data class Article(
    val id: String,
    val cat: String,
    val title: String,
    val summary: String = "",
    val s: List<Section> = emptyList(),
    val kw: List<String> = emptyList(),
) {
    fun plainText(): String = buildString {
        append(summary).append(' ')
        s.forEach { sec ->
            sec.h?.let { append(it).append(' ') }
            sec.p.forEach { append(it).append(' ') }
            sec.b.forEach { append(it).append(' ') }
        }
    }
}

@Serializable
data class LabText(
    val code: String,
    val title: String,
    val what: String,
    val high: String,
    val low: String,
    val tips: String = "",
)

/** Everything written in one language. */
@Serializable
data class ContentPack(
    val articles: List<Article> = emptyList(),
    val labs: List<LabText> = emptyList(),
    val tips: List<String> = emptyList(),
)

@Serializable
data class Food(
    val id: String,
    /** fruit, vegetable, starch, protein, dairy, legume, nuts, drink, condiment, processed */
    val g: String,
    val n: Map<String, String>,
    /** mg of potassium, phosphorus and sodium and g of protein per 100 g (approximate). */
    val k: Int,
    val p: Int,
    val na: Int,
    val pr: Double,
    /** Usually contains phosphate additives. */
    val add: Boolean = false,
    /** Mostly water: counts towards the fluid allowance. */
    val water: Boolean = false,
)

@Serializable
data class Dish(
    val id: String,
    val n: Map<String, String>,
    /** L, M or H for potassium, phosphorus, sodium and fluid. */
    val k: String,
    val p: String,
    val na: String,
    val fl: String,
    val tip: Map<String, String>,
)

@Serializable
data class FoodData(val foods: List<Food> = emptyList(), val dishes: List<Dish> = emptyList())

/** A dhikr or supplication. Quranic text is never typed here: [q] references the Quran data ("2:255" or a whole surah "112"). */
@Serializable
data class Dhikr(
    val id: String,
    /** morning, evening, after_prayer, sick, patience */
    val cat: String,
    val ar: String = "",
    val q: String? = null,
    val count: Int = 1,
    val src: String = "",
    val tr: Map<String, String> = emptyMap(),
)

@Serializable
data class DhikrData(val items: List<Dhikr> = emptyList())

@Serializable
data class Verse(val id: Int, val text: String, val translation: String? = null)

@Serializable
data class Surah(
    val id: Int,
    val name: String,
    val transliteration: String,
    val translation: String? = null,
    val type: String = "",
    val total_verses: Int = 0,
    val verses: List<Verse> = emptyList(),
)

/** Normalises text for search: case, Latin accents, Arabic diacritics and letter variants, Arabic-Indic digits. */
object TextNormalizer {
    private val marks = Regex("\\p{M}+")

    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input.lowercase()) {
            when (ch) {
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ة' -> sb.append('ه')
                'ى' -> sb.append('ي')
                'ؤ' -> sb.append('و')
                'ئ' -> sb.append('ي')
                'ـ' -> {}
                in '٠'..'٩' -> sb.append('0' + (ch - '٠'))
                else -> sb.append(ch)
            }
        }
        val decomposed = Normalizer.normalize(sb.toString(), Normalizer.Form.NFD)
        return decomposed.replace(marks, "")
    }

    fun tokens(input: String): List<String> =
        normalize(input).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
}

data class SearchDoc(val key: String, val kind: String, val title: String, val text: String, val keywords: List<String> = emptyList())

data class SearchHit(val doc: SearchDoc, val score: Int)

/** A small in-memory search over the offline content. Every query word must appear somewhere. */
class SearchIndex(docs: List<SearchDoc>) {
    private class Entry(val doc: SearchDoc, val title: String, val keywords: String, val text: String)

    private val entries = docs.map {
        Entry(
            it,
            TextNormalizer.normalize(it.title),
            TextNormalizer.normalize(it.keywords.joinToString(" ")),
            TextNormalizer.normalize(it.text),
        )
    }

    fun search(query: String, limit: Int = 50): List<SearchHit> {
        val words = TextNormalizer.tokens(query)
        if (words.isEmpty()) return emptyList()
        return entries.mapNotNull { e ->
            var score = 0
            for (w in words) {
                val s = (if (e.title.contains(w)) 6 else 0) +
                    (if (e.keywords.contains(w)) 4 else 0) +
                    (if (e.text.contains(w)) 1 else 0)
                if (s == 0) return@mapNotNull null
                score += s
            }
            SearchHit(e.doc, score)
        }.sortedByDescending { it.score }.take(limit)
    }
}
