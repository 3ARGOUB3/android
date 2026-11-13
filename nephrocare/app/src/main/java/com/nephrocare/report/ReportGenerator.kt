package com.nephrocare.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.nephrocare.AppContainer
import com.nephrocare.LocaleHelper
import com.nephrocare.R
import com.nephrocare.core.Labs
import com.nephrocare.core.WeightCalc
import com.nephrocare.core.WeightKind
import com.nephrocare.core.WeightPoint
import com.nephrocare.core.fmt
import com.nephrocare.ui.Fmt
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class ReportSection { PROFILE, WEIGHT, BP, LABS, SESSIONS, MEDS, DIARY }

/** Builds a shareable A4 PDF summary of the patient's own records for a period. */
class ReportGenerator(private val app: AppContainer) {
    private val language = app.language()
    private val ctx: Context = LocaleHelper.localized(app.context, language)
    private val rtl = language == "ar"

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val contentWidth = pageWidth - 2 * margin

    private lateinit var doc: PdfDocument
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = 0f
    private var pageNumber = 0

    private val bodyPaint = TextPaint().apply { isAntiAlias = true; textSize = 10f; color = Color.rgb(30, 30, 30) }
    private val smallPaint = TextPaint().apply { isAntiAlias = true; textSize = 8f; color = Color.rgb(90, 90, 90) }
    private val headPaint = TextPaint().apply {
        isAntiAlias = true; textSize = 13f; color = Color.rgb(21, 79, 134); typeface = Typeface.DEFAULT_BOLD
    }
    private val titlePaint = TextPaint().apply {
        isAntiAlias = true; textSize = 20f; color = Color.rgb(30, 111, 184); typeface = Typeface.DEFAULT_BOLD
    }
    private val boldPaint = TextPaint(bodyPaint).apply { typeface = Typeface.DEFAULT_BOLD }

