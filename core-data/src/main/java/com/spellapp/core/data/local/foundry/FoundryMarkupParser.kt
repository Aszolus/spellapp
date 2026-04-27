package com.spellapp.core.data.local.foundry
import com.spellapp.core.model.RulesTextBlock
import com.spellapp.core.model.RulesTextDocument
import com.spellapp.core.model.RulesTextInline

internal object FoundryMarkupParser {
    private val tagRegex = Regex("<(/?)([A-Za-z0-9]+)([^>]*)>")
    private val attributeRegex = Regex("""([A-Za-z0-9:_-]+)\s*=\s*"([^"]*)"""")
    private val localizeRegex = Regex("""@Localize\[([^\]]+)\]""")
    private val whitespaceRegex = Regex("\\s+")
    private val repeatedNewlineRegex = Regex("(\\n){3,}")
    private val brRegex = Regex("<\\s*br\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val hrRegex = Regex("<\\s*hr\\s*/?\\s*>", RegexOption.IGNORE_CASE)
    private val paragraphCloseRegex = Regex("<\\s*/p\\s*>", RegexOption.IGNORE_CASE)
    private val paragraphOpenRegex = Regex("<\\s*p[^>]*\\s*>", RegexOption.IGNORE_CASE)
    private val listItemOpenRegex = Regex("<\\s*li[^>]*\\s*>", RegexOption.IGNORE_CASE)
    private val listItemCloseRegex = Regex("<\\s*/li\\s*>", RegexOption.IGNORE_CASE)
    private val blockTagRegex = Regex("<\\s*/?(ul|ol|h1|h2|h3|h4|h5|h6)[^>]*\\s*>", RegexOption.IGNORE_CASE)
    private val tagStripRegex = Regex("<[^>]+>")
    private val inlineRollLabelRegex = Regex("""\[\[[^\]]+\]\]\{([^}]*)\}""")
    private val inlineRollRegex = Regex("""\[\[[^\]]+\]\]""")
    private val macroWithLabelRegex = Regex("""@(UUID|Damage|Check|Template)\[[^\]]+\](\{([^}]*)\})?""")
    private val numericEntityRegex = Regex("""&#(\d+);""")
    private val hexEntityRegex = Regex("""&#x([0-9a-fA-F]+);""")
    private val namedEntityRegex = Regex("""&([a-zA-Z]+);""")

    fun parse(
        descriptionRaw: String?,
        description: String?,
        localizationResolver: FoundryLocalizationResolver?,
        itemLevel: Int? = null,
        itemRank: Int? = null,
    ): RulesTextDocument {
        if (descriptionRaw.isNullOrBlank()) {
            return RulesTextDocument.fromPlainText(description)
        }

        val expanded = resolveLocalizedMarkup(descriptionRaw, localizationResolver)
        val parsed = parseStructuredDocument(
            input = expanded,
            localizationResolver = localizationResolver,
            itemLevel = itemLevel,
            itemRank = itemRank,
        )
        return if (parsed.blocks.isNotEmpty()) {
            parsed
        } else {
            RulesTextDocument.fromPlainText(
                fallbackPlainText(
                    input = expanded,
                    localizationResolver = localizationResolver,
                    itemLevel = itemLevel,
                    itemRank = itemRank,
                ).ifBlank { description.orEmpty() },
            )
        }
    }

    private fun parseStructuredDocument(
        input: String,
        localizationResolver: FoundryLocalizationResolver?,
        itemLevel: Int?,
        itemRank: Int?,
    ): RulesTextDocument {
        val root = parseHtmlTree(input) ?: return RulesTextDocument()
        return RulesTextDocument(
            blocks = nodesToBlocks(
                nodes = root.children,
                localizationResolver = localizationResolver,
                itemLevel = itemLevel,
                itemRank = itemRank,
            ),
        )
    }

    private fun parseHtmlTree(input: String): HtmlElement? {
        return runCatching {
            val root = HtmlElement(name = "root")
            val stack = ArrayDeque<HtmlElement>()
            stack.addLast(root)
            var cursor = 0
            tagRegex.findAll(input).forEach { match ->
                if (match.range.first > cursor) {
                    val text = input.substring(cursor, match.range.first)
                    if (text.isNotEmpty()) {
                        stack.last().children += HtmlText(text)
                    }
                }

                val isClosing = match.groupValues[1] == "/"
                val tagName = match.groupValues[2].lowercase()
                val attrText = match.groupValues[3]
                val isSelfClosing = attrText.trimEnd().endsWith("/") || tagName in setOf("br", "hr")

                if (isClosing) {
                    while (stack.size > 1) {
                        val current = stack.removeLast()
                        if (current.name == tagName) {
                            break
                        }
                    }
                } else {
                    val element = HtmlElement(
                        name = tagName,
                        attributes = parseAttributes(attrText),
                    )
                    stack.last().children += element
                    if (!isSelfClosing) {
                        stack.addLast(element)
                    }
                }
                cursor = match.range.last + 1
            }

            if (cursor < input.length) {
                val tail = input.substring(cursor)
                if (tail.isNotEmpty()) {
                    stack.last().children += HtmlText(tail)
                }
            }
            root
        }.getOrNull()
    }

    private fun parseAttributes(attrText: String): Map<String, String> {
        return buildMap {
            attributeRegex.findAll(attrText).forEach { match ->
                put(match.groupValues[1].lowercase(), decodeHtmlText(match.groupValues[2]).trim())
            }
        }
    }

    private fun nodesToBlocks(
        nodes: List<HtmlNode>,
        localizationResolver: FoundryLocalizationResolver?,
        itemLevel: Int?,
        itemRank: Int?,
    ): List<RulesTextBlock> {
        val blocks = mutableListOf<RulesTextBlock>()
        val looseInlineNodes = mutableListOf<HtmlNode>()

        fun flushLooseInlineNodes() {
            val inlines = nodesToInlines(
                nodes = looseInlineNodes,
                localizationResolver = localizationResolver,
                itemLevel = itemLevel,
                itemRank = itemRank,
            )
            if (inlines.isNotEmpty()) {
                blocks += RulesTextBlock.Paragraph(inlines = inlines)
            }
            looseInlineNodes.clear()
        }

        nodes.forEach { node ->
            when (node) {
                is HtmlText -> {
                    if (decodeHtmlText(node.value).isNotBlank()) {
                        looseInlineNodes += node
                    }
                }

                is HtmlElement -> when (node.name) {
                    "p" -> {
                        flushLooseInlineNodes()
                        val inlines = nodesToInlines(
                            nodes = node.children,
                            localizationResolver = localizationResolver,
                            itemLevel = itemLevel,
                            itemRank = itemRank,
                        )
                        if (inlines.isNotEmpty()) {
                            blocks += RulesTextBlock.Paragraph(inlines = inlines)
                        }
                    }

                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        flushLooseInlineNodes()
                        val inlines = nodesToInlines(
                            nodes = node.children,
                            localizationResolver = localizationResolver,
                            itemLevel = itemLevel,
                            itemRank = itemRank,
                        )
                        if (inlines.isNotEmpty()) {
                            blocks += RulesTextBlock.Heading(
                                level = node.name.removePrefix("h").toIntOrNull() ?: 1,
                                inlines = inlines,
                            )
                        }
                    }

                    "ul", "ol" -> {
                        flushLooseInlineNodes()
                        val items = node.children
                            .filterIsInstance<HtmlElement>()
                            .filter { it.name == "li" }
                            .mapNotNull { listItem ->
                                val childBlocks = nodesToBlocks(
                                    nodes = listItem.children,
                                    localizationResolver = localizationResolver,
                                    itemLevel = itemLevel,
                                    itemRank = itemRank,
                                )
                                if (childBlocks.isEmpty()) {
                                    null
                                } else {
                                    com.spellapp.core.model.RulesTextListItem(blocks = childBlocks)
                                }
                            }
                        if (items.isNotEmpty()) {
                            blocks += RulesTextBlock.ListBlock(
                                ordered = node.name == "ol",
                                items = items,
                            )
                        }
                    }

                    "li" -> {
                        looseInlineNodes += node
                    }

                    "hr" -> {
                        flushLooseInlineNodes()
                        blocks += RulesTextBlock.ThematicBreak
                    }

                    "br", "strong", "em", "span" -> {
                        looseInlineNodes += node
                    }

                    else -> {
                        if (node.children.isEmpty()) {
                            return@forEach
                        }
                        looseInlineNodes += node
                    }
                }
            }
        }

        flushLooseInlineNodes()
        return blocks
    }

    private fun nodesToInlines(
        nodes: List<HtmlNode>,
        localizationResolver: FoundryLocalizationResolver?,
        itemLevel: Int?,
        itemRank: Int?,
    ): List<RulesTextInline> {
        val inlines = mutableListOf<RulesTextInline>()
        nodes.forEach { node ->
            when (node) {
                is HtmlText -> inlines += parseInlineText(
                    input = decodeHtmlText(node.value),
                    localizationResolver = localizationResolver,
                    itemLevel = itemLevel,
                    itemRank = itemRank,
                )
                is HtmlElement -> when {
                    node.name == "br" -> inlines += RulesTextInline.Text("\n")
                    node.name == "strong" -> {
                        val children = nodesToInlines(
                            nodes = node.children,
                            localizationResolver = localizationResolver,
                            itemLevel = itemLevel,
                            itemRank = itemRank,
                        )
                        if (children.isNotEmpty()) {
                            inlines += RulesTextInline.Strong(children)
                        }
                    }

                    node.name == "em" -> {
                        val children = nodesToInlines(
                            nodes = node.children,
                            localizationResolver = localizationResolver,
                            itemLevel = itemLevel,
                            itemRank = itemRank,
                        )
                        if (children.isNotEmpty()) {
                            inlines += RulesTextInline.Emphasis(children)
                        }
                    }

                    node.name == "span" && node.attributes["class"]?.contains("action-glyph", ignoreCase = true) == true -> {
                        val glyph = extractTextContent(node.children).trim()
                        if (glyph.isNotBlank()) {
                            inlines += RulesTextInline.ActionGlyph(glyph)
                        }
                    }

                    else -> inlines += nodesToInlines(
                        nodes = node.children,
                        localizationResolver = localizationResolver,
                        itemLevel = itemLevel,
                        itemRank = itemRank,
                    )
                }
            }
        }
        return mergeAdjacentText(inlines)
    }

    private fun parseInlineText(
        input: String,
        localizationResolver: FoundryLocalizationResolver?,
        itemLevel: Int?,
        itemRank: Int?,
    ): List<RulesTextInline> {
        if (input.isBlank()) {
            return emptyList()
        }

        val tokens = mutableListOf<RulesTextInline>()
        val buffer = StringBuilder()
        var index = 0

        fun flushBuffer() {
            val text = normalizeInlineText(buffer.toString())
            if (text.isNotBlank()) {
                tokens += RulesTextInline.Text(text)
            }
            buffer.clear()
        }

        while (index < input.length) {
            when {
                input.startsWith("@UUID[", index) -> {
                    val macro = parseMacro(input, index, "UUID")
                    if (macro == null) {
                        buffer.append(input[index])
                        index += 1
                    } else {
                        flushBuffer()
                        val label = macro.label?.takeIf { it.isNotBlank() } ?: labelFromUuid(macro.body)
                        tokens += RulesTextInline.Reference(
                            key = compendiumReferenceKey(macro.body),
                            label = label,
                        )
                        index = macro.nextIndex
                    }
                }

                input.startsWith("@Damage[", index) -> {
                    val macro = parseMacro(input, index, "Damage")
                    if (macro == null) {
                        buffer.append(input[index])
                        index += 1
                    } else {
                        flushBuffer()
                        tokens += RulesTextInline.Damage(
                            formula = macro.body.trim(),
                            label = macro.label?.takeIf { it.isNotBlank() }
                                ?: formatDamageLabel(
                                    formula = macro.body.trim(),
                                    itemLevel = itemLevel,
                                    itemRank = itemRank,
                                ),
                        )
                        index = macro.nextIndex
                    }
                }

                input.startsWith("@Check[", index) -> {
                    val macro = parseMacro(input, index, "Check")
                    if (macro == null) {
                        buffer.append(input[index])
                        index += 1
                    } else {
                        flushBuffer()
                        tokens += RulesTextInline.Check(
                            target = macro.body.trim(),
                            label = macro.label?.takeIf { it.isNotBlank() } ?: "Check: ${macro.body.trim()}",
                        )
                        index = macro.nextIndex
                    }
                }

                input.startsWith("@Template[", index) -> {
                    val macro = parseMacro(input, index, "Template")
                    if (macro == null) {
                        buffer.append(input[index])
                        index += 1
                    } else {
                        flushBuffer()
                        tokens += RulesTextInline.Template(
                            params = macro.body.trim(),
                            label = macro.label?.takeIf { it.isNotBlank() } ?: "Template: ${macro.body.trim()}",
                        )
                        index = macro.nextIndex
                    }
                }

                input.startsWith("@Localize[", index) -> {
                    val macro = parseMacro(input, index, "Localize")
                    if (macro == null) {
                        buffer.append(input[index])
                        index += 1
                    } else {
                        flushBuffer()
                        val localized = resolveLocalizedMarkup("@Localize[${macro.body}]", localizationResolver)
                        tokens += parseInlineText(
                            input = localized,
                            localizationResolver = localizationResolver,
                            itemLevel = itemLevel,
                            itemRank = itemRank,
                        )
                        index = macro.nextIndex
                    }
                }

                input.startsWith("[[", index) -> {
                    val inlineRoll = parseInlineRoll(input, index)
                    if (inlineRoll == null) {
                        buffer.append(input[index])
                        index += 1
                    } else {
                        flushBuffer()
                        if (inlineRoll.label.isNotBlank()) {
                            tokens += RulesTextInline.InlineRoll(
                                formula = inlineRoll.formula,
                                label = inlineRoll.label,
                            )
                        }
                        index = inlineRoll.nextIndex
                    }
                }

                else -> {
                    buffer.append(input[index])
                    index += 1
                }
            }
        }

        flushBuffer()
        return mergeAdjacentText(tokens)
    }

    private fun parseMacro(
        input: String,
        startIndex: Int,
        macroName: String,
    ): ParsedMacro? {
        val prefix = "@$macroName["
        if (!input.startsWith(prefix, startIndex)) {
            return null
        }

        val bodyStart = startIndex + prefix.length
        val bodyEnd = findMatchingBracket(input, bodyStart - 1) ?: return null
        val body = input.substring(bodyStart, bodyEnd)
        var nextIndex = bodyEnd + 1
        var label: String? = null
        if (nextIndex < input.length && input[nextIndex] == '{') {
            val labelEnd = findMatchingBrace(input, nextIndex) ?: return null
            label = input.substring(nextIndex + 1, labelEnd)
            nextIndex = labelEnd + 1
        }

        return ParsedMacro(
            body = body,
            label = label,
            nextIndex = nextIndex,
        )
    }

    private fun parseInlineRoll(
        input: String,
        startIndex: Int,
    ): ParsedInlineRoll? {
        if (!input.startsWith("[[", startIndex)) {
            return null
        }

        val endIndex = input.indexOf("]]", startIndex + 2)
        if (endIndex < 0) {
            return null
        }

        val formula = input.substring(startIndex + 2, endIndex)
        var nextIndex = endIndex + 2
        var label = ""
        if (nextIndex < input.length && input[nextIndex] == '{') {
            val labelEnd = findMatchingBrace(input, nextIndex) ?: return null
            label = input.substring(nextIndex + 1, labelEnd)
            nextIndex = labelEnd + 1
        }

        return ParsedInlineRoll(
            formula = formula,
            label = label,
            nextIndex = nextIndex,
        )
    }

    private fun findMatchingBracket(
        input: String,
        openBracketIndex: Int,
    ): Int? {
        var depth = 0
        for (index in openBracketIndex until input.length) {
            when (input[index]) {
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return null
    }

    private fun findMatchingBrace(
        input: String,
        openBraceIndex: Int,
    ): Int? {
        var depth = 0
        for (index in openBraceIndex until input.length) {
            when (input[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return null
    }

    private fun resolveLocalizedMarkup(
        input: String,
        localizationResolver: FoundryLocalizationResolver?,
    ): String {
        if (localizationResolver == null) {
            return input
        }

        var current = input
        repeat(8) {
            var changed = false
            current = localizeRegex.replace(current) { match ->
                val key = match.groupValues.getOrElse(1) { "" }
                val localized = localizationResolver.resolve(key)
                if (localized.isNullOrBlank()) {
                    key
                } else {
                    changed = true
                    localized
                }
            }
            if (!changed) {
                return current
            }
        }
        return current
    }

    private fun fallbackPlainText(
        input: String,
        localizationResolver: FoundryLocalizationResolver?,
        itemLevel: Int?,
        itemRank: Int?,
    ): String {
        var text = resolveLocalizedMarkup(input, localizationResolver)
        text = inlineRollLabelRegex.replace(text, "$1")
        text = inlineRollRegex.replace(text, "")
        text = macroWithLabelRegex.replace(text) { match ->
            val raw = match.value
            val label = match.groups[3]?.value
            if (!label.isNullOrBlank()) {
                label
            } else {
                when {
                    raw.startsWith("@UUID[") -> {
                        val body = raw.substringAfter("@UUID[").substringBeforeLast("]")
                        labelFromUuid(body)
                    }

                    raw.startsWith("@Damage[") -> formatDamageLabel(
                        formula = raw.substringAfter("@Damage[").substringBeforeLast("]"),
                        itemLevel = itemLevel,
                        itemRank = itemRank,
                    )
                    raw.startsWith("@Check[") -> "Check: ${raw.substringAfter("@Check[").substringBeforeLast("]")}"
                    raw.startsWith("@Template[") -> "Template: ${raw.substringAfter("@Template[").substringBeforeLast("]")}"
                    else -> raw
                }
            }
        }
        text = brRegex.replace(text, "\n")
        text = hrRegex.replace(text, "\n---\n")
        text = paragraphCloseRegex.replace(text, "\n")
        text = paragraphOpenRegex.replace(text, "")
        text = listItemOpenRegex.replace(text, "- ")
        text = listItemCloseRegex.replace(text, "\n")
        text = blockTagRegex.replace(text, "\n")
        text = tagStripRegex.replace(text, "")
        text = decodeHtmlText(text)
            .replace("\r", "")
            .replace(repeatedNewlineRegex, "\n\n")
            .trim()
        return text
    }

    private fun normalizeInlineText(text: String): String {
        val normalized = text
            .replace("\r", "")
            .replace(whitespaceRegex, " ")
        return normalized
    }

    private fun decodeHtmlText(text: String): String {
        var decoded = text
            .replace("\u00A0", " ")
        decoded = hexEntityRegex.replace(decoded) { match ->
            match.groupValues[1]
                .toIntOrNull(16)
                ?.toChar()
                ?.toString()
                ?: match.value
        }
        decoded = numericEntityRegex.replace(decoded) { match ->
            match.groupValues[1]
                .toIntOrNull()
                ?.toChar()
                ?.toString()
                ?: match.value
        }
        decoded = namedEntityRegex.replace(decoded) { match ->
            namedHtmlEntities[match.groupValues[1].lowercase()] ?: match.value
        }
        return decoded
    }

    private fun formatDamageLabel(
        formula: String,
        itemLevel: Int?,
        itemRank: Int?,
    ): String {
        val substituted = substituteItemValues(
            text = formula,
            itemLevel = itemLevel,
            itemRank = itemRank,
        )
        val simplified = simplifyFormula(substituted)
        val coreFormula = simplified.substringBefore('|').trim()
        val clauses = splitTopLevel(coreFormula, ',')
            .map { clause -> formatDamageClause(clause) }
            .filter { it.isNotBlank() }
        if (clauses.isNotEmpty()) {
            return clauses.joinToString(" + ")
        }
        return simplified.ifBlank { formula }
    }

    private fun substituteItemValues(
        text: String,
        itemLevel: Int?,
        itemRank: Int?,
    ): String {
        var substituted = text
        itemLevel?.let { level ->
            substituted = substituted.replace("@item.level", level.toString(), ignoreCase = true)
        }
        itemRank?.let { rank ->
            substituted = substituted.replace("@item.rank", rank.toString(), ignoreCase = true)
        }
        return substituted
    }

    private fun simplifyFormula(
        formula: String,
    ): String {
        var current = formula.trim()
        repeat(8) {
            val previous = current
            current = current.replace(
                Regex("""(?i)\b(ceil|floor)\(([-+*/\d\s.]+)\)"""),
            ) { match ->
                val operation = match.groupValues[1].lowercase()
                val expression = match.groupValues[2]
                val value = evaluateArithmeticExpression(expression) ?: return@replace match.value
                val resolved = when (operation) {
                    "ceil" -> kotlin.math.ceil(value)
                    else -> kotlin.math.floor(value)
                }
                resolved.toInt().toString()
            }
            current = current.replace(
                Regex("""\(([-+*/\d\s.]+)\)"""),
            ) { match ->
                evaluateArithmeticExpression(match.groupValues[1])
                    ?.let { value ->
                        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
                    }
                    ?: match.value
            }
            current = current.replace(
                Regex("""(?<![A-Za-z])(\d+)\s*([*/])\s*(\d+)(?![A-Za-z])"""),
            ) { match ->
                val left = match.groupValues[1].toDoubleOrNull() ?: return@replace match.value
                val right = match.groupValues[3].toDoubleOrNull() ?: return@replace match.value
                val result = if (match.groupValues[2] == "*") left * right else left / right
                if (result % 1.0 == 0.0) result.toInt().toString() else result.toString()
            }
            current = current.replace(Regex("""\((\d+)\)"""), "$1")
            current = current.replace(Regex("""\s+"""), " ").trim()
            if (current == previous) {
                return current
            }
        }
        return current
    }

    private fun formatDamageClause(
        clause: String,
    ): String {
        val trimmedClause = clause.trim().substringBefore('|').trim()
        if (trimmedClause.isBlank()) {
            return ""
        }
        val tagStart = trimmedClause.lastIndexOf('[')
        val tagEnd = trimmedClause.lastIndexOf(']')
        if (tagStart >= 0 && tagEnd > tagStart) {
            val amount = trimmedClause.substring(0, tagStart).trim()
            val tags = trimmedClause.substring(tagStart + 1, tagEnd)
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val descriptors = tags.joinToString(" ")
            return listOf(amount, descriptors)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()
        }
        return trimmedClause
    }

    private fun splitTopLevel(
        input: String,
        separator: Char,
    ): List<String> {
        if (input.isBlank()) {
            return emptyList()
        }
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var bracketDepth = 0
        var parenDepth = 0
        input.forEach { char ->
            when (char) {
                '[' -> bracketDepth += 1
                ']' -> bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                '(' -> parenDepth += 1
                ')' -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
            }
            if (char == separator && bracketDepth == 0 && parenDepth == 0) {
                parts += current.toString()
                current.clear()
            } else {
                current.append(char)
            }
        }
        parts += current.toString()
        return parts
    }

    private fun evaluateArithmeticExpression(
        expression: String,
    ): Double? {
        val normalized = expression.replace(" ", "")
        if (normalized.isBlank() || normalized.any { it !in "0123456789.+-*/" }) {
            return null
        }

        val values = ArrayDeque<Double>()
        val operators = ArrayDeque<Char>()
        var index = 0
        while (index < normalized.length) {
            val char = normalized[index]
            if (char.isDigit() || char == '.' || (char == '-' && isUnaryMinus(normalized, index))) {
                val start = index
                index += 1
                while (index < normalized.length && (normalized[index].isDigit() || normalized[index] == '.')) {
                    index += 1
                }
                values.addLast(normalized.substring(start, index).toDoubleOrNull() ?: return null)
                continue
            }
            if (char !in "+-*/") {
                return null
            }
            while (operators.isNotEmpty() && precedence(operators.last()) >= precedence(char)) {
                applyOperator(values, operators.removeLast()) ?: return null
            }
            operators.addLast(char)
            index += 1
        }

        while (operators.isNotEmpty()) {
            applyOperator(values, operators.removeLast()) ?: return null
        }
        return values.singleOrNull()
    }

    private fun isUnaryMinus(
        expression: String,
        index: Int,
    ): Boolean {
        return expression[index] == '-' && (index == 0 || expression[index - 1] in "+-*/")
    }

    private fun precedence(
        operator: Char,
    ): Int {
        return when (operator) {
            '*', '/' -> 2
            '+', '-' -> 1
            else -> 0
        }
    }

    private fun applyOperator(
        values: ArrayDeque<Double>,
        operator: Char,
    ): Double? {
        val right = values.removeLastOrNull() ?: return null
        val left = values.removeLastOrNull() ?: return null
        val result = when (operator) {
            '+' -> left + right
            '-' -> left - right
            '*' -> left * right
            '/' -> left / right
            else -> return null
        }
        values.addLast(result)
        return result
    }

    private fun mergeAdjacentText(tokens: List<RulesTextInline>): List<RulesTextInline> {
        if (tokens.isEmpty()) {
            return emptyList()
        }

        val merged = mutableListOf<RulesTextInline>()
        tokens.forEach { token ->
            val previous = merged.lastOrNull()
            if (token is RulesTextInline.Text && previous is RulesTextInline.Text) {
                merged[merged.lastIndex] = previous.copy(text = previous.text + token.text)
            } else {
                merged += token
            }
        }
        return merged.filterNot {
            it is RulesTextInline.Text && it.text.isBlank()
        }
    }

    private fun extractTextContent(nodes: List<HtmlNode>): String {
        return buildString {
            nodes.forEach { node ->
                when (node) {
                    is HtmlText -> append(decodeHtmlText(node.value))
                    is HtmlElement -> append(extractTextContent(node.children))
                }
            }
        }
    }

    private sealed interface HtmlNode

    private data class HtmlText(
        val value: String,
    ) : HtmlNode

    private data class HtmlElement(
        val name: String,
        val attributes: Map<String, String> = emptyMap(),
        val children: MutableList<HtmlNode> = mutableListOf(),
    ) : HtmlNode

    private data class ParsedMacro(
        val body: String,
        val label: String?,
        val nextIndex: Int,
    )

    private data class ParsedInlineRoll(
        val formula: String,
        val label: String,
        val nextIndex: Int,
    )

    private val namedHtmlEntities = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "ndash" to "-",
        "mdash" to "-",
        "minus" to "-",
        "hellip" to "...",
        "lsquo" to "'",
        "rsquo" to "'",
        "ldquo" to "\"",
        "rdquo" to "\"",
    )
}
