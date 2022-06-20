package com.algorigo.bridge

import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

fun Pair<Long, TimeUnit>.toByteArray(): ByteArray {
    val periodBytes = first.toByteArray()
    val intervalTimeUnit = second.toByteArray()
    return periodBytes + intervalTimeUnit
}

fun ByteArray.toTimeWithUnit(): Pair<Long, TimeUnit> {
    val first = this.copyOfRange(0, 8).toLong()
    val second = this[8].toTimeUnit()
    return Pair(first, second)
}

fun Long.toByteArray(): ByteArray {
    return ByteBuffer.allocate(8).putLong(this).array()
}

fun TimeUnit.toByteArray(): ByteArray {
    return byteArrayOf((0xff and this.ordinal).toByte())
}

fun ByteArray.toLong(): Long {
    val buffer = ByteBuffer.wrap(this)
    return buffer.long
}

fun Byte.toTimeUnit(): TimeUnit {
    return TimeUnit.values()[this.toInt()]
}
