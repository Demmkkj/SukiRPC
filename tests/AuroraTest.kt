package dev.nuvio.presence

import dev.nuvio.presence.ui.themes.*
import java.io.*
import java.nio.file.Files
import java.util.zip.*

fun main(){
    val bases=setOf("OBSIDIAN")
    val s=Skin("Night","OBSIDIAN",colorKeys.associateWith{"#010203"})
    val json=ThemeFile.encode(ThemeBundle(s))
    check(ThemeFile.decode(json,bases).skin==s)
    val image=byteArrayOf(1,2,3)
    val zip=ThemeFile.encode(ThemeBundle(s,image))
    check(ThemeFile.decode(zip,bases).image!!.contentEquals(image))
    fun reject(bytes:ByteArray){check(runCatching{ThemeFile.decode(bytes,bases)}.isFailure)}
    reject(zip.copyOf(zip.size-20));reject("{}".toByteArray());reject(json.toString(Charsets.UTF_8).replace("\"formatVersion\":1","\"formatVersion\":2").toByteArray())
    reject(ThemeFile.encode(ThemeBundle(s.copy(numbers=s.numbers+("blur" to 9000)))))
    reject(ThemeFile.encode(ThemeBundle(s.copy(colors=s.colors+("text" to "invalid")))))
    fun archive(name:String,data:ByteArray)=ByteArrayOutputStream().also{out->ZipOutputStream(out).use{z->
        for((n,b) in listOf("theme.json" to json,name to data)){z.putNextEntry(ZipEntry(n));z.write(b);z.closeEntry()}
    }}.toByteArray()
    reject(archive("../other",image));reject(archive("background.jpg",ByteArray(IMAGE_LIMIT+1)))
    val root=Files.createTempDirectory("suki").toFile()
    try{
        fun store()=ThemeStore(root,bases){_,_->error("unsupported image")}
        val first=store().import(json);check(store().list().single()==first)
        check(runCatching{store().import(zip)}.isFailure)
        check(store().list().single()==first);store().remove(first);check(store().list().isEmpty())
    }finally{root.deleteRecursively()}
    for(api in listOf(26,31,32,35))for(ultra in listOf(false,true)){
        check(allowGlass(ultra,api,24)==(api>=32 && ultra));check(!allowGlass(ultra,api,0))
    }
    val playback=Playback("Private film","S2:E4","id","poster","playing",100,10000,1.0,10)
    val privacy=MediaPrivacy(setOf(Hidden.Title,Hidden.Progress))
    val w=widgetSnapshot(true,"Nuvio TV","Conectado",playback,Options(),MediaFormat(),privacy,10)
    check(w.cover.isEmpty() && w.title.isEmpty() && !w.details.contains("E4") && !w.details.contains("min"))
    check(privacyAfterTap(privacyAfterTap(privacy))==privacy)
    check(widgetSnapshot(true,"Nuvio TV","SDK",playback,Options(),MediaFormat(),MediaPrivacy(excluded=setOf(playback.title)),10).title.isEmpty())
    println("PASS: Aurora JSON, ZIP, limits, persistence, graphics, private widgets and tile state")
}
