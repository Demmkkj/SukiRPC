package dev.nuvio.artwork;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import java.io.ByteArrayOutputStream;

public final class SessionArtwork {
    private static final String[] URI_KEYS = {
        MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
    };
    private static final String[] BITMAP_KEYS = {
        MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON
    };

    private SessionArtwork() {}

    public static final class Candidate {
        public final String url;
        public final String source;
        public final boolean rejected;
        Candidate(String url, String source, boolean rejected) {
            this.url = url;
            this.source = source;
            this.rejected = rejected;
        }
    }

    public static Candidate resolve(MediaMetadata metadata, String app, String mediaId) {
        boolean rejected = false;
        String integration = "";
        for (String key : URI_KEYS) {
            String value = metadata.getString(key);
            if (value == null || value.isEmpty()) continue;
            String url = ArtworkPolicy.publicUrl(value, app);
            if (!url.isEmpty()) return new Candidate(url, "session", rejected);
            String derived = ArtworkPolicy.fromArtworkMetadata(value, app);
            if (derived.isEmpty() && ArtworkPolicy.isYoutubeSource(app)) {
                derived = ArtworkPolicy.thumbnailFromLink(value);
            }
            if (integration.isEmpty()) integration = derived;
            if (derived.isEmpty()) rejected = true;
        }
        if (!integration.isEmpty()) return new Candidate(integration, "metadata", rejected);
        String identifier = ArtworkPolicy.fromMediaId(mediaId, app);
        if (identifier.isEmpty()) {
            String mediaUri = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI);
            String fromUri = ArtworkPolicy.isYoutubeSource(app)
                ? ArtworkPolicy.thumbnailFromLink(mediaUri) : ArtworkPolicy.fromMediaId(mediaUri, app);
            if (!fromUri.isEmpty()) return new Candidate(fromUri, "media_uri", rejected);
        }
        return new Candidate(identifier, identifier.isEmpty() ? "" : "identifier", rejected);
    }

    public static boolean hasBitmap(MediaMetadata metadata) {
        for (String key : BITMAP_KEYS) if (metadata.containsKey(key)) return true;
        return false;
    }

    public static Bitmap bitmap(MediaMetadata metadata) {
        for (String key : BITMAP_KEYS) {
            Bitmap image = metadata.getBitmap(key);
            if (image != null && !image.isRecycled() && image.getWidth() > 0 && image.getHeight() > 0 &&
                (long) image.getWidth() * image.getHeight() <= 4_194_304L) return image;
        }
        return null;
    }

    public static byte[] encode(Bitmap original) {
        if (original == null || original.isRecycled()) return null;
        try {
            for (int side : new int[]{ArtworkPolicy.MAX_LOCAL_SIDE, 160, 96}) {
                float scale = Math.min(1f, (float) side / Math.max(original.getWidth(), original.getHeight()));
                Bitmap resized = scale == 1f ? original : Bitmap.createScaledBitmap(original,
                    Math.max(1, Math.round(original.getWidth() * scale)), Math.max(1, Math.round(original.getHeight() * scale)), true);
                try {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    if (resized.compress(Bitmap.CompressFormat.JPEG, 75, output) &&
                        output.size() <= ArtworkPolicy.MAX_LOCAL_BYTES) return output.toByteArray();
                } finally {
                    if (resized != original) resized.recycle();
                }
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    public static boolean validEncoded(byte[] bytes) {
        if (bytes == null || bytes.length < 4 || bytes.length > ArtworkPolicy.MAX_LOCAL_BYTES) return false;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        return "image/jpeg".equals(options.outMimeType) && options.outWidth > 0 && options.outHeight > 0 &&
            options.outWidth <= ArtworkPolicy.MAX_LOCAL_SIDE && options.outHeight <= ArtworkPolicy.MAX_LOCAL_SIDE;
    }
}
