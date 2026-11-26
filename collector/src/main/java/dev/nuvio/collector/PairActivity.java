package dev.nuvio.collector;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.view.Gravity;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.json.JSONObject;
import java.net.*;
import java.util.*;

public class PairActivity extends Activity {
    private TextView info;
    private Button pair;
    private AlertDialog qrDialog;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(1); l.setPadding(48,32,48,32); l.setBackgroundColor(0xff15131c);
        TextView title = new TextView(this);
        title.setText("SukiRPC Collector · Pareamento"); title.setTextSize(26); title.setTextColor(Color.WHITE); l.addView(title);
        info = new TextView(this); info.setTextSize(19); info.setTextColor(Color.WHITE); info.setPadding(0,24,0,24); l.addView(info);
        pair = new Button(this); pair.setText("Gerar código e QR (5 minutos)"); l.addView(pair); pair.setOnClickListener(v -> generate());
        Button revoke = new Button(this); revoke.setText("Desvincular celular"); l.addView(revoke);
        revoke.setOnClickListener(v -> {
            getSharedPreferences("lan", MODE_PRIVATE).edit().remove("token_hash").apply();
            LocalServer.cancelCode();
            if (qrDialog != null) qrDialog.dismiss();
            info.setText("Celular desvinculado. Gere um novo código.");
        });
        setContentView(l);
        info.setText("Autorize o acesso às notificações. Depois gere um código ou QR para conectar o celular.");
        pair.requestFocus();
    }
    private List<String> localIps() throws Exception {
        List<String> preferred = new ArrayList<>(), other = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return preferred;
        for (NetworkInterface n : Collections.list(interfaces)) {
            if (!n.isUp() || n.isLoopback()) continue;
            String name = n.getName().toLowerCase(Locale.ROOT);
            if (name.startsWith("tun") || name.startsWith("tap") || name.startsWith("ppp")) continue;
            for (InetAddress a : Collections.list(n.getInetAddresses())) {
                if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                    (name.startsWith("wlan") || name.startsWith("eth") ? preferred : other).add(a.getHostAddress());
                }
            }
        }
        preferred.addAll(other); return preferred;
    }
    private Bitmap qrBitmap(String payload) throws Exception {
        Map<EncodeHintType,Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 4);
        BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 512, 512, hints);
        int w = matrix.getWidth(), h = matrix.getHeight();
        int[] pixels = new int[w*h];
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) pixels[y*w+x] = matrix.get(x,y) ? Color.BLACK : Color.WHITE;
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    private void showQr(Bitmap bitmap, String ip) {
        ImageView image = new ImageView(this); image.setImageBitmap(bitmap); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = Math.round(240 * getResources().getDisplayMetrics().density);
        FrameLayout frame = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size,size,Gravity.CENTER);
        frame.addView(image,params);
        qrDialog = new AlertDialog.Builder(this)
            .setTitle("Escaneie no SukiRPC")
            .setMessage("IP: " + ip + " · Válido por 5 minutos, uso único. Não compartilhe este QR.")
            .setView(frame).setPositiveButton("Fechar",null).create();
        qrDialog.show();
    }
    private void generate() {
        pair.setEnabled(false); info.setText("Preparando identidade…");
        new Thread(() -> {
            try {
                List<String> ips = localIps();
                if (ips.isEmpty()) throw new IllegalStateException("Sem IPv4 privado");
                String fp = LocalServer.fingerprint();
                String code = LocalServer.newCode();
                String ip = ips.get(0);
                JSONObject payload = new JSONObject().put("type","nuvio_pairing").put("version",1)
                    .put("host",ip).put("port",LocalServer.PORT).put("identity",fp).put("code",code);
                Bitmap bitmap = qrBitmap(payload.toString());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    pair.setEnabled(true);
                    info.setText("IP: " + android.text.TextUtils.join("  ",ips) + "\nPorta: " + LocalServer.PORT
                        + "\nIdentidade: " + fp.substring(0,16) + "\nCódigo: " + code
                        + "\nUse o QR ou digite os dados manualmente. Válido por 5 min, uso único.");
                    if (qrDialog != null) qrDialog.dismiss();
                    showQr(bitmap,ip);
                });
            } catch(Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    pair.setEnabled(true);
                    info.setText("Falha ao gerar QR: " + e.getClass().getSimpleName());
                });
            }
        },"nuvio-qr").start();
    }
}
