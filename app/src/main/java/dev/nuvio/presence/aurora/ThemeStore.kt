package dev.nuvio.presence.ui.themes

import java.io.*
import java.nio.file.*
import java.util.UUID

internal data class SavedSkin(val id:String,val skin:Skin,val date:Long,val imported:Boolean,val background:File?)
internal class ThemeStore(filesDir:File,private val bases:Set<String>,private val normalize:(ByteArray,Boolean)->ByteArray){
    private val root=File(filesDir,"aurora").apply { mkdirs() }

    fun list()=root.listFiles().orEmpty().filter{it.isDirectory && !it.name.startsWith(".")}.mapNotNull { dir ->
        runCatching { val skin=ThemeFile.decode(boundedFile(File(dir,"theme.json"),16384),bases).skin
            SavedSkin(dir.name,skin,dir.name.substringBefore('-').toLong(),dir.name.endsWith("-i"),File(dir,"background.jpg").takeIf{it.isFile}) }.getOrNull()
    }
    private fun boundedFile(file:File,limit:Int)=file.inputStream().use{bounded(it,limit)}
    fun bundle(item:SavedSkin)=ThemeBundle(item.skin,item.background?.let{boundedFile(it,IMAGE_LIMIT)})
    fun import(bytes:ByteArray):SavedSkin {val b=ThemeFile.decode(bytes,bases);return save(b.copy(image=b.image?.let{normalize(it,true)}),true)}
    fun save(b:ThemeBundle,imported:Boolean=false):SavedSkin {
        val json=ThemeFile.encode(b.copy(image=null));ThemeFile.decode(json,bases)
        val now=System.currentTimeMillis()
        val id="$now-${UUID.randomUUID()}"+if(imported)"-i" else ""
        val tmp=File(root,".tmp-"+id);check(tmp.mkdir())
        val dest=File(root,id)
        try{File(tmp,"theme.json").writeBytes(json);b.image?.let{File(tmp,"background.jpg").writeBytes(it)}
            Files.move(tmp.toPath(),dest.toPath(),StandardCopyOption.ATOMIC_MOVE)
        }finally{tmp.deleteRecursively()}
        return SavedSkin(id,b.skin,now,imported,File(dest,"background.jpg").takeIf{it.isFile})
    }
    fun remove(item:SavedSkin){check(File(root,item.id).deleteRecursively())}
}
