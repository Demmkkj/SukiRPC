package dev.nuvio.presence

import dev.nuvio.artwork.ArtworkPolicy
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

// Select only public DNS answers before OkHttp opens a socket.
internal object ArtworkDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> =
        Dns.SYSTEM.lookup(hostname).filter(ArtworkPolicy::publicAddress)
            .takeIf { it.isNotEmpty() } ?: throw UnknownHostException("Nenhum endereço público para a capa")
}
