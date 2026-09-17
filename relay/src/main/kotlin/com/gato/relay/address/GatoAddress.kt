package com.gato.relay.address

import java.net.InetSocketAddress

data class GatoAddress(val hostName: String, val port: Int)

inline val GatoAddress.inetSocketAddress
    get() = InetSocketAddress(hostName, port)

inline val InetSocketAddress.gatoAddress
    get() = GatoAddress(hostName, port)