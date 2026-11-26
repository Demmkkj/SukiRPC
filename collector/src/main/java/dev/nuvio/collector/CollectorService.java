package dev.nuvio.collector;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import org.json.*;

public class CollectorService extends NotificationListenerService {
    private final Handler h=new Handler(Looper.getMainLooper());
    private LocalServer server;
    private final Runnable tick=new Runnable(){public void run(){collect();h.postDelayed(this,1000);}};
    @Override public void onListenerConnected(){h.removeCallbacks(tick);deleteFile("playback.json");if(server!=null)server.close();server=new LocalServer(this);server.start();h.post(tick);}
    @Override public void onListenerDisconnected(){shutdown();}
    private void shutdown(){h.removeCallbacks(tick);if(server!=null){server.close();server=null;}}
    @Override public void onDestroy(){shutdown();super.onDestroy();}
    private String get(MediaMetadata m,String k){String v=m.getString(k);return v==null?"":v;}
    private String first(String... v){for(String s:v)if(!s.isEmpty())return s;return "";}
    private static final java.util.regex.Pattern TMDB=java.util.regex.Pattern.compile("/t/p/(?:original|w[0-9]{2,4}|h[0-9]{2,4})/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)",java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern METAHUB=java.util.regex.Pattern.compile("/poster/(?:small|medium|large)/tt[0-9]{7,12}/img");
    private boolean publicArtwork(String raw){try{
        java.net.URI u=new java.net.URI(raw);
        if(!"https".equals(u.getScheme())||u.getHost()==null||u.getPort()!=-1||u.getRawUserInfo()!=null||u.getRawQuery()!=null||u.getRawFragment()!=null)return false;
        String host=u.getHost().toLowerCase(java.util.Locale.ROOT), path=u.getRawPath();
        return path!=null&&((host.equals("image.tmdb.org")&&TMDB.matcher(path).matches())||(host.equals("images.metahub.space")&&METAHUB.matcher(path).matches()));
    }catch(Exception ignored){return false;}}
    private String state(int s){switch(s){case 1:return "stopped";case 2:return "paused";case 3:return "playing";case 4:return "forwarding";case 5:return "rewinding";case 6:return "buffering";case 7:return "error";case 8:return "connecting";case 9:case 10:case 11:return "skipping";default:return "none";}}
    private void collect(){JSONObject j=new JSONObject();try{
        j.put("active",false);
        MediaSessionManager manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
        for(MediaController c:manager.getActiveSessions(new ComponentName(this,CollectorService.class))){
            if(!"com.nuvio.tv".equals(c.getPackageName()))continue;
            MediaMetadata m=c.getMetadata();PlaybackState p=c.getPlaybackState();long duration=-1;
            j.put("active",true).put("package","com.nuvio.tv");
            if(m!=null){
                String title=first(get(m,MediaMetadata.METADATA_KEY_TITLE),get(m,MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
                String artist=first(get(m,MediaMetadata.METADATA_KEY_ARTIST),get(m,MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
                String art=get(m,MediaMetadata.METADATA_KEY_ART_URI), album=get(m,MediaMetadata.METADATA_KEY_ALBUM_ART_URI), icon=get(m,MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
                String poster="";for(String uri:new String[]{art,album,icon})if(publicArtwork(uri)){poster=uri;break;}
                if(m.containsKey(MediaMetadata.METADATA_KEY_DURATION))duration=m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                j.put("title",title).put("artist",artist).put("description",artist.isEmpty()?title:title+", "+artist).put("media_id",get(m,MediaMetadata.METADATA_KEY_MEDIA_ID));
                j.put("artwork_url",poster).put("duration_ms",duration);
            }
            if(p!=null){long pos=p.getPosition();if(pos>=0&&p.getState()==PlaybackState.STATE_PLAYING&&p.getLastPositionUpdateTime()>0)pos+=(long)(Math.max(0,SystemClock.elapsedRealtime()-p.getLastPositionUpdateTime())*p.getPlaybackSpeed());if(duration>0&&pos>=0)pos=Math.min(pos,duration);j.put("state",state(p.getState())).put("position_ms",pos).put("speed",p.getPlaybackSpeed());}
            break;
        }
    }catch(Exception e){try{j=new JSONObject().put("active",false).put("error","media_session_unavailable");}catch(Exception ignored){}}
        try{j.put("updated_at_ms",System.currentTimeMillis());String data=j.toString();if(server!=null)server.update(data);
        }catch(Exception e){Log.w("NuvioCollector","Falha ao atualizar reprodução");}
    }
}
