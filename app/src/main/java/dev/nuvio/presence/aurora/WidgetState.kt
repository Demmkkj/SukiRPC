package dev.nuvio.presence

internal data class WidgetSnapshot(val status:String,val device:String,val title:String,val details:String,val cover:String,val localCover:ByteArray?=null)
internal fun widgetSnapshot(running:Boolean,connection:String,discord:String,playback:Playback?,options:Options,format:MediaFormat,privacy:MediaPrivacy,now:Long):WidgetSnapshot{
    val device=connection.substringBefore(" •").takeIf{connection.contains(" •")} .orEmpty()
    val p=mediaPresence(playback,options,format,privacy,device,now,System.currentTimeMillis()).takeIf{running}
    val progress=if(p!=null && (p.start>0 || p.end>0)) {
        val pos=if(p.end>0)(playback!!.duration-p.anchorPosition) else p.anchorPosition
        "${pos.coerceAtLeast(0)/60000} min "+if(p.end>0)"restantes" else "decorridos"
    }else ""
    val local = playback?.localArtwork?.takeIf {
        p != null && playback.artworkPermitted && artworkAllowed(options, privacy, playback.title)
    }
    return WidgetSnapshot(if(Hidden.Disabled in privacy.hidden)"Privacidade ativa" else if(running)discord else "Presença desativada",
        if(running)connection else "Sem conexão",p?.title.orEmpty(),if(p==null)"Sem sessão disponível" else listOf(p.detail,
            when(p.state){"paused"->"Pausado";"playing"->"Reproduzindo";else->"Carregando"},progress).filter{it.isNotBlank()}.joinToString(" · "),p?.art.orEmpty(),local)
}
internal fun privacyAfterTap(p:MediaPrivacy)=p.copy(hidden=if(Hidden.Disabled in p.hidden)p.hidden-Hidden.Disabled else p.hidden+Hidden.Disabled)
