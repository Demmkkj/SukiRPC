package dev.nuvio.collector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import org.json.JSONObject;
import dev.nuvio.artwork.ArtworkPolicy;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;

final class LocalServer {
    static final int PORT = 8766;
    static final String ALIAS = "nuvio-lan-tls-v2";
    private static final SecureRandom RNG = new SecureRandom();
    private static String pairCode = "";
    private static long expires;
    private static int attempts;
    private final android.net.nsd.NsdManager nsd;
    private android.net.nsd.NsdManager.RegistrationListener registration;
    private synchronized void announce(String pin){
        if(closed || registration!=null || nsd==null)return;
        android.net.nsd.NsdServiceInfo info=new android.net.nsd.NsdServiceInfo();
        info.setServiceName("SukiRPC "+android.os.Build.MODEL);info.setServiceType("_sukirpc._tcp.");info.setPort(PORT);
        info.setAttribute("pin",pin);info.setAttribute("model",android.os.Build.MODEL);info.setAttribute("version","2.0.0");info.setAttribute("protocol","2");
        registration=new android.net.nsd.NsdManager.RegistrationListener(){
            public void onServiceRegistered(android.net.nsd.NsdServiceInfo i){}
            public void onServiceUnregistered(android.net.nsd.NsdServiceInfo i){}
            public void onRegistrationFailed(android.net.nsd.NsdServiceInfo i,int e){}
            public void onUnregistrationFailed(android.net.nsd.NsdServiceInfo i,int e){}
        };
        try{nsd.registerService(info,android.net.nsd.NsdManager.PROTOCOL_DNS_SD,registration);}catch(RuntimeException e){registration=null;}
    }
    private final SharedPreferences prefs;
    private volatile SSLServerSocket server;
    private volatile boolean closed;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4));
    private final Set<SSLSocket> peers = ConcurrentHashMap.newKeySet();
    volatile java.util.List<String> packages=java.util.Collections.singletonList("com.nuvio.tv");
    static final class Snapshot {
        final String json;
        final long sampledAt;
        Snapshot(String json, long sampledAt) { this.json = json; this.sampledAt = sampledAt; }
        long age(long now) { return sampledAt < 0 ? Long.MAX_VALUE : Math.max(0, now - sampledAt); }
    }
    private volatile Snapshot snapshot = new Snapshot("{\"active\":false}", -1);
    private final CollectorService collector;
    LocalServer(CollectorService c) { collector = c; nsd=c.getSystemService(android.net.nsd.NsdManager.class);prefs = c.getSharedPreferences("lan", Context.MODE_PRIVATE); }

    static synchronized KeyStore identity() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyPairGenerator gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
            gen.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048).setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=SukiRPC Collector"))
                .setCertificateSerialNumber(new BigInteger(120, RNG).add(BigInteger.ONE))
                .setCertificateNotBefore(new Date(1577836800000L)).setCertificateNotAfter(new Date(4102444800000L)).build());
            gen.generateKeyPair();
        }
        return ks;
    }
    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x:b) s.append(String.format(Locale.ROOT,"%02x", x & 255)); return s.toString(); }
    static String fingerprint() throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(identity().getCertificate(ALIAS).getEncoded())); }
    static synchronized String newCode() { pairCode = String.format(Locale.ROOT,"%08d", RNG.nextInt(100000000)); expires=SystemClock.elapsedRealtime()+300000; attempts=0; return pairCode; }
    static synchronized void cancelCode() { pairCode=""; expires=0; }
    static boolean same(String a,String b) { return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)); }
    static String hash(String s) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    private synchronized JSONObject pair(String code) throws Exception {
        synchronized (LocalServer.class) {
            if (pairCode.isEmpty() || SystemClock.elapsedRealtime()>expires || attempts>=5) return error("pair_expired");
            attempts++;
            if (!same(pairCode,code)) return error("pair_invalid");
            byte[] b=new byte[32]; RNG.nextBytes(b); String token=hex(b);
            if (!prefs.edit().putString("token_hash",hash(token)).commit()) return error("storage");
            cancelCode(); return new JSONObject().put("ok",true).put("token",token);
        }
    }
    private static JSONObject error(String e) throws Exception { return new JSONObject().put("ok",false).put("error",e); }
    void update(String json) { snapshot = new Snapshot(json, SystemClock.elapsedRealtime()); }
    void start() { new Thread(this::serve,"nuvio-lan").start(); }
    private void serve() {
        try {
            KeyStore ks=identity();
            final PrivateKey key=(PrivateKey)ks.getKey(ALIAS,null);
            final X509Certificate cert=(X509Certificate)ks.getCertificate(ALIAS);
            X509ExtendedKeyManager km=new X509ExtendedKeyManager() {
                public String[] getClientAliases(String k,Principal[] p){return null;}
                public String chooseClientAlias(String[] k,Principal[] p,Socket s){return null;}
                public String[] getServerAliases(String k,Principal[] p){return k.equalsIgnoreCase("RSA")?new String[]{ALIAS}:null;}
                public String chooseServerAlias(String k,Principal[] p,Socket s){return k.equalsIgnoreCase("RSA")?ALIAS:null;}
                public String chooseEngineServerAlias(String k,Principal[] p,SSLEngine e){return chooseServerAlias(k,p,null);}
                public X509Certificate[] getCertificateChain(String a){return ALIAS.equals(a)?new X509Certificate[]{cert}:null;}
                public PrivateKey getPrivateKey(String a){return ALIAS.equals(a)?key:null;}
            };
            SSLContext tls=SSLContext.getInstance("TLS"); tls.init(new KeyManager[]{km},null,RNG);
            try (SSLServerSocket sock = (SSLServerSocket) tls.getServerSocketFactory().createServerSocket()) {
                sock.setReuseAddress(true);
                sock.setEnabledProtocols(new String[]{"TLSv1.2"});
                synchronized (this) {
                    if (closed) return;
                    sock.bind(new InetSocketAddress("0.0.0.0", PORT));
                    server = sock;
                }
                try{announce(fingerprint());}catch(Exception ignored){}
                while (!closed) enqueue((SSLSocket) sock.accept());
            }
        } catch(Exception e){if(!closed) Log.e("NuvioCollector","Servidor LAN indisponivel",e);}
    }

    private synchronized void enqueue(SSLSocket peer) throws IOException {
        if (closed || !peer.getInetAddress().isSiteLocalAddress()) {
            peer.close();
            return;
        }
        peers.add(peer);
        try {
            workers.execute(() -> {
                try { handle(peer); }
                finally { peers.remove(peer); }
            });
        } catch (RejectedExecutionException e) {
            peers.remove(peer);
            peer.close();
        }
    }
    private boolean authorized(JSONObject req) throws Exception {
        String expected = prefs.getString("token_hash", "");
        return !expected.isEmpty() && same(expected, hash(req.optString("token")));
    }

    private void handle(SSLSocket s) {
        try(SSLSocket peer=s){
            peer.setSoTimeout(5000); peer.startHandshake();
            InputStream in=peer.getInputStream(); ByteArrayOutputStream buf=new ByteArrayOutputStream();
            for(int i=0;i<4096;i++){int b=in.read();if(b<0)return;if(b==10)break;buf.write(b);if(i==4095)return;}
            JSONObject req=new JSONObject(new String(buf.toByteArray(),StandardCharsets.UTF_8)); JSONObject out;
            Snapshot sample = snapshot;
            long age = sample.age(SystemClock.elapsedRealtime());
            switch(req.optString("op")){
                case "pair": out=pair(req.optString("code"));break;
                case "artwork":
                    if (!authorized(req)) out = error("unauthorized");
                    else if (age > 6000) out = error("stale");
                    else {
                        String key = req.optString("key");
                        JSONObject current = new JSONObject(sample.json);
                        boolean matches = ArtworkPolicy.validLocalKey(key) && key.equals(current.optString("artwork_key"))
                            && packages.contains(current.optString("package"));
                        byte[] data = matches ? collector.readArtwork(key) : null;
                        out = new JSONObject().put("ok", true).put("key", key.length() == 32 ? key : "")
                            .put("jpeg", data == null ? "" : Base64.encodeToString(data, Base64.NO_WRAP));
                    }
                    break;
                case "playback":
                    if (!authorized(req)) out = error("unauthorized");
                    else if (age > 6000) out = error("stale");
                    else {
                        org.json.JSONArray requested=req.optJSONArray("packages");
                        java.util.ArrayList<String> allow=new java.util.ArrayList<>();
                        if(requested==null)allow.add("com.nuvio.tv");
                        else for(int i=0;i<Math.min(32,requested.length());i++){
                            String pkg=requested.optString(i,"");if(pkg.length()<=160 && pkg.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+"))allow.add(pkg);
                        }
                        packages=java.util.Collections.unmodifiableList(allow);
                        out=new JSONObject(sample.json);
                        if(!allow.contains(out.optString("package")))out=new JSONObject().put("active",false);
                        if (!req.optBoolean("artwork", true)) {
                            for (String field : new String[]{"artwork_url", "artwork_key", "artwork_source", "artwork_rejected"}) out.remove(field);
                        }
                        out.put("ok",true).put("protocol",2).put("collector_version","2.0.0").put("capabilities",new org.json.JSONArray().put("snapshot").put("media_packages").put("artwork_bitmap")).put("sample_age_ms",age);
                    }
                    break;
                default: out=error("unknown_operation");
            }
            peer.getOutputStream().write((out.toString()+"\n").getBytes(StandardCharsets.UTF_8)); peer.getOutputStream().flush();
        }catch(Exception e){if(!closed) Log.e("NuvioCollector","Falha na conexao: "+e.getClass().getSimpleName());}
    }
    synchronized void close() {
        closed = true;
        if (registration != null) {
            try { nsd.unregisterService(registration); } catch (Exception ignored) {}
            registration = null;
        }
        try { if (server != null) server.close(); } catch (IOException ignored) {}
        workers.shutdownNow();
        for (SSLSocket peer : peers) {
            try { peer.close(); } catch (IOException ignored) {}
        }
        peers.clear();
    }
}

