package dev.nuvio.presence

import android.os.SystemClock
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

// Only public catalog metadata. No playback URLs, files, tokens or disk cache.
internal object MobilePosterSearch {
    data class Result(val url:String, val status:String)
    private data class Entry(val result:Result,val until:Long)
    private val cache=LinkedHashMap<String,Entry>(16,0.75f,true)
    private val worker=ThreadPoolExecutor(0,1,30L,TimeUnit.SECONDS,LinkedBlockingQueue<Runnable>())
    private var busy=false
    private var generation=0L
    private val idPattern=Regex("tt[0-9]{7,12}")
    private val marks=Regex("\\p{M}+")
    private val spaces=Regex("\\s+")
    private fun normalize(s:String)=Normalizer.normalize(s,Normalizer.Form.NFD)
        .replace(marks,"").lowercase(Locale.ROOT).trim().replace(spaces," ")

    @Synchronized fun reset(){ generation++;cache.clear() }

    @Synchronized fun lookup(title:String):Result {
        val clean=title.trim()
        if(clean.length !in 2..150 || clean.any{it.isISOControl()} || clean.contains("://"))
            return Result("","Mobile: título inválido para busca")
        val key=normalize(clean)
        val now=SystemClock.elapsedRealtime()
        cache[key]?.let{if(it.until>now)return it.result else cache.remove(key)}
        if(busy)return Result("","Mobile: aguardando busca de capa")
        busy=true
        val ticket=generation
        worker.execute {
            val result=try { search(clean) } catch(_:Exception) {
                Result("","Mobile: catálogo indisponível; nova tentativa em 5 min")
            }
            synchronized(this@MobilePosterSearch){
                if(ticket==generation){
                    cache[key]=Entry(result,SystemClock.elapsedRealtime()+if(result.url.isNotEmpty()) 3600000L else 300000L)
                    while(cache.size>48){val first=cache.entries.iterator();first.next();first.remove()}
                }
                busy=false
            }
        }
        return Result("","Mobile: buscando capa no catálogo")
    }

    private fun search(title:String):Result {
        val q=URLEncoder.encode(title,StandardCharsets.UTF_8.name()).replace("+","%20")
        val ids=linkedSetOf<String>()
        // Both categories must return successfully before accepting a unique match.
        for(type in listOf("movie","series")){
            val path="/catalog/$type/top/search=$q.json"
            val data=fetch(path).getJSONArray("metas")
            for(i in 0 until data.length()){
                val item=data.optJSONObject(i)?:continue
                if(normalize(item.optString("name"))!=normalize(title))continue
                val id=item.optString("id")
                if(idPattern.matches(id))ids.add(id)
            }
        }
        if(ids.size!=1)return Result("",if(ids.isEmpty())"Mobile: nenhuma correspondência exata" else "Mobile: título ambíguo; capa ignorada")
        val id=ids.first()
        val url=publicArtwork("https://images.metahub.space/poster/medium/$id/img")
        return Result(url,if(url.isNotEmpty())"Mobile: capa encontrada por título" else "Mobile: URL da capa rejeitada")
    }

    private fun fetch(path:String):JSONObject {
        var address=URL("https://v3-cinemeta.strem.io$path")
        repeat(3){
            val c=address.openConnection() as HttpsURLConnection
            try{
                c.instanceFollowRedirects=false
                c.connectTimeout=3500;c.readTimeout=3500
                c.setRequestProperty("Accept","application/json")
                val code=c.responseCode
                if(code in 300..399){
                    val next=URL(address,c.getHeaderField("Location")?:error("redirect"))
                    require(next.protocol=="https" && next.host in setOf("v3-cinemeta.strem.io","cinemeta-live.strem.io") && next.port==-1 && next.userInfo==null && next.path==address.path && next.query==null && next.ref==null)
                    address=next
                }else{
                    require(code==200)
                    val bytes=c.inputStream.use{input->
                        val out=java.io.ByteArrayOutputStream();val buf=ByteArray(8192)
                        while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=1048576);out.write(buf,0,n)}
                        out.toByteArray()
                    }
                    return JSONObject(String(bytes,StandardCharsets.UTF_8))
                }
            }finally{c.disconnect()}
        }
        error("redirect limit")
    }
}
