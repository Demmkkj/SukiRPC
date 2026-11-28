package dev.nuvio.presence

import org.json.JSONObject
import android.util.Base64
import dev.nuvio.artwork.ArtworkPolicy
import dev.nuvio.artwork.SessionArtwork
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


internal object LanClient {
    private const val PORT=8766
    private fun hex(b:ByteArray)=b.joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun localAddress(ip:String):InetAddress {
        require(ip.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) && ip.split('.').all{it.toInt() in 0..255}) { "Informe um IPv4 válido" }
        return InetAddress.getByName(ip).also{require(it.isSiteLocalAddress){"Use o IP privado da Fire TV"}}
    }
    private fun request(ip:String,pin:String,req:JSONObject):Pair<JSONObject,String> {
        val address=localAddress(ip)
        require(pin.matches(Regex("[0-9a-f]{16}|[0-9a-f]{64}"))) { "Identidade inválida" }
        var actual=""
        val tm=object:X509TrustManager {
            override fun getAcceptedIssuers():Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain:Array<X509Certificate>,authType:String){throw CertificateException("client")}
            override fun checkServerTrusted(chain:Array<X509Certificate>,authType:String){
                val cert=chain.firstOrNull()?:throw CertificateException("certificado ausente")
                cert.checkValidity()
                val fingerprint=hex(MessageDigest.getInstance("SHA-256").digest(cert.encoded))
                if(!acceptsPin(pin,fingerprint)) throw CertificateException("Identidade da Fire TV divergente")
                actual=fingerprint
            }
        }
        val ssl=SSLContext.getInstance("TLS").apply{init(null,arrayOf<TrustManager>(tm),SecureRandom())}
        Socket().use { raw ->
            raw.connect(InetSocketAddress(address,PORT),2500);raw.soTimeout=3500
            (ssl.socketFactory.createSocket(raw,ip,PORT,true) as SSLSocket).use { s ->
                s.enabledProtocols=arrayOf("TLSv1.2");s.soTimeout=3500;s.startHandshake()
                check(actual.isNotEmpty()){ "Identidade não verificada" }
                s.outputStream.write((req.toString()+"\n").toByteArray(Charsets.UTF_8));s.outputStream.flush()
                val out=ByteArrayOutputStream();var done=false
                val input=s.inputStream.buffered()
                for(i in 0 until 65536){val b=input.read();if(b<0)break;if(b==10){done=true;break};out.write(b)}
                check(done){"Resposta inválida"}
                val j=JSONObject(String(out.toByteArray(),Charsets.UTF_8))
                check(j.optBoolean("ok")){when(j.optString("error")){"unauthorized"->"Pareamento revogado";"pair_expired"->"Código expirado ou bloqueado";"pair_invalid"->"Código incorreto";"stale"->"Coletor sem dados recentes";else->"Coletor indisponível"}}
                return j to actual
            }
        }
    }
    fun pair(ip:String,identity:String,code:String):PairedDevice {
        val pin=identity.trim().lowercase().replace(" ","").replace(":","")
        require(code.matches(Regex("[0-9]{8}"))){"O código possui 8 dígitos"}
        val (j,full)=request(ip.trim(),pin,JSONObject().put("op","pair").put("code",code))
        val token=j.getString("token");check(token.matches(Regex("[0-9a-f]{64}"))){"Credencial inválida"}
        return PairedDevice(ip.trim(),full,token)
    }
    fun playback(d: PairedDevice, packages: List<String>, artwork: Boolean = true): JSONObject =
        request(d.ip, d.pin, JSONObject().put("op", "playback").put("packages", org.json.JSONArray(packages))
            .put("artwork", artwork).put("token", d.token)).first

    fun artwork(d: PairedDevice, key: String): ByteArray? {
        if (!ArtworkPolicy.validLocalKey(key)) return null
        val response = request(d.ip, d.pin, JSONObject().put("op", "artwork").put("key", key).put("token", d.token)).first
        val encoded = response.optString("jpeg")
        if (response.optString("key") != key || encoded.isEmpty() ||
            encoded.length > ((ArtworkPolicy.MAX_LOCAL_BYTES + 2) / 3) * 4) return null
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        return bytes.takeIf(SessionArtwork::validEncoded)
    }
}
