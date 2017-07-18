package io.data2viz.format

import io.data2viz.request.request
import kotlin.js.Math

private val prefixes = listOf<String>("y", "z", "a", "f", "p", "n", "µ", "m", "", "k", "M", "G", "T", "P", "E", "Z", "Y")
private var prefixExponent = 0
private val validTypes = "efgrs%pbodxXc"                     // empty string is also a valid type

data class CoefficientExponent(val coefficient: String, val exponent: Int)

data class Locale(
        var decimalSeparator: String = ".",
        var grouping: List<Int> = listOf(3),
        var groupSeparator: String = ",",
        var currency: List<String> = listOf("$", ""),
        var numerals: List<String>? = null,
        var percent: String = "%")

fun locale(localeURL:String, callback:(Locale) -> Unit):Unit {
    request(localeURL).get { xhr ->
        val locale:Locale = JSON.parse(xhr.responseText)
        callback(locale)
    }
}

fun Locale.format(specifier: String): (Double) -> String {

    val specifier = FormatSpecifier(specifier)

    val fill = specifier.fill
    val align = specifier.align
    val sign = specifier.sign
    val symbol = specifier.symbol
    val zero = specifier.zero
    val width = specifier.width
    val groupSeparation = specifier.groupSeparation
    val type = specifier.type

    val groupFunction = formatGroup(grouping, groupSeparator)

    // Compute the prefix and suffix.
    // For SI-prefix, the suffix is lazily computed.
    val prefix = if (symbol == "$") currency[0] else if (symbol == "#" && isTypeIn(type, "boxX")) "0" + type.toLowerCase() else ""
    val suffix = if (symbol == "$") currency[1] else if (isTypeIn(type, "%p")) "%" else ""

    // What format function should we use?
    // Is this an integer type?
    // Can this type generate exponential notation?
    val formatType = formatTypes(type)
    val maybeSuffix = isTypeIn(type, "defgprs%")

    // Set the default exponent if not specified,
    // or clamp the specified exponent to the supported range.
    // For significant exponent, it must be in [1, 21].
    // For fixed exponent, it must be in [0, 20].
    val precision = if (specifier.precision == null) {
        if (type.isNotEmpty()) 6 else 12
    } else {
        if (isTypeIn(type, "gprs")) Math.max(1, Math.min(21, specifier.precision!!)) else Math.max(0, Math.min(20, specifier.precision!!))
    }

    fun format(value: Double): String {

        var returnValue: String

        var valuePrefix = prefix
        var valueSuffix = suffix

        if (type == "c") {
            valueSuffix = formatType(value, 0) + valueSuffix
            returnValue = ""
        } else {
            // value = +value              // typé

            // Perform the initial formatting.
            var valueNegative = value < 0
            returnValue = formatType(Math.abs(value), precision)

            // If a negative value rounds to zero during formatting, treat as positive.
            if (valueNegative && returnValue.toDouble() == 0.0) valueNegative = false

            // Compute the prefix and suffix.
            valuePrefix = (if (valueNegative) (if (sign == "(") sign else "-") else if (sign == "-" || sign == "(") "" else sign) + valuePrefix
            valueSuffix = valueSuffix + (if (type == "s") prefixes[8 + prefixExponent / 3] else "") + (if (valueNegative && sign == "(") ")" else "")

            // Break the formatted value into the integer “value” part that can be
            // grouped, and fractional or exponential “suffix” part that is not.
            if (maybeSuffix) {
                var i = -1
                val n = returnValue.length
                while (++i < n) {
                    val c = returnValue[i]
                    if (c !in '0'..'9') {
                        valueSuffix = (if (c == '.') decimalSeparator + returnValue.slice(i + 1 until returnValue.size) else returnValue.slice(i until returnValue.size)) + valueSuffix
                        returnValue = returnValue.slice(0 until i)
                        break
                    }
                }
            }
        }

        // If the fill character is not "0", grouping is applied before padding.
        if (groupSeparation && !zero) returnValue = groupFunction(returnValue, 9999999)

        // Compute the padding.
        val length = valuePrefix.length + returnValue.length + valueSuffix.length
        var padding = if (width != null && length < width) "".padStart(width - length, fill[0]) else ""

        // If the fill character is "0", grouping is applied after padding.
        if (groupSeparation && zero) {
            returnValue = groupFunction(padding + returnValue, if (padding.isNotEmpty()) width!! - valueSuffix.length else 9999999)
            padding = ""
        }

        // Reconstruct the final output based on the desired alignment.
        when (align) {
            "<" -> returnValue = valuePrefix + returnValue + valueSuffix + padding
            "=" -> returnValue = valuePrefix + padding + returnValue + valueSuffix
            "^" -> {
                val padLength = padding.length / 2 - 1
                returnValue = padding.slice(0 .. padLength) + valuePrefix + returnValue + valueSuffix + padding.slice(0 until padding.length - 1 - padLength)
            }
            else -> returnValue = padding + valuePrefix + returnValue + valueSuffix
        }


        return returnValue

        // TODO
        //return numerals(returnValue)
    }
    return ::format
}

