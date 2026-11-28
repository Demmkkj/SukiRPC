package dev.nuvio.artwork;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Public, static image routes only. Used at collection, protocol ingress and publication. */
public final class ArtworkPolicy {
    private static final Pattern TMDB = Pattern.compile("/t/p/(?:original|[wh][0-9]{2,4})/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)", Pattern.CASE_INSENSITIVE);
    private static final Pattern METAHUB = Pattern.compile("/poster/(?:small|medium|large)/tt[0-9]{7,12}/img");
    private static final Pattern SPOTIFY = Pattern.compile("/image/[0-9a-f]{40}", Pattern.CASE_INSENSITIVE);
    private static final String VIDEO_ID = "[A-Za-z0-9_-]{11}";
    private static final Pattern YOUTUBE = Pattern.compile("/(?:vi|vi_webp)/" + VIDEO_ID + "/(?:default|mqdefault|hqdefault|sddefault|maxresdefault|[0-3])\\.(?:jpg|webp)");
    private static final Pattern YTM = Pattern.compile("/[A-Za-z0-9_-]{20,256}(?:=w[0-9]{2,4}-h[0-9]{2,4}(?:-[A-Za-z0-9]{1,12}){0,8})?");
    private static final Pattern IMDB = Pattern.compile("(?:(?:imdb|movie|series):)?(tt[0-9]{7,12})(?::[0-9]{1,5}:[0-9]{1,5})?");
    private static final Pattern EXPLICIT_IMDB = Pattern.compile("imdb:(tt[0-9]{7,12})");
    private static final Pattern YOUTUBE_ID = Pattern.compile("(?:youtube:|yt_id:)?(" + VIDEO_ID + ")");
    private static final Pattern BARE_VIDEO_ID = Pattern.compile(VIDEO_ID);
    private static final Pattern VIDEO_SEGMENT = Pattern.compile("/(?:shorts|live|embed)/(" + VIDEO_ID + ")");
    private static final Pattern SHORT_LINK = Pattern.compile("/(" + VIDEO_ID + ")");
    private static final Pattern SPOTIFY_IMAGE = Pattern.compile("spotify:image:([0-9a-f]{40})");
    public static final int MAX_LOCAL_BYTES = 24 * 1024;
    public static final int MAX_LOCAL_SIDE = 256;

    private enum Provider { GENERIC, CINEMA, SPOTIFY, YOUTUBE, YOUTUBE_MUSIC, EXPLICIT_IMDB }

    private static final Map<String, Provider> PROVIDERS = providers();

    private static Map<String, Provider> providers() {
        Map<String, Provider> providers = new HashMap<>();
        add(providers, Provider.CINEMA, "com.nuvio.app", "com.nuviodebug.com", "com.nuvio.tv", "com.stremio.one");
        add(providers, Provider.SPOTIFY, "com.spotify.music", "com.spotify.tv.android");
        add(providers, Provider.YOUTUBE, "com.google.android.youtube", "com.google.android.youtube.tv");
        add(providers, Provider.YOUTUBE_MUSIC, "com.google.android.apps.youtube.music");
        add(providers, Provider.EXPLICIT_IMDB, "org.jellyfin.mobile", "org.jellyfin.androidtv", "com.plexapp.android", "org.xbmc.kodi");
        return Collections.unmodifiableMap(providers);
    }

    private static void add(Map<String, Provider> providers, Provider provider, String... apps) {
        for (String app : apps) providers.put(app, provider);
    }

    private static Provider provider(String source) {
        Provider provider = PROVIDERS.get(source);
        return provider == null ? Provider.GENERIC : provider;
    }

    private ArtworkPolicy() {}

    public static String publicUrl(String value, String source) {
        if (value == null || value.isEmpty() || value.length() > 768) return "";
        try {
            URI url = new URI(value);
            if (!"https".equals(url.getScheme()) || url.getHost() == null || url.getPort() != -1 ||
                !url.getHost().equalsIgnoreCase(url.getRawAuthority()) ||
                url.getRawQuery() != null || url.getRawFragment() != null) return "";
            String host = url.getHost().toLowerCase(Locale.ROOT);
            String path = url.getRawPath();
            boolean accepted;
            switch (host) {
                case "image.tmdb.org": accepted = TMDB.matcher(path).matches(); break;
                case "images.metahub.space": accepted = METAHUB.matcher(path).matches(); break;
                case "i.scdn.co": accepted = SPOTIFY.matcher(path).matches(); break;
                case "i.ytimg.com":
                case "img.youtube.com":
                    if (YOUTUBE.matcher(path).matches()) return "https://i.ytimg.com" + path;
                    return "";
                case "lh3.googleusercontent.com":
                    accepted = (provider(source) == Provider.YOUTUBE_MUSIC || "history".equals(source))
                        && YTM.matcher(path).matches();
                    break;
                default: accepted = false;
            }
            return accepted ? value : "";
        } catch (URISyntaxException ignored) {
            return "";
        }
    }