    suspend fun generate(days: Int, sections: Set<ReportSection>): File {
        doc = PdfDocument()
        val since = if (days == 0) Long.MIN_VALUE else System.currentTimeMillis() - days * 24L * 3600 * 1000
        newPage()
        text(ctx.getString(R.string.report_title), titlePaint)
        text(
            ctx.getString(
                R.string.report_period,
                if (days == 0) ctx.getString(R.string.period_all) else ctx.getString(R.string.report_last_days, days),
                Fmt.date(System.currentTimeMillis(), language),
            ),
            smallPaint,
        )
        y += 6

        if (ReportSection.PROFILE in sections) profile()
        if (ReportSection.WEIGHT in sections) weight(since)
        if (ReportSection.BP in sections) bloodPressure(since)
        if (ReportSection.LABS in sections) labs(since)
        if (ReportSection.SESSIONS in sections) sessions(since)
        if (ReportSection.MEDS in sections) meds(since)
        if (ReportSection.DIARY in sections) diary(since)

        y += 10
        text(ctx.getString(R.string.report_footer), smallPaint)
        finishPage()

        val dir = File(app.context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, "NephroCare_" + DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now()) + ".pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    // ---- sections ----

    private fun profile() {
        val p = app.settings.profile.value
        heading(ctx.getString(R.string.report_patient))
        val rows = listOf(
            R.string.profile_name to p.name,
            R.string.profile_birth_year to (p.birthYear?.toString() ?: ""),
            R.string.profile_treatment to p.treatment,
            R.string.profile_since to p.dialysisSince,
            R.string.profile_center to p.center,
            R.string.profile_nephrologist to p.nephrologist,
            R.string.profile_blood_type to p.bloodType,
            R.string.profile_allergies to p.allergies,
        ).filter { it.second.isNotBlank() }
        table(listOf(0.35f, 0.65f), rows.map { listOf(ctx.getString(it.first), it.second) }, header = null)
    }

    private suspend fun weight(since: Long) {
        val all = app.db.weights().all()
        val list = all.filter { it.time >= since }
        heading(ctx.getString(R.string.section_weight))
        if (list.isEmpty()) { text(ctx.getString(R.string.no_entries), bodyPaint); return }
        val points = all.map { WeightPoint(it.time, runCatching { WeightKind.valueOf(it.kind) }.getOrDefault(WeightKind.HOME), it.kg) }
        val dry = WeightCalc.dryWeight(points)
        val gains = WeightCalc.interdialyticGains(points).filter { it.preTime >= since }
        val kg = ctx.getString(R.string.unit_kg)
        text(ctx.getString(R.string.home_dry_weight) + ": " + (dry?.let { "${fmt(it, 1)} $kg" } ?: "—"), bodyPaint)
        if (gains.isNotEmpty()) {
            text(
                ctx.getString(R.string.report_avg_gain, fmt(gains.map { it.kg }.average(), 1) + " $kg", fmt(gains.maxOf { it.kg }, 1) + " $kg"),
                bodyPaint,
            )
        }
        chart(
            listOf(
                list.filter { it.kind == "PRE" }.map { it.time to it.kg } to Color.rgb(198, 40, 40),
                list.filter { it.kind == "POST" }.map { it.time to it.kg } to Color.rgb(46, 125, 50),
                list.filter { it.kind == "HOME" }.map { it.time to it.kg } to Color.rgb(30, 111, 184),
            ),
            dry,
        )
        table(
            listOf(0.4f, 0.3f, 0.3f),
            list.take(40).map { listOf(Fmt.dateTime(it.time, language), ctx.getString(com.nephrocare.ui.weightKindLabel(it.kind)), "${fmt(it.kg, 1)} $kg") },
            header = listOf(ctx.getString(R.string.report_date), ctx.getString(R.string.report_type), ctx.getString(R.string.weight_value)),
        )
    }

    private suspend fun bloodPressure(since: Long) {
        val list = app.db.bp().all().filter { it.time >= since }
        heading(ctx.getString(R.string.section_bp))
        if (list.isEmpty()) { text(ctx.getString(R.string.no_entries), bodyPaint); return }
        chart(
            listOf(
                list.map { it.time to it.systolic.toDouble() } to Color.rgb(198, 40, 40),
                list.map { it.time to it.diastolic.toDouble() } to Color.rgb(30, 111, 184),
            ),
            null,
        )
        table(
            listOf(0.4f, 0.2f, 0.15f, 0.25f),
            list.take(40).map {
                listOf(Fmt.dateTime(it.time, language), "${it.systolic}/${it.diastolic}", it.pulse?.toString() ?: "", ctx.getString(com.nephrocare.ui.bpContextLabel(it.context)))
            },
            header = listOf(ctx.getString(R.string.report_date), "mmHg", ctx.getString(R.string.bp_pulse), ctx.getString(R.string.report_type)),
        )
    }

    private suspend fun labs(since: Long) {
        val list = app.db.labs().all().filter { it.date >= since }
        heading(ctx.getString(R.string.section_labs))
        if (list.isEmpty()) { text(ctx.getString(R.string.no_entries), bodyPaint); return }
        val rows = Labs.all.flatMap { test ->
            list.filter { it.code == test.code }.take(6).map { r ->
                listOf(test.name, Fmt.date(r.date, language), "${fmt(r.value)} ${r.unit}", (test.dialysis ?: test.general).label() + " " + test.mainUnit)
            }
        }
        table(
            listOf(0.25f, 0.25f, 0.2f, 0.3f), rows,
            header = listOf(ctx.getString(R.string.report_test), ctx.getString(R.string.report_date), ctx.getString(R.string.lab_value), ctx.getString(R.string.report_reference)),
        )
        text(ctx.getString(R.string.lab_ranges_vary), smallPaint)
    }

    private suspend fun sessions(since: Long) {
        val list = app.db.sessions().all().filter { it.date >= since }
        heading(ctx.getString(R.string.report_sessions))
        if (list.isEmpty()) { text(ctx.getString(R.string.no_entries), bodyPaint); return }
        table(
            listOf(0.3f, 0.14f, 0.14f, 0.2f, 0.22f),
            list.take(40).map { s ->
                listOf(
                    Fmt.dateTime(s.date, language),
                    s.preWeight?.let { fmt(it, 1) } ?: "",
                    s.postWeight?.let { fmt(it, 1) } ?: "",
                    listOfNotNull(
                        if (s.preSystolic != null) "${s.preSystolic}/${s.preDiastolic}" else null,
                        if (s.postSystolic != null) "${s.postSystolic}/${s.postDiastolic}" else null,
                    ).joinToString(" → "),
                    s.note,
                )
            },
            header = listOf(
                ctx.getString(R.string.report_date), ctx.getString(R.string.weight_kind_pre), ctx.getString(R.string.weight_kind_post),
                "mmHg", ctx.getString(R.string.note_optional),
            ),
        )
    }

    private suspend fun meds(since: Long) {
        val meds = app.db.meds().all()
        val intakes = app.db.intakes().all().filter { it.takenAt >= since }
        heading(ctx.getString(R.string.section_meds))
        if (meds.isEmpty()) { text(ctx.getString(R.string.no_entries), bodyPaint); return }
        table(
            listOf(0.3f, 0.25f, 0.25f, 0.2f),
            meds.map { m ->
                val taken = intakes.count { it.medId == m.id && it.status == "TAKEN" }
                val skipped = intakes.count { it.medId == m.id && it.status == "SKIPPED" }
                listOf(
                    m.name + if (!m.active) " (${ctx.getString(R.string.med_inactive)})" else "",
                    m.dose,
                    m.times.replace(";", " "),
                    ctx.getString(R.string.report_taken_skipped, taken, skipped),
                )
            },
            header = listOf(ctx.getString(R.string.med_name), ctx.getString(R.string.med_dose), ctx.getString(R.string.med_times), ctx.getString(R.string.report_adherence)),
        )
    }

    private suspend fun diary(since: Long) {
        val entries = app.db.diary().all().filter { it.date >= since }
        val questions = app.db.questions().all().filter { !it.answered }
        heading(ctx.getString(R.string.report_diary))
        if (entries.isEmpty()) text(ctx.getString(R.string.no_entries), bodyPaint)
        val symptomNames = com.nephrocare.ui.SYMPTOMS.associate { it.first to ctx.getString(it.second) }
        entries.take(40).forEach { e ->
            val symptoms = e.symptoms.split(',').mapNotNull { symptomNames[it] }.joinToString(", ")
            text(
                "${Fmt.dateTime(e.date, language)} — ${ctx.getString(R.string.diary_fatigue, e.fatigue)}" +
                    (if (symptoms.isNotBlank()) " — $symptoms" else "") +
                    (if (e.note.isNotBlank()) " — ${e.note}" else ""),
                bodyPaint,
            )
        }
        if (questions.isNotEmpty()) {
            y += 4
            text(ctx.getString(R.string.diary_tab_questions), boldPaint)
            questions.forEach { text("• ${it.text}", bodyPaint) }
        }
    }

    // ---- drawing helpers ----

    private fun newPage() {
        pageNumber++
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        page = doc.startPage(info)
        canvas = page!!.canvas
        y = margin
        val footer = "NephroCare · $pageNumber"
        canvas!!.drawText(footer, pageWidth / 2f - smallPaint.measureText(footer) / 2, pageHeight - 20f, smallPaint)
    }

    private fun finishPage() {
        page?.let { doc.finishPage(it) }
        page = null
    }

    private fun ensure(height: Float) {
        if (y + height > pageHeight - margin) {
            finishPage()
            newPage()
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()

    private fun text(value: String, paint: TextPaint) {
        val l = layout(value, paint, contentWidth.toInt())
        ensure(l.height.toFloat())
        canvas!!.save()
        canvas!!.translate(margin, y)
        l.draw(canvas!!)
        canvas!!.restore()
        y += l.height + 3
    }

    private fun heading(value: String) {
        ensure(60f)
        y += 8
        text(value, headPaint)
        val line = Paint().apply { color = Color.rgb(200, 215, 230); strokeWidth = 1f }
        canvas!!.drawLine(margin, y, pageWidth - margin, y, line)
        y += 4
    }

    /** A table whose columns run right to left in Arabic. */
    private fun table(widths: List<Float>, rows: List<List<String>>, header: List<String>?) {
        val allRows = (header?.let { listOf(it) } ?: emptyList()) + rows
        allRows.forEachIndexed { index, row ->
            val isHeader = header != null && index == 0
            val paint = if (isHeader) boldPaint else bodyPaint
            val layouts = row.mapIndexed { i, cell -> layout(cell, paint, (contentWidth * widths[i] - 6).toInt()) }
            val h = (layouts.maxOfOrNull { it.height } ?: 0) + 6f
            ensure(h)
            if (isHeader) {
                canvas!!.drawRect(margin, y, pageWidth - margin, y + h, Paint().apply { color = Color.rgb(230, 239, 248) })
            }
            var x = 0f
            layouts.forEachIndexed { i, l ->
                val w = contentWidth * widths[i]
                val left = if (rtl) margin + contentWidth - x - w else margin + x
                canvas!!.save()
                canvas!!.translate(left + 3, y + 3)
                l.draw(canvas!!)
                canvas!!.restore()
                x += w
            }
            canvas!!.drawLine(margin, y + h, pageWidth - margin, y + h, Paint().apply { color = Color.rgb(225, 225, 225) })
            y += h
        }
        y += 6
    }

    private fun chart(series: List<Pair<List<Pair<Long, Double>>, Int>>, refLine: Double?) {
        val pts = series.flatMap { it.first }
        if (pts.size < 2) return
        val height = 150f
        ensure(height + 10)
        val c = canvas!!
        val left = margin + 30
        val right = pageWidth - margin
        val top = y
        val bottom = y + height
        val minX = pts.minOf { it.first }
        val maxX = pts.maxOf { it.first }.let { if (it == minX) it + 1 else it }
        var minY = (pts.map { it.second } + listOfNotNull(refLine)).min()
        var maxY = (pts.map { it.second } + listOfNotNull(refLine)).max()
        if (minY == maxY) { minY -= 1; maxY += 1 }
        val pad = (maxY - minY) * 0.1
        minY -= pad; maxY += pad
        fun px(t: Long) = left + (right - left) * ((t - minX).toFloat() / (maxX - minX))
        fun py(v: Double) = bottom - (bottom - top) * ((v - minY) / (maxY - minY)).toFloat()
        val grid = Paint().apply { color = Color.rgb(225, 225, 225); strokeWidth = 0.6f }
        for (i in 0..4) {
            val v = minY + (maxY - minY) * i / 4
            c.drawLine(left, py(v), right, py(v), grid)
            c.drawText(fmt(v, 0), margin, py(v) + 3, smallPaint)
        }
        refLine?.let {
            val p = Paint().apply {
                color = Color.rgb(46, 158, 122); strokeWidth = 1.2f; style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
            }
            c.drawLine(left, py(it), right, py(it), p)
        }
        series.forEach { (points, color) ->
            if (points.isEmpty()) return@forEach
            val sorted = points.sortedBy { it.first }
            val path = Path()
            sorted.forEachIndexed { i, (t, v) -> if (i == 0) path.moveTo(px(t), py(v)) else path.lineTo(px(t), py(v)) }
            c.drawPath(path, Paint().apply { this.color = color; strokeWidth = 1.6f; style = Paint.Style.STROKE; isAntiAlias = true })
            val dot = Paint().apply { this.color = color; isAntiAlias = true }
            sorted.forEach { (t, v) -> c.drawCircle(px(t), py(v), 2f, dot) }
        }
        c.drawText(Fmt.shortDate(minX, language), left, bottom + 10, smallPaint)
        val end = Fmt.shortDate(maxX, language)
        c.drawText(end, right - smallPaint.measureText(end), bottom + 10, smallPaint)
        y = bottom + 18
    }
}