fun Locale.formatPrefix(specifier:String, fixedPrefix:Double): (Double) -> String {
    val formatSpecifier = FormatSpecifier(specifier)
    formatSpecifier.type = "f"
    val f = format(formatSpecifier.toString())
    val e = Math.max(-8.0, Math.min(8.0, Math.floor(exponent(fixedPrefix).toDouble() / 3.0).toDouble())) * 3
    val k = Math.pow(10.0, -e)
    val prefix = prefixes[8 + (e / 3.0).toInt()]
    return fun(value:Double):String {
        return f(k * value) + prefix
    }
}

private fun exponent(value:Double): Int {
    val x = formatDecimal(Math.abs(value))
    return if (x != null) x.exponent else 0
}

private fun isTypeIn(type: String, types: String) = type.length == 1 && types.contains(type)
fun isValidType(type: String) = type.isEmpty() || isTypeIn(type, validTypes)

private fun Double.toFixed(digits: Int): String = this.asDynamic().toFixed(digits)
private fun Double.toExponential(digits: Int): String = this.asDynamic().toExponential(digits)
private fun Double.toExponential(): String = this.asDynamic().toExponential()
private fun Double.toPrecision(digits: Int): String = this.asDynamic().toPrecision(digits)
private fun Int.toStringDigits(digits: Int): String = this.asDynamic().toString(digits)

private fun formatGroup(group: List<Int>, groupSeparator: String): (String, Int) -> String {
    return fun(value: String, width: Int): String {
        var i = value.length
        val t = mutableListOf<String>()
        var j = 0
        var g = group[0]
        var length = 0

        while (i > 0 && g > 0) {
            if (length + g + 1 > width) g = Math.max(1, width - length)
            i -= g
            t.add(value.substring(i, i + g))
            length += g + 1
            if (length > width) break
            j = (j + 1) % group.size
            g = group[j]
        }

        t.reverse()
        return t.joinToString(separator = groupSeparator)
    }
}


fun formatTypes(type: String): (Double, Int) -> String {
    when (type) {
        "f" -> return fun(x: Double, p: Int): String { return x.toFixed(p) }
        "%" -> return fun(x: Double, p: Int): String { return (x * 100).toFixed(p) }
        "b" -> return fun(x: Double, _: Int): String { return Math.round(x).toStringDigits(2) }
        "c" -> return fun(x: Double, _: Int): String { return "$x" }
        "d" -> return fun(x: Double, _: Int): String { return Math.round(x).toStringDigits(10) }
        "e" -> return fun(x: Double, p: Int): String { return x.toExponential(p) }
        "g" -> return fun(x: Double, p: Int): String { return x.toPrecision(p) }
        "o" -> return fun(x: Double, _: Int): String { return Math.round(x).toStringDigits(8) }
        "p" -> return fun(x: Double, p: Int): String { return formatRounded(x * 100, p) }
        "r" -> return ::formatRounded
        "s" -> return ::formatPrefixAuto
        "X" -> return fun(x: Double, _: Int): String { return Math.round(x).toStringDigits(16).toUpperCase() }
        "x" -> return fun(x: Double, _: Int): String { return Math.round(x).toStringDigits(16) }
        else -> return ::formatDefault
    }
}

