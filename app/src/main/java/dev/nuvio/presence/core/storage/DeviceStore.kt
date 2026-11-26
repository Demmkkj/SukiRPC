package dev.nuvio.presence

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

internal data class PairedDevice(val ip: String, val pin: String, val token: String)
internal object DeviceStore {
    private const val ALIAS = "nuvio-device-secret-v1"
    // Guarded by the same monitor as save/load/clear. Cache only while encrypted prefs match.
    private var cachedRecord:String?=null
    private var cachedDevice:PairedDevice?=null
    private fun key(): SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }
    @Synchronized fun save(c:Context,d:PairedDevice) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted=cipher.doFinal(JSONObject().put("ip",d.ip).put("pin",d.pin).put("token",d.token).toString().toByteArray(Charsets.UTF_8))
        val b64=Base64.encodeToString(cipher.iv+encrypted,Base64.NO_WRAP)
        check(c.getSharedPreferences("device",0).edit().putString("pairing",b64).commit()) { "Falha ao salvar pareamento" }
        cachedRecord=b64;cachedDevice=d
    }
    @Synchronized fun load(c:Context):PairedDevice? = try {
        val stored=c.getSharedPreferences("device",0).getString("pairing",null)
        if(stored==null){cachedRecord=null;cachedDevice=null;null}
        else if(stored==cachedRecord&&cachedDevice!=null)cachedDevice
        else {
            cachedRecord=null;cachedDevice=null
            val raw=Base64.decode(stored,Base64.NO_WRAP)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,raw.copyOfRange(0,12))) }
            val j=JSONObject(String(cipher.doFinal(raw.copyOfRange(12,raw.size)),Charsets.UTF_8))
            PairedDevice(j.getString("ip"),j.getString("pin"),j.getString("token")).also {
                cachedRecord=stored;cachedDevice=it
            }
        }
    } catch(_:Exception){ cachedRecord=null;cachedDevice=null;null }
    @Synchronized fun clear(c:Context) { cachedRecord=null;cachedDevice=null;c.getSharedPreferences("device",0).edit().remove("pairing").commit() }
}
