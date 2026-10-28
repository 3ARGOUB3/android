package com.masroufi.core

import jxl.Workbook
import jxl.write.Label
import jxl.write.Number
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class XlsImporterTest {

    private val header = listOf(
        "الملاحظات", "المجموعة", "جهة الإتصال", "الى (المحفظة)", "المحفظة",
        "العملة", "التكلفة", "القسم", "القسم الرئيسي", "التاريخ",
    )

    private fun xls(rows: List<List<Any>>): ByteArray {
        val out = ByteArrayOutputStream()
        val wb = Workbook.createWorkbook(out)
        val sheet = wb.createSheet("المعاملات", 0)
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { c, v ->
                sheet.addCell(if (v is Double) Number(c, r, v) else Label(c, r, v.toString()))
            }
        }
        wb.write()
        wb.close()
        return out.toByteArray()
    }

    @Test
    fun readsExportRows() {
        val bytes = xls(
            listOf(
                header,
                listOf("بيتزا", "", "", "", "مصروف الشهر", "DZD", -250.0, "بيتزا", "أكل خارج المنزل", "27 أكتوبر، 2026 01:47 PM"),
                listOf("", "", "", "مصروف الشهر", "TRUST Bank", "DZD", 80000.0, "تحويل رصيد", "تحويل رصيد", "25 أكتوبر، 2026 11:24 AM"),
                listOf("bad", "", "", "", "x", "DZD", -1.0, "a", "b", "not a date"),
            )
        )
        val result = XlsImporter.parse(bytes.inputStream())
        assertEquals(listOf(4), result.skippedRows)
        assertEquals(2, result.transactions.size)

        val pizza = result.transactions.first()
        assertEquals("مصروف الشهر", pizza.wallet)
        assertEquals(-250.0, pizza.amount)
        assertEquals("أكل خارج المنزل", pizza.mainCategory)
        assertEquals(LocalDateTime.of(2026, 10, 27, 13, 47), pizza.dateTime)
        assertEquals(true, pizza.isExpense)

        val transfer = result.transactions.last()
        assertEquals(true, transfer.isTransfer)
        assertEquals("مصروف الشهر", transfer.toWallet)
    }

    @Test
    fun rejectsNonExcelFiles() {
        assertFailsWith<ImportException> { XlsImporter.parse("hello".byteInputStream()) }
    }
}