fun formatDefault(x: Double, p: Int): String {
    val newX = x.toPrecision(p)
    var i0 = -1
    var i1 = 0

    loop@ for (i in 1 until newX.length) {
        val c = newX[i]
        when (c) {
            '.' -> {
                i0 = i
                i1 = i
            }
            '0' -> {
                if (i0 == 0) i0 = i
                i1 = i
            }
            'e' -> break@loop
            else -> if (i0 > 0) i0 = 0
        }
    }

    return if (i0 > 0) newX.slice(0 until i0) + newX.slice(i1 + 1 until newX.size) else newX
}

fun formatRounded(x: Double, p: Int): String {
    val ce = formatDecimal(x, p) ?: return "$x"

    val ret = if (ce.exponent < 0) {
        "0.".padEnd(-ce.exponent + 1, '0') + ce.coefficient
    } else {
        if (ce.coefficient.length > ce.exponent + 1) {
            ce.coefficient.slice(0 .. ce.exponent) + "." +
                    ce.coefficient.slice(ce.exponent + 1 until ce.coefficient.size)
        } else {
            ce.coefficient + "".padStart(ce.exponent - ce.coefficient.length + 1, '0')
        }
    }
    return ret
}

/**
 * Computes the decimal coefficient and exponent of the specified number x with
 * significant digits p, where x is positive and p is in [1, 21] or undefined.
 * For example, formatDecimal(1.23) returns ["123", 0].
 */
fun formatDecimal(x: Double, p: Int = 0): CoefficientExponent? {
    var newX: String = x.toExponential()
    if (p != 0) {
        newX = x.toExponential(p - 1)
    }

    val index = newX.indexOf("e")
    if (index < 0) return null

    val coefficient = newX.slice(0 until index)

    // The string returned by toExponential either has the form \d\.\d+e[-+]\d+
    // (e.g., 1.2e+3) or the form \de[-+]\d+ (e.g., 1e+3).
    val decimal: String = if (coefficient.length > 1) coefficient[0] + coefficient.slice(2 until coefficient.size) else coefficient

    val parsePrecision = newX.slice(index + 1 until newX.size).toIntOrNull()
    val precision = if (parsePrecision != null) parsePrecision else 0

    return CoefficientExponent(decimal, precision)
}

private fun formatPrefixAuto(x: Double, p: Int = 0): String {
    val ce = formatDecimal(x, p) ?: return "$x"

    prefixExponent = Math.max(-8, Math.min(8, Math.floor(ce.exponent / 3.0))) * 3
    val i = ce.exponent - prefixExponent + 1
    val n = ce.coefficient.length

    return if (i == n) ce.coefficient
    else if (i > n) ce.coefficient.padEnd(i, '0')
    else if (i > 0) ce.coefficient.slice(0 until i) + "." + ce.coefficient.slice(i until ce.coefficient.size)
    else "0.".padEnd(2 - i, '0') + formatDecimal(x, Math.max(0, p + i - 1))!!.coefficient // less than 1y!
}

fun precisionFixed(step:Double): Int {
    return Math.max(0, -exponent(Math.abs(step)))
}

fun precisionPrefix(step:Double, value:Double): Int {
    return Math.max(0, Math.max(-8, Math.min(8, Math.floor(exponent(value) / 3.0))) * 3 - exponent(Math.abs(step)));
}

fun precisionRound(step:Double, max:Double): Int {
    val newStep = Math.abs(step)
    val newMax = Math.abs(max) - step;
    return Math.max(0, exponent(newMax) - exponent(newStep)) + 1;
}