package com.speechtotext.app.util

import com.speechtotext.app.domain.Transcript
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxWriter {

    fun writeDocx(transcript: Transcript, outputStream: OutputStream) {
        val zip = ZipOutputStream(outputStream)

        // 1. [Content_Types].xml
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        val contentTypes = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
              <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
              <Default Extension="xml" ContentType="application/xml"/>
              <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            </Types>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        zip.write(contentTypes)
        zip.closeEntry()

        // 2. _rels/.rels
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        val rels = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
            </Relationships>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        zip.write(rels)
        zip.closeEntry()

        // 3. word/document.xml
        zip.putNextEntry(ZipEntry("word/document.xml"))
        val titleXml = escapeXml(transcript.title)
        val langXml = escapeXml(transcript.languageName)
        val timeXml = escapeXml(TimeFormat.formatSecondsToDisplay(transcript.durationSeconds))

        val bodyBuilder = StringBuilder()
        // Title
        bodyBuilder.append("""
            <w:p>
              <w:pPr><w:jc w:val="center"/><w:spacing w:after="200"/></w:pPr>
              <w:r><w:rPr><w:b/><w:sz w:val="40"/><w:color w:val="00B0FF"/></w:rPr><w:t>$titleXml</w:t></w:r>
            </w:p>
        """.trimIndent())

        // Metadata subtitle
        bodyBuilder.append("""
            <w:p>
              <w:pPr><w:jc w:val="center"/><w:spacing w:after="400"/></w:pPr>
              <w:r><w:rPr><w:i/><w:sz w:val="20"/><w:color w:val="757575"/></w:rPr><w:t>Language: $langXml | Duration: $timeXml</w:t></w:r>
            </w:p>
        """.trimIndent())

        // Paragraphs of transcript
        val paragraphs = transcript.fullText.split("\n").filter { it.isNotBlank() }
        for (para in paragraphs) {
            val textXml = escapeXml(para)
            val rtlPr = if (transcript.isRtl) "<w:bidi/>" else ""
            bodyBuilder.append("""
                <w:p>
                  <w:pPr>$rtlPr<w:spacing w:after="200" w:line="320" w:lineRule="auto"/></w:pPr>
                  <w:r><w:rPr><w:sz w:val="24"/></w:rPr><w:t xml:space="preserve">$textXml</w:t></w:r>
                </w:p>
            """.trimIndent())
        }

        val docXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                $bodyBuilder
                <w:sectPr>
                  <w:pgSz w:w="11906" w:h="16838"/>
                  <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
                </w:sectPr>
              </w:body>
            </w:document>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        zip.write(docXml)
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
