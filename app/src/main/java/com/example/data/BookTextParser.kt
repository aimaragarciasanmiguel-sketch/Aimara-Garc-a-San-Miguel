package com.example.data

object BookTextParser {
    
    data class ParsedSection(
        val title: String,
        val subtitle: String?,
        val paragraphs: List<String>
    )

    fun parseRawText(rawText: String): List<ParsedSection> {
        val lines = rawText.split("\n").map { it.trim() }
        val sections = mutableListOf<ParsedSection>()
        
        var currentTitle = ""
        var currentSubtitle: String? = null
        val currentParagraphs = mutableListOf<String>()
        
        fun commitSection() {
            if (currentTitle.isNotEmpty() || currentParagraphs.isNotEmpty()) {
                sections.add(
                    ParsedSection(
                        title = if (currentTitle.isEmpty()) "Sección General" else currentTitle,
                        subtitle = currentSubtitle,
                        paragraphs = currentParagraphs.toList()
                    )
                )
                currentParagraphs.clear()
                currentSubtitle = null
            }
        }
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isEmpty()) {
                i++
                continue
            }
            
            // Look for major section headers in uppercase (e.g. PRÓLOGO, INTRODUCCIÓN, CAPÍTULO I, etc.)
            val isMajorHeader = line.length in 3..60 && (
                line.all { it.isUpperCase() || it.isWhitespace() || !it.isLetter() } ||
                line.startsWith("CAPÍTULO", ignoreCase = true) || 
                line.startsWith("PRÓLOGO", ignoreCase = true) || 
                line.startsWith("INTRODUCCIÓN", ignoreCase = true) ||
                line.startsWith("INTRODICCIÓN", ignoreCase = true)
            )
            
            if (isMajorHeader) {
                commitSection()
                currentTitle = line
                
                // Let's see if the next non-empty line is a subtitle
                i++
                while (i < lines.size && lines[i].isEmpty()) {
                    i++
                }
                if (i < lines.size) {
                    val nextLine = lines[i]
                    // If it's a short title-like line, treat as subtitle
                    if (nextLine.length in 3..60 && !nextLine.all { it.isUpperCase() || !it.isLetter() }) {
                        currentSubtitle = nextLine
                        i++
                    }
                }
                continue
            }
            
