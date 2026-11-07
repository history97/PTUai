package kr.landscapebid.app.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** 파일(엑셀 .xlsx / CSV)을 "행 목록(칸은 String 또는 Double)"으로 바꾼다. 웹의 XLSX.utils.sheet_to_json(header:1) 에 해당. */
object RowsReader {
    fun read(bytes: ByteArray): List<List<Any?>> {
        if (bytes.size >= 4 && bytes[0] == 0xD0.toByte() && bytes[1] == 0xCF.toByte() && bytes[2] == 0x11.toByte() && bytes[3] == 0xE0.toByte()) {
            throw IllegalArgumentException("예전 엑셀 형식(.xls)은 아직 읽지 못합니다. 엑셀에서 .xlsx 로 다시 저장해 올려 주세요")
        }
        if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) return XlsxReader.read(bytes)
        return CsvReader.read(bytes)
    }
}

object XlsxReader {
    fun read(bytes: ByteArray): List<List<Any?>> {
        var shared: List<String> = emptyList()
        val sheets = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val name = e.name
                if (name == "xl/sharedStrings.xml") shared = parseShared(zip.readBytes())
                else if (name.startsWith("xl/worksheets/") && name.endsWith(".xml")) sheets[name] = zip.readBytes()
                e = zip.nextEntry
            }
        }
        val first = sheets["xl/worksheets/sheet1.xml"] ?: sheets.entries.minByOrNull { it.key }?.value
            ?: throw IllegalArgumentException("엑셀 안에서 시트를 찾지 못했습니다")
        return parseSheet(first, shared)
    }

    private fun local(name: String): String = name.substringAfter(':')

    /** 문자열 표. 한글 발음 표기(rPh)는 건너뛴다. */
    private fun parseShared(b: ByteArray): List<String> {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(b), null)
        val out = ArrayList<String>()
        var sb = StringBuilder()
        var inT = false
        var inPh = 0
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (local(p.name)) { "si" -> sb = StringBuilder(); "t" -> inT = true; "rPh" -> inPh++ }
                XmlPullParser.TEXT -> if (inT && inPh == 0) sb.append(p.text)
                XmlPullParser.END_TAG -> when (local(p.name)) { "t" -> inT = false; "rPh" -> inPh--; "si" -> out.add(sb.toString()) }
            }
            ev = p.next()
        }
        return out
    }

    private fun colOf(ref: String): Int {
        var n = 0
        for (ch in ref) { if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break }
        return n - 1
    }

    private fun convert(t: String, v: String, inl: String, shared: List<String>): Any? = when (t) {
        "s" -> v.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
        "inlineStr" -> inl
        "str" -> v
        "b" -> if (v.trim() == "1") 1.0 else 0.0
        "e" -> ""
        else -> if (v.isBlank()) "" else (v.trim().toDoubleOrNull() ?: v)
    }

    private fun parseSheet(b: ByteArray, shared: List<String>): List<List<Any?>> {
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(b), null)
        val rows = ArrayList<MutableList<Any?>>()
        var cur: MutableList<Any?>? = null
        var rowIdx = -1
        var colIdx = 0
        var nextCol = 0
        var type = ""
        var vText = StringBuilder()
        var isText = StringBuilder()
        var inV = false
        var inIs = false
        var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (local(p.name)) {
                    "row" -> {
                        val r = p.getAttributeValue(null, "r")?.toIntOrNull()
                        rowIdx = if (r != null) r - 1 else rowIdx + 1
                        while (rows.size <= rowIdx) rows.add(ArrayList())
                        cur = rows[rowIdx]
                        nextCol = 0
                    }
                    "c" -> {
                        val ref = p.getAttributeValue(null, "r")
                        colIdx = if (ref != null) colOf(ref) else nextCol
                        nextCol = colIdx + 1
                        type = p.getAttributeValue(null, "t") ?: ""
                        vText = StringBuilder(); isText = StringBuilder()
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> if (inIs) inT = true
                }
                XmlPullParser.TEXT -> { if (inV) vText.append(p.text) else if (inIs && inT) isText.append(p.text) }
                XmlPullParser.END_TAG -> when (local(p.name)) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "is" -> inIs = false
                    "c" -> {
                        val row = cur
                        if (row != null && colIdx >= 0) {
                            while (row.size <= colIdx) row.add("")
                            row[colIdx] = convert(type, vText.toString(), isText.toString(), shared)
                        }
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }
}

object CsvReader {
    fun read(bytes: ByteArray): List<List<Any?>> {
        var text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        if (text.contains('\uFFFD')) text = String(bytes, charset("EUC-KR"))   // 한글 엑셀이 저장한 CSV
        val firstLine = text.lineSequence().firstOrNull() ?: ""
        val sep = if (firstLine.count { it == '\t' } > firstLine.count { it == ',' }) '\t' else ','
        val rows = ArrayList<List<Any?>>()
        var row = ArrayList<Any?>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == sep -> { row.add(sb.toString()); sb.setLength(0) }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(sb.toString()); sb.setLength(0); rows.add(row); row = ArrayList()
                }
                else -> sb.append(ch)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) { row.add(sb.toString()); rows.add(row) }
        return rows
    }
}
