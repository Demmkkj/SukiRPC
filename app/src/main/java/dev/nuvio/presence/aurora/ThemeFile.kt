package dev.nuvio.presence.ui.themes

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.*
import java.util.zip.*
import java.nio.charset.StandardCharsets

internal val colorKeys = listOf("primary","secondary","accent","background","surface","text","muted","border","gradient","onPrimary")
internal val ranges = mapOf("opacity" to 0..100,"blur" to 0..32,"shadow" to 0..16,"stroke" to 0..6,"radius" to 0..48,
    "motion" to 0..100,"speed" to 1..100,"glow" to 0..100,"imageOpacity" to 0..100,"dim" to 0..100,"imageBlur" to 0..24,"scale" to 100..200)
internal val defaultNumbers = ranges.keys.zip(listOf(94,0,0,1,20,0,50,45,80,30,0,100)).toMap()
internal val choices = mapOf("font" to listOf("Sans","Serif","Mono"), "button" to listOf("Filled","Outlined"),
    "switch" to listOf("Switch","Checkbox"), "fit" to listOf("Crop","Fit","Fill"), "position" to listOf("Center","Top","Bottom"),
    "gradientStyle" to listOf("Radial","Diagonal","Solid"))
internal val defaultStyles = choices.mapValues { it.value.first() }
internal data class Skin(val name:String,val base:String,val colors:Map<String,String>,val numbers:Map<String,Int> = defaultNumbers,
    val styles:Map<String,String> = defaultStyles,val author:String="",val description:String="",val version:String="1")
internal data class ThemeBundle(val skin:Skin,val image:ByteArray?=null)
internal const val IMAGE_LIMIT=4*1024*1024
internal const val FILE_LIMIT=IMAGE_LIMIT+128*1024
internal fun bounded(input:InputStream,limit:Int):ByteArray {
    val out=ByteArrayOutputStream()
    val buf=ByteArray(8192)
    while(true){val n=input.read(buf);if(n<0)break;require(out.size()+n<=limit);out.write(buf,0,n)}
    return out.toByteArray()
}
internal object ThemeFile {
    private val gson=Gson()
    private const val manifest="theme.json"
    private const val art="background.jpg"
    fun encode(bundle:ThemeBundle):ByteArray {
        val s=bundle.skin
        val root=JsonObject().apply {
            addProperty("formatVersion",1);addProperty("minSukiRPC","1.2.0");addProperty("name",s.name)
            addProperty("base",s.base);addProperty("author",s.author);addProperty("description",s.description);addProperty("version",s.version)
            add("palette",gson.toJsonTree(s.colors));add("visual",gson.toJsonTree(s.numbers));add("styles",gson.toJsonTree(s.styles))
            if(bundle.image!=null)addProperty("background",art)
        }
        val json=root.toString().toByteArray()
        if(bundle.image==null)return json
        return ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip ->
            for((name,bytes) in listOf(manifest to json,art to bundle.image)){
                zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()
            }
        }}.toByteArray()
    }
    fun decode(bytes:ByteArray,bases:Set<String>):ThemeBundle {
        require(bytes.size in 1..FILE_LIMIT)
        val files=mutableMapOf<String,ByteArray>()
        val json=if(bytes.first()==0x50.toByte()){
            // Accept the ordinary two-entry ZIP container. Reject incomplete directories and ZIP64.
            val end=bytes.size-22
            fun u16(i:Int)=(bytes[i].toInt() and 255) or ((bytes[i+1].toInt() and 255) shl 8)
            require(end>=0 && u16(end)==0x4b50 && u16(end+2)==0x0605 && u16(end+4)==0 && u16(end+6)==0 &&
                u16(end+8)==2 && u16(end+10)==2 && u16(end+20)==0)
            ZipInputStream(bytes.inputStream()).use { zip ->
                while(true){val e=zip.nextEntry?:break
                    require(!e.isDirectory && e.name in setOf(manifest,art) && e.name !in files)
                    files[e.name]=bounded(zip,if(e.name==art)IMAGE_LIMIT else 16384)
                    zip.closeEntry()
                }
            }
            require(files.keys==setOf(manifest,art));files.getValue(manifest)
        }else bytes
        require(json.size<=16384)
        val reader=JsonReader(json.inputStream().reader(StandardCharsets.UTF_8)).apply { strictness=Strictness.STRICT }
        val o=JsonParser.parseReader(reader).asJsonObject
        require(reader.peek()==JsonToken.END_DOCUMENT)
        val fields=setOf("formatVersion","minSukiRPC","name","base","author","description","version","palette","visual","styles")
        require(o.keySet()==if(files.isEmpty()) fields else fields+"background")
        fun str(x:JsonElement,limit:Int):String {require(x.isJsonPrimitive && x.asJsonPrimitive.isString);return x.asString.also{require(it.length<=limit && it.none { ch->ch.code<32 })}}
        fun num(x:JsonElement):Int {require(x.isJsonPrimitive && x.asJsonPrimitive.isNumber);return x.asString.toInt()}
        require(num(o["formatVersion"])==1 && str(o["minSukiRPC"],16)=="1.2.0")
        fun obj(key:String,keys:Set<String>)=o.getAsJsonObject(key).also { require(it.keySet()==keys) }
        val colors=obj("palette",colorKeys.toSet()).entrySet().associate{it.key to str(it.value,7).also{v->require(v.matches(Regex("#[0-9a-fA-F]{6}")))}}
        val numbers=obj("visual",ranges.keys).entrySet().associate{it.key to num(it.value).also{v->require(v in ranges.getValue(it.key))}}
        val styles=obj("styles",choices.keys).entrySet().associate{it.key to str(it.value,32).also{v->require(v in choices.getValue(it.key))}}
        if(files.isNotEmpty())require(str(o["background"],20)==art)
        val skin=Skin(str(o["name"],64).also{require(it.isNotBlank())},str(o["base"],32).also{require(it in bases)},colors,numbers,styles,
            str(o["author"],64),str(o["description"],256),str(o["version"],32))
        return ThemeBundle(skin,files[art])
    }
}

internal fun allowGlass(ultra:Boolean,api:Int,intensity:Int)=ultra && api>=32 && intensity>0
