package fr.fteychene.teaching.cloud.cadavreexquis

import java.net.InetAddress

val PORT = (System.getenv("PORT") ?: "8080").toInt()

fun main() {
    println(InetAddress.getLocalHost().hostName)
    InetAddress.getLocalHost().hostAddress
}