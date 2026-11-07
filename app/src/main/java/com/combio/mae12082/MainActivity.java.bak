package com.combiomae.mae12082;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.*;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import androidx.core.content.FileProvider;
import org.json.*;
import java.net.*;
import java.io.*;

public class MainActivity extends Activity {

    TextView status;
    Button atualizar;
    File apkBaixado;

    String repositorio = "https://api.github.com/repos/testeapp202627-star/ComboioMae12082-Atualizacoes/releases/latest";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout tela = new LinearLayout(this);
        tela.setOrientation(1);
        tela.setGravity(Gravity.CENTER);
        tela.setPadding(25,25,25,25);
        tela.setBackgroundColor(Color.rgb(225,245,230));

        TextView titulo = new TextView(this);
        titulo.setText("COMBOIO\nMÃE 12082");
        titulo.setTextSize(30);
        titulo.setTextColor(Color.rgb(20,90,45));
        titulo.setGravity(Gravity.CENTER);
        tela.addView(titulo);

        status = new TextView(this);
        status.setText("Versão 1.1\nVerificando atualizações...");
        status.setTextSize(18);
        status.setTextColor(Color.BLACK);
        status.setGravity(Gravity.CENTER);
        status.setPadding(10,30,10,30);
        tela.addView(status);

        atualizar = new Button(this);
        atualizar.setText("Verificar atualização");
        tela.addView(atualizar);

        atualizar.setOnClickListener(v -> verificarAtualizacao());

        setContentView(tela);
        verificarAtualizacao();
    }

    void verificarAtualizacao() {
        status.setText("Consultando GitHub...");
        atualizar.setEnabled(false);

        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection)
                    new URL(repositorio).openConnection();

                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setRequestProperty("Accept","application/vnd.github+json");

                BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream()));

                StringBuilder resposta = new StringBuilder();
                String linha;
                while ((linha = r.readLine()) != null)
                    resposta.append(linha);

                r.close();
                c.disconnect();

                JSONObject release = new JSONObject(resposta.toString());
                String versao = release.getString("tag_name").replaceFirst("^[vV]","");
                JSONArray assets = release.getJSONArray("assets");

                String link = "";
                for (int i=0; i<assets.length(); i++) {
                    JSONObject item = assets.getJSONObject(i);
                    if (item.getString("name").toLowerCase().endsWith(".apk")) {
                        link = item.getString("browser_download_url");
                        break;
                    }
                }

                final String urlAPK = link;

                runOnUiThread(() -> {
                    atualizar.setEnabled(true);

                    if (comparar(versao, BuildConfig.VERSION_NAME) > 0 && !urlAPK.isEmpty()) {
                        status.setText("Nova versão disponível: " + versao);
                        atualizar.setText("Baixar atualização");
                        atualizar.setOnClickListener(v -> baixar(urlAPK));
                    } else {
                        status.setText("Aplicativo atualizado!\nVersão " + BuildConfig.VERSION_NAME);
                        atualizar.setText("Verificar novamente");
                        atualizar.setOnClickListener(v -> verificarAtualizacao());
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Não foi possível consultar o GitHub.\nVerifique sua internet.");
                    atualizar.setEnabled(true);
                    atualizar.setText("Tentar novamente");
                    atualizar.setOnClickListener(v -> verificarAtualizacao());
                });
            }
        }).start();
    }

    int comparar(String a, String b) {
        String[] x = a.split("\\.");
        String[] y = b.split("\\.");
        for (int i=0; i<Math.max(x.length,y.length); i++) {
            int n = i<x.length ? numero(x[i]) : 0;
            int m = i<y.length ? numero(y[i]) : 0;
            if (n>m) return 1;
            if (n<m) return -1;
        }
        return 0;
    }

    int numero(String s) {
        try { return Integer.parseInt(s.replaceAll("[^0-9].*","")); }
        catch(Exception e) { return 0; }
    }

    void baixar(String link) {
        atualizar.setEnabled(false);
        status.setText("Baixando atualização...");

        new Thread(() -> {
            try {
                File pasta = new File(getFilesDir(),"updates");
                pasta.mkdirs();

                apkBaixado = new File(pasta,"ComboioMae12082.apk");

                HttpURLConnection c = (HttpURLConnection)
                    new URL(link).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);

                InputStream in = c.getInputStream();
                FileOutputStream out = new FileOutputStream(apkBaixado);

                byte[] buffer = new byte[8192];
                int n;
                while ((n=in.read(buffer))!=-1)
                    out.write(buffer,0,n);

                out.close();
                in.close();
                c.disconnect();

                if (apkBaixado.length() < 100000) {
                    throw new Exception("Arquivo APK inválido");
                }

                runOnUiThread(() -> {
                    status.setText("Download concluído!\nPreparando instalação...");
                    atualizar.setEnabled(true);
                    instalar();
                });

            } catch(Exception e) {
                runOnUiThread(() -> {
                    status.setText("Erro ao baixar atualização.");
                    atualizar.setEnabled(true);
                });
            }
        }).start();
    }

    void instalar() {
        try {
            if (Build.VERSION.SDK_INT >= 26 &&
                !getPackageManager().canRequestPackageInstalls()) {
                Intent permissao = new Intent(
                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(permissao,100);
                status.setText("Autorize instalar aplicativos desta fonte e volte.");
                return;
            }

            Uri uri = FileProvider.getUriForFile(
                this,
                getPackageName()+".fileprovider",
                apkBaixado);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);

        } catch(Exception e) {
            status.setText("Não foi possível abrir o instalador.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == 100 && Build.VERSION.SDK_INT >= 26 &&
            getPackageManager().canRequestPackageInstalls()) {
            instalar();
        }
    }
}
