package dev.nuvio.presence.ui.themes

import android.graphics.*
import java.io.ByteArrayOutputStream
internal fun decodePhoto(bytes:ByteArray,jpegOnly:Boolean=false):ByteArray {
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outMimeType in if(jpegOnly)setOf("image/jpeg") else setOf("image/jpeg","image/png","image/webp"))
        require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192 && bounds.outWidth.toLong()*bounds.outHeight<=24000000)
        val opts=BitmapFactory.Options();opts.inSampleSize=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/opts.inSampleSize>1536)opts.inSampleSize*=2
        val image=requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts))
        return try{ByteArrayOutputStream().use{require(image.compress(Bitmap.CompressFormat.JPEG,86,it));it.toByteArray().also{v->require(v.size<=IMAGE_LIMIT)}}}finally{image.recycle()}
    }

internal fun ThemeStore(context:android.content.Context)=ThemeStore(context.filesDir,ThemeId.entries.map{it.name}.toSet(),::decodePhoto)