            currentParagraphs.add(line)
            i++
        }
        
        commitSection()
        return sections
    }

    fun generateHtml(
        sections: List<ParsedSection>, 
        marginTopMm: Float, 
        marginBottomMm: Float, 
        marginLeftMm: Float, 
        marginRightMm: Float,
        columnWidthMm: Float,
        columnGapMm: Float,
        fontSizeSp: Float,
        textColorHex: String = "#1E293B",
        lineHeight: Float = 1.6f,
        paddingTopMm: Float = 5f,
        paddingBottomMm: Float = 5f,
        paddingLeftMm: Float = 5f,
        paddingRightMm: Float = 5f,
        pageWidthMm: Float = 148f,
        pageHeightMm: Float = 210f,
        showPageNumbers: Boolean = true,
        pageNumbersStartAtPage: Int = 1,
        pageNumbersStartFromValue: Int = 1,
        title: String = "Maqueta"
    ): String {
        val htmlBuilder = StringBuilder()
        htmlBuilder.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <script src="https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js"></script>
                <style>
                    :root {
                        --margin-top: ${marginTopMm}mm;
                        --margin-bottom: ${marginBottomMm}mm;
                        --margin-left: ${marginLeftMm}mm;
                        --margin-right: ${marginRightMm}mm;
                        --padding-top: ${paddingTopMm}mm;
                        --padding-bottom: ${paddingBottomMm}mm;
                        --padding-left: ${paddingLeftMm}mm;
                        --padding-right: ${paddingRightMm}mm;
                        --col-width: ${columnWidthMm}mm;
                        --col-gap: ${columnGapMm}mm;
                        --font-size: ${fontSizeSp}px;
                        --text-color: $textColorHex;
                    }
                    * {
                        box-sizing: border-box;
                    }
                    @page {
                        size: auto;
                        margin: 0mm;
                    }
                    body {
                        margin: 0;
                        padding: 0;
                        background-color: #FAF7F2; /* Beautiful off-white paper texture styling */
                        color: var(--text-color);
                        font-family: 'Georgia', 'Times New Roman', serif;
                        font-size: var(--font-size);
                        line-height: $lineHeight;
                        overflow-x: scroll;
                        overflow-y: hidden;
                        height: 100vh;
                        width: 100vw;
                        -webkit-user-select: text;
                        user-select: text;
                    }
                    .book-canvas {
                        height: 100vh;
                        display: flex;
                        flex-direction: row;
                    }
                    .columns-container {
                        column-width: var(--col-width);
                        column-gap: var(--col-gap);
                        column-fill: auto;
                        height: calc(100vh - var(--margin-top) - var(--margin-bottom) - var(--padding-top) - var(--padding-bottom));
                        margin-top: var(--margin-top);
                        margin-bottom: var(--margin-bottom);
                        margin-left: var(--margin-left);
                        margin-right: var(--margin-right);
                        padding-top: var(--padding-top);
                        padding-bottom: var(--padding-bottom);
                        padding-left: var(--padding-left);
                        padding-right: var(--padding-right);
                    }
                    .section-break {
                        break-before: column;
                        page-break-before: column;
                        margin-bottom: 24px;
                    }
                    h1 {
                        text-align: center;
                        font-size: 1.6em;
                        font-weight: bold;
                        color: #0F172A;
                        margin-top: 20px;
                        margin-bottom: 15px;
                        break-after: avoid;
                        page-break-after: avoid;
                        font-family: 'Georgia', serif;
                    }
                    h2 {
                        text-align: center;
                        font-size: 1.2em;
                        font-style: italic;
                        font-weight: normal;
                        color: #334155;
                        margin-top: 10px;
                        margin-bottom: 25px;
                        break-after: avoid;
                        page-break-after: avoid;
                    }
                    p {
                        text-align: justify;
                        text-indent: 1.5em;
                        margin: 0 0 12px 0;
                        font-size: 1em;
                    }
                    p.first {
                        text-indent: 0;
                    }
                    p.first::first-letter {
                        font-size: 3.2em;
                        float: left;
                        line-height: 0.85;
                        margin-right: 6px;
                        margin-top: 4px;
                        font-weight: bold;
                        color: #0F172A;
                    }
                    /* Custom elegant scrollbar styling */
                    ::-webkit-scrollbar {
                        height: 6px;
                    }
                    ::-webkit-scrollbar-track {
                        background: #F1ECE4;
                    }
                    ::-webkit-scrollbar-thumb {
                        background: #C4B29E;
                        border-radius: 3px;
                    }
                </style>
                <script>
                    var projectConfig = {
                        title: "${title.replace("\"", "\\\"")}",
                        pageWidth: $pageWidthMm,
                        pageHeight: $pageHeightMm,
                        showPageNumbers: $showPageNumbers,
                        pageNumbersStartAtPage: $pageNumbersStartAtPage,
                        pageNumbersStartFromValue: $pageNumbersStartFromValue,
                        fontSizeSp: $fontSizeSp,
                        lineHeightMultiplier: $lineHeight
                    };

                    function updateHtmlFooter(pages, current) {
                        var footer = document.getElementById('html-page-footer');
                        if (footer) {
                            if (!projectConfig.showPageNumbers) {
                                footer.style.display = 'none';
                                return;
                            }
                            footer.style.display = 'block';
                            var pageNum = current + 1;
                            var displayNum = pageNum;
                            if (pageNum >= projectConfig.pageNumbersStartAtPage) {
                                displayNum = pageNum - projectConfig.pageNumbersStartAtPage + projectConfig.pageNumbersStartFromValue;
                            }
                            footer.innerHTML = "PÁG. " + displayNum + " de " + pages;
                        }
                    }

                    function reportPages() {
                        if (window.AndroidFlowPageCounter) {
                            var totalWidth = document.documentElement.scrollWidth;
                            var viewWidth = window.innerWidth;
                            var pages = Math.max(1, Math.round(totalWidth / viewWidth));
                            var current = Math.round(window.scrollX / viewWidth);
                            window.AndroidFlowPageCounter.sendLayoutMetrics(pages, current);
                            updateHtmlFooter(pages, current);
                        }
                    }
                    function scrollToPage(pageIndex) {
                        var scrollXPosition = pageIndex * window.innerWidth;
                        if (Math.abs(window.scrollX - scrollXPosition) > 5) {
                            window.scrollTo({
                                left: scrollXPosition,
                                behavior: 'smooth'
                            });
                        }
                    }
                    window.onload = function() {
                        reportPages();
                        setTimeout(reportPages, 300);
                        setTimeout(reportPages, 800);
                    };
                    window.onresize = reportPages;
                    window.onscroll = function() {
                        var viewWidth = window.innerWidth;
                        var current = Math.round(window.scrollX / viewWidth);
                        var totalWidth = document.documentElement.scrollWidth;
                        var pages = Math.max(1, Math.round(totalWidth / viewWidth));
                        if (window.AndroidFlowPageCounter) {
                            window.AndroidFlowPageCounter.sendLayoutMetrics(pages, current);
                        }
                        updateHtmlFooter(pages, current);
                    };

                    function drawJustifiedText(pdfDoc, text, xStart, yPos, maxWidth, fontSize) {
                        var words = text.split(/\s+/);
                        if (words.length <= 1) {
                            pdfDoc.text(text, xStart, yPos);
                            return;
                        }
                        
                        var totalWordsWidth = 0;
                        for (var i = 0; i < words.length; i++) {
                            totalWordsWidth += pdfDoc.getStringUnitWidth(words[i]) * fontSize / pdfDoc.internal.scaleFactor;
                        }
                        
                        var totalSpacesWidth = maxWidth - totalWordsWidth;
                        var numSpaces = words.length - 1;
                        var spaceWidth = totalSpacesWidth / numSpaces;
                        
                        if (spaceWidth > (fontSize * 0.7)) {
                            pdfDoc.text(text, xStart, yPos);
                            return;
                        }
                        
                        var currentX = xStart;
                        for (var i = 0; i < words.length; i++) {
                            pdfDoc.text(words[i], currentX, yPos);
                            currentX += (pdfDoc.getStringUnitWidth(words[i]) * fontSize / pdfDoc.internal.scaleFactor) + spaceWidth;
                        }
                    }

                    function generateAndExportPdf() {
                        try {
                            if (!window.jspdf) {
                                alert("Error: El motor de PDF jsPDF no está cargado.");
                                return;
                            }
                            
                            var { jsPDF } = window.jspdf;
                            var pw = projectConfig.pageWidth || 148;
                            var ph = projectConfig.pageHeight || 210;
                            
                            var doc = new jsPDF({
                                orientation: 'portrait',
                                unit: 'mm',
                                format: [pw, ph]
                            });
                            
                            var marginTop = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--margin-top')) || 20;
                            var marginBottom = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--margin-bottom')) || 20;
                            var marginLeft = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--margin-left')) || 20;
                            var marginRight = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--margin-right')) || 20;
                            var paddingTop = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--padding-top')) || 5;
                            var paddingBottom = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--padding-bottom')) || 5;
                            var paddingLeft = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--padding-left')) || 5;
                            var paddingRight = parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--padding-right')) || 5;
                            
                            var contentLeft = marginLeft + paddingLeft;
                            var contentRight = pw - (marginRight + paddingRight);
                            var contentTop = marginTop + paddingTop;
                            var contentBottom = ph - (marginBottom + paddingBottom);
                            var contentWidth = contentRight - contentLeft;
                            
                            var currentPageNum = 1;
                            var yCursor = contentTop;
                            
                            function drawFooter(pdfDoc, pageNum) {
                                if (projectConfig.showPageNumbers && pageNum >= projectConfig.pageNumbersStartAtPage) {
                                    var displayNum = pageNum - projectConfig.pageNumbersStartAtPage + projectConfig.pageNumbersStartFromValue;
                                    pdfDoc.setFont("times", "bold");
                                    pdfDoc.setFontSize(10);
                                    pdfDoc.setTextColor(80, 80, 80);
                                    
                                    var str = displayNum.toString();
                                    var textWidth = pdfDoc.getStringUnitWidth(str) * 10 / pdfDoc.internal.scaleFactor;
                                    var xCentered = (pw - textWidth) / 2;
                                    var yFooter = ph - (marginBottom / 2);
                                    pdfDoc.text(str, xCentered, yFooter);
                                }
                            }
                            
                            function checkPageBreak(neededHeight) {
                                if (yCursor + neededHeight > contentBottom) {
                                    drawFooter(doc, currentPageNum);
                                    doc.addPage();
                                    currentPageNum++;
                                    yCursor = contentTop;
                                    return true;
                                }
                                return false;
                            }
                            
                            var sections = document.querySelectorAll('.section-break');
                            var firstElement = true;
                            
                            for (var i = 0; i < sections.length; i++) {
                                var section = sections[i];
                                if (!firstElement) {
                                    drawFooter(doc, currentPageNum);
                                    doc.addPage();
                                    currentPageNum++;
                                    yCursor = contentTop;
                                }
                                firstElement = false;
                                
                                var elements = section.children;
                                for (var j = 0; j < elements.length; j++) {
                                    var el = elements[j];
                                    var tagName = el.tagName.toLowerCase();
                                    var text = el.innerText.trim();
                                    if (!text) continue;
                                    
                                    if (tagName === 'h1') {
                                        doc.setFont("times", "bold");
                                        doc.setFontSize(18);
                                        doc.setTextColor(15, 23, 42);
                                        
                                        var lines = doc.splitTextToSize(text, contentWidth);
                                        var lineHeight = 7.5;
                                        
                                        checkPageBreak(lineHeight * lines.length + 5);
                                        yCursor += 5;
                                        
                                        for (var k = 0; k < lines.length; k++) {
                                            checkPageBreak(lineHeight);
                                            var textW = doc.getStringUnitWidth(lines[k]) * 18 / doc.internal.scaleFactor;
                                            var xCenter = contentLeft + (contentWidth - textW) / 2;
                                            doc.text(lines[k], xCenter, yCursor);
                                            yCursor += lineHeight;
                                        }
                                        yCursor += 4;
                                        
                                    } else if (tagName === 'h2') {
                                        doc.setFont("times", "italic");
                                        doc.setFontSize(14);
                                        doc.setTextColor(51, 65, 85);
                                        
                                        var lines = doc.splitTextToSize(text, contentWidth);
                                        var lineHeight = 6;
                                        
                                        checkPageBreak(lineHeight * lines.length + 3);
                                        
                                        for (var k = 0; k < lines.length; k++) {
                                            checkPageBreak(lineHeight);
                                            var textW = doc.getStringUnitWidth(lines[k]) * 14 / doc.internal.scaleFactor;
                                            var xCenter = contentLeft + (contentWidth - textW) / 2;
                                            doc.text(lines[k], xCenter, yCursor);
                                            yCursor += lineHeight;
                                        }
                                        yCursor += 6;
                                        
                                    } else if (tagName === 'p') {
                                        doc.setFont("times", "normal");
                                        doc.setFontSize(11);
                                        doc.setTextColor(30, 41, 59);
                                        
                                        var isFirstPara = el.classList.contains('first');
                                        var textIndent = isFirstPara ? 0 : 10;
                                        
                                        if (isFirstPara && text.length > 2) {
                                            var firstLetter = text.charAt(0);
                                            var remainingText = text.substring(1);
                                            
                                            doc.setFont("times", "bold");
                                            doc.setFontSize(30);
                                            doc.setTextColor(15, 23, 42);
                                            var dpWidth = doc.getStringUnitWidth(firstLetter) * 30 / doc.internal.scaleFactor;
                                            
                                            doc.setFont("times", "normal");
                                            doc.setFontSize(11);
                                            doc.setTextColor(30, 41, 59);
                                            
                                            var remainingWidth = contentWidth - dpWidth - 2;
                                            var linesOfRemaining = doc.splitTextToSize(remainingText, remainingWidth);
                                            var firstLineRemaining = linesOfRemaining[0];
                                            
                                            var otherLines = doc.splitTextToSize(remainingText.substring(firstLineRemaining.length), contentWidth);
                                            var lineHeight = 5.2;
                                            
                                            checkPageBreak(lineHeight * 3);
                                            
                                            doc.setFont("times", "bold");
                                            doc.setFontSize(30);
                                            doc.setTextColor(15, 23, 42);
                                            doc.text(firstLetter, contentLeft, yCursor + 8);
                                            
                                            doc.setFont("times", "normal");
                                            doc.setFontSize(11);
                                            doc.setTextColor(30, 41, 59);
                                            doc.text(firstLineRemaining, contentLeft + dpWidth + 2, yCursor + 3);
                                            yCursor += lineHeight;
                                            
                                            for (var m = 0; m < otherLines.length; m++) {
                                                checkPageBreak(lineHeight);
                                                var textLine = otherLines[m].trim();
                                                if (textLine) {
                                                    if (m < otherLines.length - 1) {
                                                        drawJustifiedText(doc, textLine, contentLeft, yCursor, contentWidth, 11);
                                                    } else {
                                                        doc.text(textLine, contentLeft, yCursor);
                                                    }
                                                    yCursor += lineHeight;
                                                }
                                            }
                                            yCursor += 3;
                                        } else {
                                            var lines = doc.splitTextToSize(text, contentWidth - textIndent);
                                            var lineHeight = 5.2;
                                            
                                            checkPageBreak(lineHeight + 2);
                                            
                                            for (var k = 0; k < lines.length; k++) {
                                                checkPageBreak(lineHeight);
                                                var textLine = lines[k].trim();
                                                if (k === 0) {
                                                    doc.text(textLine, contentLeft + textIndent, yCursor);
                                                } else if (k < lines.length - 1) {
                                                    drawJustifiedText(doc, textLine, contentLeft, yCursor, contentWidth, 11);
                                                } else {
                                                    doc.text(textLine, contentLeft, yCursor);
                                                }
                                                yCursor += lineHeight;
                                            }
                                            yCursor += 3;
                                        }
                                    }
                                }
                            }
                            
                            drawFooter(doc, currentPageNum);
                            
                            var base64Data = doc.output('datauristring').split(',')[1];
                            if (window.AndroidFlowPageCounter && window.AndroidFlowPageCounter.onPdfGenerated) {
                                window.AndroidFlowPageCounter.onPdfGenerated(base64Data, projectConfig.title || "Export");
                            }
                        } catch (e) {
                            alert("Fallo al generar el PDF con jsPDF: " + e.message);
                        }
                    }
                </script>
            </head>
            <body>
                <div class="book-canvas">
                    <div class="columns-container">
        """.trimIndent())

        for (section in sections) {
            htmlBuilder.append("""<div class="section-break">""")
            if (section.title.isNotBlank()) {
                htmlBuilder.append("""<h1>${section.title}</h1>""")
            }
            if (section.subtitle != null && section.subtitle.isNotBlank()) {
                htmlBuilder.append("""<h2>${section.subtitle}</h2>""")
            }
            
            var isFirst = true
            for (para in section.paragraphs) {
                if (para.isNotBlank()) {
                    if (isFirst) {
                        htmlBuilder.append("""<p class="first">$para</p>""")
                        isFirst = false
                    } else {
                        htmlBuilder.append("""<p>$para</p>""")
                    }
                }
            }
            htmlBuilder.append("""</div>""")
        }

        htmlBuilder.append("""
                    </div>
                </div>
                <div id="html-page-footer" style="position: fixed; bottom: 8px; left: 50%; transform: translateX(-50%); font-family: 'Georgia', serif; font-size: 11px; color: #64748B; font-weight: bold; pointer-events: none; z-index: 1000; background: rgba(250,247,242,0.92); padding: 4px 10px; border-radius: 6px; letter-spacing: 0.05em; border: 1px solid rgba(196,178,158,0.3); font-variant-numeric: tabular-nums;">
                    PÁG. 1 de 1
                </div>
            </body>
            </html>
        """.trimIndent())

        return htmlBuilder.toString()
    }

    fun unparseRawText(sections: List<ParsedSection>): String {
        val sb = StringBuilder()
        for (section in sections) {
            if (section.title.isNotBlank() && section.title != "Sección General") {
                sb.append(section.title).append("\n\n")
            }
            if (section.subtitle != null && section.subtitle.isNotBlank()) {
                sb.append(section.subtitle).append("\n\n")
            }
            for (para in section.paragraphs) {
                if (para.isNotBlank()) {
                    sb.append(para).append("\n\n")
                }
            }
        }
        return sb.toString().trim()
    }
}
