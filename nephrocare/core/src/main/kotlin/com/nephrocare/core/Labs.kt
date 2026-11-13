package com.nephrocare.core

/** A unit a lab may report in, with the factor that converts it to the test's main unit. */
data class LabUnit(val symbol: String, val toMain: Double)

data class LabRange(val low: Double?, val high: Double?) {
    fun contains(v: Double) = (low == null || v >= low) && (high == null || v <= high)
    fun label(): String = when {
        low != null && high != null -> "${fmt(low)} – ${fmt(high)}"
        low != null -> "≥ ${fmt(low)}"
        high != null -> "≤ ${fmt(high)}"
        else -> "—"
    }
}

/**
 * A lab test the app follows.
 * [general] is a typical adult laboratory range; [dialysis] is a range commonly cited
 * in guidelines for people on haemodialysis (e.g. KDIGO, K/DOQI). Both are only indicative:
 * every laboratory prints its own range and the medical team sets each patient's targets.
 */
data class LabTest(
    val code: String,
    val name: String,
    val units: List<LabUnit>,
    val general: LabRange,
    val dialysis: LabRange?,
    /** Whether comparing to a range is meaningful for a dialysis patient (not for urea/creatinine). */
    val flagged: Boolean = true,
) {
    val mainUnit: String get() = units.first().symbol
    fun toMain(value: Double, unit: String): Double =
        value * (units.firstOrNull { it.symbol == unit }?.toMain ?: 1.0)
}

enum class LabStatus { LOW, IN_RANGE, HIGH, NOT_FLAGGED }

object Labs {
    val all: List<LabTest> = listOf(
        LabTest("UREA", "Urée", listOf(LabUnit("g/L", 1.0), LabUnit("mmol/L", 0.06006), LabUnit("mg/dL", 0.01)),
            LabRange(0.15, 0.45), null, flagged = false),
        LabTest("CREAT", "Créatinine", listOf(LabUnit("mg/L", 1.0), LabUnit("µmol/L", 1 / 8.84), LabUnit("mg/dL", 10.0)),
            LabRange(6.0, 12.0), null, flagged = false),
        LabTest("K", "Potassium", listOf(LabUnit("mmol/L", 1.0), LabUnit("mEq/L", 1.0)),
            LabRange(3.5, 5.0), LabRange(3.5, 5.5)),
        LabTest("PHOS", "Phosphore", listOf(LabUnit("mg/L", 1.0), LabUnit("mmol/L", 30.97), LabUnit("mg/dL", 10.0)),
            LabRange(25.0, 45.0), LabRange(35.0, 55.0)),
        LabTest("CA", "Calcium", listOf(LabUnit("mg/L", 1.0), LabUnit("mmol/L", 40.08), LabUnit("mg/dL", 10.0)),
            LabRange(85.0, 105.0), LabRange(84.0, 95.0)),
        LabTest("HB", "Hémoglobine", listOf(LabUnit("g/dL", 1.0), LabUnit("g/L", 0.1)),
            LabRange(12.0, 16.0), LabRange(10.0, 11.5)),
        LabTest("FERRITIN", "Ferritine", listOf(LabUnit("ng/mL", 1.0), LabUnit("µg/L", 1.0)),
            LabRange(30.0, 300.0), LabRange(200.0, 500.0)),
        LabTest("ALB", "Albumine", listOf(LabUnit("g/L", 1.0), LabUnit("g/dL", 10.0)),
            LabRange(35.0, 50.0), LabRange(40.0, null)),
        LabTest("PTH", "PTH", listOf(LabUnit("pg/mL", 1.0), LabUnit("ng/L", 1.0), LabUnit("pmol/L", 9.43)),
            LabRange(15.0, 65.0), LabRange(130.0, 585.0)),
        LabTest("CRP", "CRP", listOf(LabUnit("mg/L", 1.0), LabUnit("mg/dL", 10.0)),
            LabRange(null, 5.0), null),
        LabTest("TSAT", "TSAT", listOf(LabUnit("%", 1.0)),
            LabRange(20.0, 50.0), LabRange(20.0, 50.0)),
    )

    fun byCode(code: String): LabTest? = all.firstOrNull { it.code == code }

    /** Compares a value (in the main unit) with the dialysis range when there is one, else the general one. */
    fun status(test: LabTest, mainValue: Double): LabStatus {
        if (!test.flagged) return LabStatus.NOT_FLAGGED
        val range = test.dialysis ?: test.general
        return when {
            range.low != null && mainValue < range.low -> LabStatus.LOW
            range.high != null && mainValue > range.high -> LabStatus.HIGH
            else -> LabStatus.IN_RANGE
        }
    }
}

/** Formats a number with at most two decimals and no trailing zeros, always with a dot. */
fun fmt(v: Double, maxDecimals: Int = 2): String {
    val s = String.format(java.util.Locale.ROOT, "%.${maxDecimals}f", v)
    return if (s.contains('.')) s.trimEnd('0').trimEnd('.') else s
}
