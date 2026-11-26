package dev.nuvio.collector;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;
import org.json.JSONObject;
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
    private final SharedPreferences prefs;
    private volatile SSLServerSocket server;
    private volatile boolean closed;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(4));
    private volatile String snapshot = "{\"active\":false}";
    private volatile long sampledAt = -1;
    LocalServer(Context c) { prefs = c.getSharedPreferences("lan", Context.MODE_PRIVATE); }

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
    void update(String json) { snapshot=json; sampledAt=SystemClock.elapsedRealtime(); }
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
            SSLServerSocket sock=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket();
            sock.setReuseAddress(true); sock.setEnabledProtocols(new String[]{"TLSv1.2"});
            sock.bind(new InetSocketAddress("0.0.0.0",PORT)); server=sock;
            if(closed){sock.close();return;}
            while(!closed){
                SSLSocket peer=(SSLSocket)sock.accept();
                if(!peer.getInetAddress().isSiteLocalAddress()){peer.close();continue;}
                try { workers.execute(()->handle(peer)); } catch(RejectedExecutionException e){peer.close();}
            }
        } catch(Exception e){if(!closed) Log.e("NuvioCollector","Servidor LAN indisponivel",e);}
    }
    private void handle(SSLSocket s) {
        try(SSLSocket peer=s){
            peer.setSoTimeout(5000); peer.startHandshake();
            InputStream in=peer.getInputStream(); ByteArrayOutputStream buf=new ByteArrayOutputStream();
            for(int i=0;i<4096;i++){int b=in.read();if(b<0)return;if(b==10)break;buf.write(b);if(i==4095)return;}
            JSONObject req=new JSONObject(new String(buf.toByteArray(),StandardCharsets.UTF_8)); JSONObject out;
            switch(req.optString("op")){
                case "pair": out=pair(req.optString("code"));break;
                case "playback":
                    String expected=prefs.getString("token_hash","");
                    if(expected.isEmpty()||!same(expected,hash(req.optString("token")))) out=error("unauthorized");
                    else if(sampledAt<0||SystemClock.elapsedRealtime()-sampledAt>6000) out=error("stale");
                    else {out=new JSONObject(snapshot);out.put("ok",true);out.put("sample_age_ms",Math.max(0,SystemClock.elapsedRealtime()-sampledAt));}
                    break;
                default: out=error("unknown_operation");
            }
            peer.getOutputStream().write((out.toString()+"\n").getBytes(StandardCharsets.UTF_8)); peer.getOutputStream().flush();
        }catch(Exception e){Log.e("NuvioCollector","Falha na conexao: "+e.getClass().getSimpleName());}
    }
    void close(){closed=true; try{if(server!=null)server.close();}catch(IOException ignored){} workers.shutdownNow();}
}
