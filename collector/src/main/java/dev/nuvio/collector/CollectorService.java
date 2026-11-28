package dev.nuvio.collector;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.*;
import android.os.*;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import org.json.*;
import dev.nuvio.artwork.SessionArtwork;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CollectorService extends NotificationListenerService {
    private final Handler h=new Handler(Looper.getMainLooper());
    private LocalServer server;
    private volatile ArtworkFrame artworkFrame;

    private static final class ArtworkFrame {
        final List<String> identity;
        final String key;
        final MediaMetadata metadata;
        ArtworkFrame(List<String> identity, MediaMetadata metadata) {
            this.identity = identity;
            this.metadata = metadata;
            this.key = UUID.randomUUID().toString().replace("-", "");
        }
    }

    byte[] readArtwork(String key) {
        ArtworkFrame frame = artworkFrame;
        if (frame == null || !frame.key.equals(key)) return null;
        byte[] bytes = SessionArtwork.encode(SessionArtwork.bitmap(frame.metadata));
        return artworkFrame == frame ? bytes : null;
    }
    private final Runnable tick=new Runnable(){public void run(){collect();h.postDelayed(this,1000);}};
    @Override public void onListenerConnected(){h.removeCallbacks(tick);deleteFile("playback.json");if(server!=null)server.close();server=new LocalServer(this);server.start();h.post(tick);}
    @Override public void onListenerDisconnected(){shutdown();}
    private void shutdown(){artworkFrame=null;h.removeCallbacks(tick);if(server!=null){server.close();server=null;}}
    @Override public void onDestroy(){shutdown();super.onDestroy();}
    private String get(MediaMetadata m,String k){String v=m.getString(k);return v==null?"":v;}
    private String first(String... v){for(String s:v)if(!s.isEmpty())return s;return "";}
    private String state(int s){switch(s){case 1:return "stopped";case 2:return "paused";case 3:return "playing";case 4:return "forwarding";case 5:return "rewinding";case 6:return "buffering";case 7:return "error";case 8:return "connecting";case 9:case 10:case 11:return "skipping";default:return "none";}}
    private void collect(){JSONObject j=new JSONObject();try{
        j.put("active",false);
        MediaSessionManager manager=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
        java.util.List<String> allow=server==null?java.util.Collections.singletonList("com.nuvio.tv"):server.packages;
        List<MediaController> sessions = manager.getActiveSessions(new ComponentName(this, CollectorService.class));
        for (RankedSessions.Sample<MediaController, PlaybackState> sample : RankedSessions.read(
            sessions, allow, MediaController::getPackageName, MediaController::getPlaybackState,
            playback -> {
                int state = playback.getState();
                if (state == PlaybackState.STATE_STOPPED || state == PlaybackState.STATE_NONE) return -1;
                return state == PlaybackState.STATE_PLAYING ? 0 : 1;
            }
        )) {
            MediaMetadata m = sample.session.getMetadata();
            if (m == null) continue;
            PlaybackState p = sample.playback;
            long duration = -1;
            j.put("active", true).put("package", sample.app);
            String title=first(get(m,MediaMetadata.METADATA_KEY_TITLE),get(m,MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
            String artist=first(get(m,MediaMetadata.METADATA_KEY_ARTIST),get(m,MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
            String mediaId = get(m, MediaMetadata.METADATA_KEY_MEDIA_ID).trim();
            String app = sample.app;
            SessionArtwork.Candidate artwork = SessionArtwork.resolve(m, app, mediaId);
            if (artwork.url.isEmpty() && SessionArtwork.hasBitmap(m)) {
                List<String> identity = Arrays.asList(app, mediaId, title, artist);
                ArtworkFrame frame = artworkFrame;
                if (frame == null || !frame.identity.equals(identity)) {
                    frame = new ArtworkFrame(identity, m);
                    artworkFrame = frame;
                }
                j.put("artwork_key", frame.key);
            } else artworkFrame = null;
            j.put("artwork_source", artwork.source).put("artwork_rejected", artwork.rejected);
            if(m.containsKey(MediaMetadata.METADATA_KEY_DURATION))duration=m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            j.put("title",title).put("artist",artist).put("description",artist.isEmpty()?title:title+", "+artist).put("media_id",get(m,MediaMetadata.METADATA_KEY_MEDIA_ID));
            long year=m.getLong(MediaMetadata.METADATA_KEY_YEAR);
            if(year>=1000 && year<=9999)j.put("year",year);
            String genre=get(m,MediaMetadata.METADATA_KEY_GENRE);
            if(!genre.isEmpty())j.put("genre",genre);
            j.put("artwork_url",artwork.url).put("duration_ms",duration);
            long pos = p.getPosition();
            if (pos >= 0 && p.getState() == PlaybackState.STATE_PLAYING && p.getLastPositionUpdateTime() > 0) {
                pos += (long) (Math.max(0, SystemClock.elapsedRealtime() - p.getLastPositionUpdateTime()) * p.getPlaybackSpeed());
            }
            if (duration > 0 && pos >= 0) pos = Math.min(pos, duration);
            j.put("state", state(p.getState())).put("position_ms", pos).put("speed", p.getPlaybackSpeed());
            break;
        }
    }catch(Exception e){try{j=new JSONObject().put("active",false).put("error","media_session_unavailable");}catch(Exception ignored){}}
        if (!j.optBoolean("active")) artworkFrame = null;
        try{j.put("updated_at_ms",System.currentTimeMillis());String data=j.toString();if(server!=null)server.update(data);
        }catch(Exception e){Log.w("NuvioCollector","Falha ao atualizar reprodução");}
    }
}

