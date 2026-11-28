package dev.nuvio.presence

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import java.io.InputStream
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object RpcStore {
    const val MAX_BYTES=262144
    const val MAX_PRESETS=100
    private fun prefs(c:Context)=c.getSharedPreferences("rpc_studio",Context.MODE_PRIVATE)
    fun encode(p:RpcPreset)=JSONObject().apply {
        put("version",1);put("name",p.name);put("details",p.details);put("state",p.state);put("type",p.type)
        put("largeImage",p.largeImage);put("largeText",p.largeText);put("smallImage",p.smallImage);put("smallText",p.smallText)
        put("timer",p.timer);put("minutes",p.minutes);put("button1",p.button1);put("url1",p.url1);put("button2",p.button2);put("url2",p.url2);put("appPackage",p.appPackage)
    }.toString()
    fun decode(raw:String,validate:Boolean=true):RpcPreset {
        require(raw.length<=MAX_BYTES);val j=JSONObject(raw);require(j.optInt("version",1)==1)
        fun s(k:String,d:String="")=j.optString(k,d)
        val p=RpcPreset(s("name"),s("details"),s("state"),s("type","playing"),s("largeImage"),s("largeText"),s("smallImage"),s("smallText"),s("timer","elapsed"),j.optInt("minutes",60),s("button1"),s("url1"),s("button2"),s("url2"),s("appPackage"))
        if(validate)require(p.error()==null){p.error().orEmpty()};return p
    }
    fun catalog(raw:String):List<RpcPreset> {
        require(raw.length<=MAX_BYTES);val a=JSONArray(raw);require(a.length()<=MAX_PRESETS)
        return (0 until a.length()).map { decode(a.getJSONObject(it).toString()) }
    }
    fun load(c:Context)=runCatching { catalog(prefs(c).getString("saved","[]")?:"[]") }.getOrDefault(emptyList())
    fun save(c:Context, items:List<RpcPreset>) {
        require(items.size<=MAX_PRESETS);val raw=JSONArray().apply { items.forEach { put(JSONObject(encode(it))) } }.toString()
        require(raw.toByteArray().size<=MAX_BYTES);prefs(c).edit().putString("saved",raw).apply()
    }
    fun read(input:InputStream):String {
        val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=input.read(buffer);if(n<0)break;require(out.size()+n<=MAX_BYTES){"Arquivo muito grande"};out.write(buffer,0,n)}
        return out.toString(Charsets.UTF_8.name())
    }
    // Fixed public catalog; never sends user settings or local app names.
    const val COMMUNITY_URL="https://raw.githubusercontent.com/Demmkkj/SukiRPC/main/rpc-presets.json"
    suspend fun community():List<RpcPreset> = withContext(Dispatchers.IO) {
        val c=URI(COMMUNITY_URL).toURL().openConnection() as HttpsURLConnection
        c.instanceFollowRedirects=false;c.connectTimeout=8000;c.readTimeout=8000
        try { require(c.responseCode==200){"Catálogo ainda indisponível (${c.responseCode})"};c.inputStream.use { communityPresets(catalog(read(it))) } } finally { c.disconnect() }
    }
}
