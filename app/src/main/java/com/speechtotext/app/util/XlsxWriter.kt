package com.speechtotext.app.util

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxWriter {

    fun writeXlsx(lines: List<CaptionLine>, outputStream: OutputStream) {
        val zip = ZipOutputStream(outputStream)

        // 1. [Content_Types].xml
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
              <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        zip.write(contentTypes)
        zip.closeEntry()

        // 2. _rels/.rels
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        val rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        zip.write(rels)
        zip.closeEntry()

        // 3. xl/_rels/workbook.xml.rels
        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        val wbRels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        zip.write(wbRels)
        zip.closeEntry()

        // 4. xl/workbook.xml
        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        val wb = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="Transcripts" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        zip.write(wb)
        zip.closeEntry()

        // 5. xl/worksheets/sheet1.xml
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        val rowsBuilder = StringBuilder()

        // Header Row (Row 1): No, Start, End, Text
        rowsBuilder.append("""
            <row r="1">
              <c r="A1" t="inlineStr"><is><t>No</t></is></c>
              <c r="B1" t="inlineStr"><is><t>Start</t></is></c>
              <c r="C1" t="inlineStr"><is><t>End</t></is></c>
              <c r="D1" t="inlineStr"><is><t>Text</t></is></c>
            </row>
        """.trimIndent())

        // Data Rows
        for ((idx, line) in lines.withIndex()) {
            val rowNum = idx + 2
            val no = line.index
            val start = TimeFormat.formatToSrtTime(line.start)
            val end = TimeFormat.formatToSrtTime(line.end)
            val text = escapeXml(line.text)

            rowsBuilder.append("""
                <row r="$rowNum">
                  <c r="A$rowNum"><v>$no</v></c>
                  <c r="B$rowNum" t="inlineStr"><is><t>$start</t></is></c>
                  <c r="C$rowNum" t="inlineStr"><is><t>$end</t></is></c>
                  <c r="D$rowNum" t="inlineStr"><is><t>$text</t></is></c>
                </row>
            """.trimIndent())
        }

        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData>
                $rowsBuilder
              </sheetData>
            </worksheet>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        zip.write(sheetXml)
        zip.closeEntry()

        zip.finish()
        zip.flush()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