    public static String fromArtworkMetadata(String value, String source) {
        if (value == null || value.length() > 768) return "";
        if (provider(source) == Provider.SPOTIFY) {
            Matcher match = SPOTIFY_IMAGE.matcher(value);
            if (match.matches()) return "https://i.scdn.co/image/" + match.group(1);
        }
        return "";
    }

    public static String fromMediaId(String value, String source) {
        if (value == null || value.length() > 160) return "";
        switch (provider(source)) {
            case CINEMA:
                Matcher imdb = IMDB.matcher(value);
                return imdb.matches() ? "https://images.metahub.space/poster/medium/" + imdb.group(1) + "/img" : "";
            case YOUTUBE:
            case YOUTUBE_MUSIC:
                Matcher video = YOUTUBE_ID.matcher(value);
                return video.matches() ? videoThumbnail(video.group(1)) : thumbnailFromLink(value);
            case EXPLICIT_IMDB:
                Matcher providerId = EXPLICIT_IMDB.matcher(value);
                return providerId.matches() ? "https://images.metahub.space/poster/medium/" + providerId.group(1) + "/img" : "";
            default: return "";
        }
    }

    public static boolean isYoutubeSource(String source) {
        Provider p = provider(source);
        return p == Provider.YOUTUBE || p == Provider.YOUTUBE_MUSIC;
    }

    private static boolean youtubePage(String host) {
        return "youtube.com".equals(host) || "www.youtube.com".equals(host) ||
            "m.youtube.com".equals(host) || "music.youtube.com".equals(host);
    }

    private static String videoThumbnail(String id) {
        return "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
    }

    /** Extract only the video ID. Tracking, playlist and playback parameters stay on device. */
    public static String thumbnailFromLink(String value) {
        if (value == null || value.length() > 768) return "";
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null || uri.getPort() != -1 ||
                !host.equalsIgnoreCase(uri.getRawAuthority())) return "";
            host = host.toLowerCase(Locale.ROOT);
            String path = uri.getRawPath();
            String query = uri.getRawQuery();
            if (query != null) {
                for (String field : query.split("&", -1)) {
                    String name = field.split("=", 2)[0];
                    if (!name.matches("v|si|t|list|index|start|pp|feature|ab_channel")) return "";
                }
            }
            if ("youtu.be".equals(host)) {
                Matcher item = SHORT_LINK.matcher(path);
                return item.matches() ? videoThumbnail(item.group(1)) : "";
            }
            if (!youtubePage(host)) return "";
            Matcher segment = VIDEO_SEGMENT.matcher(path);
            if (segment.matches()) return videoThumbnail(segment.group(1));
            if (!path.equals("/watch") || query == null) return "";
            String id = null;
            for (String field : query.split("&", -1)) {
                if (!field.startsWith("v=")) continue;
                if (id != null) return "";
                id = field.substring(2);
            }
            return id != null && BARE_VIDEO_ID.matcher(id).matches() ? videoThumbnail(id) : "";
        } catch (URISyntaxException ignored) {
            return "";
        }
    }

    public static boolean managedArtworkHost(String host) {
        return youtubeArtworkOrPageHost(host) || "image.tmdb.org".equals(host) || "images.metahub.space".equals(host) ||
            "i.scdn.co".equals(host) || "lh3.googleusercontent.com".equals(host);
    }

    private static boolean youtubeArtworkOrPageHost(String host) {
        return youtubePage(host) || "youtu.be".equals(host) || "img.youtube.com".equals(host) ||
            "i.ytimg.com".equals(host);
    }

    public static boolean publicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        int first = bytes[0] & 255;
        int second = bytes[1] & 255;
        if (bytes.length == 4) {
            // Reject this-network, CGNAT, benchmark, documentation and reserved ranges.
            return first != 0 && first < 224 && !(first == 100 && second >= 64 && second <= 127) &&
                !(first == 198 && (second == 18 || second == 19)) &&
                !(first == 192 && second == 0 && ((bytes[2] & 255) == 0 || (bytes[2] & 255) == 2)) &&
                !(first == 198 && second == 51 && (bytes[2] & 255) == 100) &&
                !(first == 203 && second == 0 && (bytes[2] & 255) == 113);
        }
        // IPv6 global unicast only. Exclude the documentation prefix.
        return bytes.length == 16 && (first & 0xe0) == 0x20 &&
            !(first == 0x20 && second == 1 && (bytes[2] & 255) == 0x0d && (bytes[3] & 255) == 0xb8);
    }

    public static boolean validLocalKey(String key) {
        return key != null && key.matches("[a-f0-9]{32}");
    }
}
