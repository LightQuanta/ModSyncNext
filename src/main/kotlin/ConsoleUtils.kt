package tech.lq0.modSyncNext

import kotlin.system.exitProcess

fun String.black() = "\u001B[30m$this\u001B[0m"
fun String.red() = "\u001B[31m$this\u001B[0m"
fun String.green() = "\u001B[32m$this\u001B[0m"
fun String.yellow() = "\u001B[33m$this\u001B[0m"
fun String.blue() = "\u001B[34m$this\u001B[0m"
fun String.magenta() = "\u001B[35m$this\u001B[0m"
fun String.cyan() = "\u001B[36m$this\u001B[0m"
fun String.white() = "\u001B[37m$this\u001B[0m"

fun String.brightBlack() = "\u001B[90m$this\u001B[0m"
fun String.brightRed() = "\u001B[91m$this\u001B[0m"
fun String.brightGreen() = "\u001B[92m$this\u001B[0m"
fun String.brightYellow() = "\u001B[93m$this\u001B[0m"
fun String.brightBlue() = "\u001B[94m$this\u001B[0m"
fun String.brightMagenta() = "\u001B[95m$this\u001B[0m"
fun String.brightCyan() = "\u001B[96m$this\u001B[0m"
fun String.brightWhite() = "\u001B[97m$this\u001B[0m"

fun String.bold() = "\u001B[1m$this\u001B[0m"
fun String.underline() = "\u001B[4m$this\u001B[0m"

fun exitWithHint(text: String): Nothing {
    println(text)
    println("按回车退出")
    readln()
    exitProcess(0)
}