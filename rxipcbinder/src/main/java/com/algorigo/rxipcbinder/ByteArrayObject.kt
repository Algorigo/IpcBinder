package com.algorigo.rxipcbinder

abstract class ByteArrayObject {

    class NotParcelableObjectException: Throwable()

    abstract fun toByteArray(): Pair<String, ByteArray>

    companion object {
        fun createFrom(name: String, byteArray: ByteArray): ByteArrayObject {
            val instance = Class.forName(name).getConstructor(ByteArray::class.java)
                .newInstance(byteArray)
            if (instance is ByteArrayObject) {
                return instance
            }
            throw NotParcelableObjectException()
        }
    }
}
