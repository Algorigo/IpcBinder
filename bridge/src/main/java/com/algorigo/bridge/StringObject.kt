package com.algorigo.bridge

import com.algorigo.rxipcbinder.ByteArrayObject

class StringObject() : ByteArrayObject() {

    private lateinit var string: String

    constructor(string: String): this() {
        this.string = string
    }

    constructor(byteArray: ByteArray): this() {
        string = byteArray.toString(Charsets.UTF_8)
    }

    override fun toByteArray(): ByteArray {
        return string.toByteArray(Charsets.UTF_8)
    }

    override fun toString(): String {
        return string
    }
}