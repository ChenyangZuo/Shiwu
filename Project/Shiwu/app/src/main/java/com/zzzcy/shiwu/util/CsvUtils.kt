package com.zzzcy.shiwu.util

/** CSV 字段转义：含逗号/引号/换行时用双引号包裹 */
fun csvEscape(s: String): String =
    if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"" + s.replace("\"", "\"\"") + "\""
    } else {
        s
    }

/** 解析 CSV 文本（支持引号包裹、转义引号、字段内换行） */
fun parseCsv(text: String): List<List<String>> {
    val rows = ArrayList<List<String>>()
    val row = ArrayList<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes -> when (c) {
                '"' -> {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                else -> field.append(c)
            }
            c == '"' -> inQuotes = true
            c == ',' -> {
                row.add(field.toString())
                field.setLength(0)
            }
            c == '\r' -> Unit
            c == '\n' -> {
                row.add(field.toString())
                field.setLength(0)
                rows.add(row.toList())
                row.clear()
            }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) {
        row.add(field.toString())
        rows.add(row.toList())
    }
    return rows
}
