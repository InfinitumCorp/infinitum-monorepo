package com.infinitum.throne.core.json

sealed class JsonValue {
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue()
    data class Arr(val items: List<JsonValue>) : JsonValue()
    data class Str(val value: String) : JsonValue()
    data class Num(val value: Long) : JsonValue()
    data class Bool(val value: Boolean) : JsonValue()
    data object Null : JsonValue()

    fun field(key: String): JsonValue? = (this as? Obj)?.fields?.get(key)

    fun str(key: String): String? = when (val value = field(key)) {
        is Str -> value.value
        is Num -> value.value.toString()
        else -> null
    }

    fun long(key: String): Long? = when (val value = field(key)) {
        is Num -> value.value
        is Str -> value.value.toLongOrNull()
        else -> null
    }

    fun bool(key: String): Boolean? = (field(key) as? Bool)?.value

    fun strings(key: String): List<String>? {
        val array = field(key) as? Arr ?: return null
        if (array.items.any { it !is Str }) return null
        return array.items.map { (it as Str).value }
    }

    fun render(): String = buildString { appendValue(this@JsonValue) }

    companion object {
        fun parse(text: String): JsonValue {
            val parser = Parser(text)
            val value = parser.parseValue()
            parser.skip()
            if (!parser.done()) throw JsonException("Trailing JSON")
            return value
        }

        fun obj(vararg pairs: Pair<String, JsonValue>): Obj {
            val fields = LinkedHashMap<String, JsonValue>()
            for ((key, value) in pairs) {
                if (fields.containsKey(key)) throw JsonException("Duplicate field")
                fields[key] = value
            }
            return Obj(fields)
        }

        fun arr(items: List<JsonValue>): Arr = Arr(items)
        fun str(value: String): Str = Str(value)
        fun num(value: Long): Num = Num(value)
        fun bool(value: Boolean): Bool = Bool(value)
    }
}

class JsonException(message: String) : Exception(message)

private fun StringBuilder.appendValue(value: JsonValue) {
    when (value) {
        is JsonValue.Obj -> {
            append('{')
            value.fields.entries.forEachIndexed { index, (key, child) ->
                if (index > 0) append(',')
                appendString(key)
                append(':')
                appendValue(child)
            }
            append('}')
        }
        is JsonValue.Arr -> {
            append('[')
            value.items.forEachIndexed { index, child ->
                if (index > 0) append(',')
                appendValue(child)
            }
            append(']')
        }
        is JsonValue.Str -> appendString(value.value)
        is JsonValue.Num -> append(value.value.toString())
        is JsonValue.Bool -> append(if (value.value) "true" else "false")
        JsonValue.Null -> append("null")
    }
}

private fun StringBuilder.appendString(value: String) {
    append('"')
    for (ch in value) {
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (ch.code < 0x20) append("\\u%04x".format(ch.code)) else append(ch)
        }
    }
    append('"')
}

private class Parser(private val text: String) {
    private var index = 0

    fun done(): Boolean = index >= text.length

    fun skip() {
        while (index < text.length && text[index].isWhitespace()) index++
    }

    fun parseValue(): JsonValue {
        skip()
        if (done()) throw JsonException("Unexpected end")
        return when (text[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonValue.Str(parseString())
            't' -> {
                consume("true")
                JsonValue.Bool(true)
            }
            'f' -> {
                consume("false")
                JsonValue.Bool(false)
            }
            'n' -> {
                consume("null")
                JsonValue.Null
            }
            '-', in '0'..'9' -> parseNumber()
            else -> throw JsonException("Unexpected character")
        }
    }

    private fun parseObject(): JsonValue.Obj {
        expect('{')
        val fields = LinkedHashMap<String, JsonValue>()
        skip()
        if (peek('}')) {
            index++
            return JsonValue.Obj(fields)
        }
        while (true) {
            skip()
            if (done() || text[index] != '"') throw JsonException("Expected a field name")
            val key = parseString()
            skip()
            expect(':')
            val value = parseValue()
            if (fields.containsKey(key)) throw JsonException("Duplicate field")
            fields[key] = value
            skip()
            when {
                peek(',') -> index++
                peek('}') -> {
                    index++
                    return JsonValue.Obj(fields)
                }
                else -> throw JsonException("Expected a comma or the end of an object")
            }
        }
    }

    private fun parseArray(): JsonValue.Arr {
        expect('[')
        val items = ArrayList<JsonValue>()
        skip()
        if (peek(']')) {
            index++
            return JsonValue.Arr(items)
        }
        while (true) {
            items += parseValue()
            skip()
            when {
                peek(',') -> index++
                peek(']') -> {
                    index++
                    return JsonValue.Arr(items)
                }
                else -> throw JsonException("Expected a comma or the end of an array")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val out = StringBuilder()
        while (!done()) {
            val ch = text[index++]
            when {
                ch == '"' -> return out.toString()
                ch == '\\' -> out.append(escape())
                ch.code < 0x20 -> throw JsonException("Raw control character")
                else -> out.append(ch)
            }
        }
        throw JsonException("Unclosed string")
    }

    private fun escape(): Char {
        if (done()) throw JsonException("Bad escape")
        return when (val ch = text[index++]) {
            '"', '\\', '/' -> ch
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (index + 4 > text.length) throw JsonException("Bad unicode escape")
                val hex = text.substring(index, index + 4)
                val code = hex.toIntOrNull(16) ?: throw JsonException("Bad unicode escape")
                index += 4
                code.toChar()
            }
            else -> throw JsonException("Bad escape")
        }
    }

    private fun parseNumber(): JsonValue.Num {
        val start = index
        if (peek('-')) index++
        if (done() || text[index] !in '0'..'9') throw JsonException("Bad number")
        if (text[index] == '0') {
            index++
            if (!done() && text[index] in '0'..'9') throw JsonException("Leading zero")
        } else {
            while (!done() && text[index] in '0'..'9') index++
        }
        if (!done() && text[index] in setOf('.', 'e', 'E')) throw JsonException("Only integers are accepted")
        val literal = text.substring(start, index)
        if (literal == "-" || literal == "-0") throw JsonException("Bad number")
        val value = literal.toLongOrNull() ?: throw JsonException("Number out of range")
        return JsonValue.Num(value)
    }

    private fun consume(literal: String) {
        if (!text.startsWith(literal, index)) throw JsonException("Expected $literal")
        val next = index + literal.length
        if (next < text.length && text[next].isLetter()) throw JsonException("Expected $literal")
        index = next
    }

    private fun expect(ch: Char) {
        if (done() || text[index] != ch) throw JsonException("Expected $ch")
        index++
    }

    private fun peek(ch: Char): Boolean = !done() && text[index] == ch
}
