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
        write(c,d,true)
    }
    private fun write(c:Context,d:PairedDevice,activate:Boolean){
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE,key()) }
        val encrypted=cipher.doFinal(JSONObject().put("ip",d.ip).put("pin",d.pin).put("token",d.token).toString().toByteArray(Charsets.UTF_8))
        val b64=Base64.encodeToString(cipher.iv+encrypted,Base64.NO_WRAP)
        val prefs=c.getSharedPreferences("device",0)
        migrate(c)
        val edit=prefs.edit().putString("pair."+d.pin,b64)
        if(activate)edit.putString("active",d.pin)
        check(edit.commit())
        cachedRecord=b64;cachedDevice=d
    }
    private fun prefs(c:Context)=c.getSharedPreferences("device",0)
    private fun migrate(c:Context){
        val prefs=prefs(c)
        val legacy=prefs.getString("pairing",null)?:return
        val d=decrypt(legacy)?:error("Não foi possível abrir o pareamento antigo")
        val edit=prefs.edit()
        migratedPair(d.pin,legacy).forEach{(key,value)->edit.putString(key,value)}
        check(edit.remove("pairing").commit())
    }
    @Synchronized fun ids(c:Context):List<String>{migrate(c);return prefs(c).all.keys.filter{it.startsWith("pair.")}.map{it.removePrefix("pair.")}.sorted()}
    @Synchronized fun select(c:Context,id:String){require(prefs(c).contains("pair."+id));prefs(c).edit().putString("active",id).apply()}
    @Synchronized fun get(c:Context,id:String):PairedDevice? { migrate(c);return decrypt(prefs(c).getString("pair."+id,null)) }
    @Synchronized fun load(c:Context):PairedDevice? {
        val prefs=prefs(c)
        return decrypt(prefs.getString("pairing",null)?:prefs.getString("pair."+prefs.getString("active",""),null))
    }
    @Synchronized fun relocate(c:Context,old:PairedDevice,new:PairedDevice){
        val stored=get(c,old.pin)
        if(mayRelocate(old.pin,new.pin,old.ip,stored?.ip) && stored==old)write(c,new,false)
    }
    private fun decrypt(stored:String?):PairedDevice? = try {
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
    @Synchronized fun remove(c:Context,id:String){
        val rest=ids(c)-id
        val prefs=prefs(c);val edit=prefs.edit().remove("pair."+id)
        if(prefs.getString("active",null)==id)edit.putString("active",rest.firstOrNull())
        check(edit.commit());cachedRecord=null;cachedDevice=null
    }
    @Synchronized fun clear(c:Context){load(c)?.let{remove(c,it.pin)}}
}
