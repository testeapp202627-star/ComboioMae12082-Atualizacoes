package com.combiomae.mae12082;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import android.content.*;
import android.net.Uri;
import java.net.*;
import java.io.*;
import org.json.*;

public class MainActivity extends Activity {

    String repositorio = "https://api.github.com/repos/testeapp202627-star/ComboioMae12082-Atualizacoes/releases/latest";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        titulo.setPadding(10,30,10,30);
        tela.addView(titulo);

        TextView descricao = new TextView(this);
        descricao.setText("Aplicativo iniciado com sucesso!\n\nVersão 1.0\n\nVerificando atualizações...");
        descricao.setTextSize(18);
        descricao.setTextColor(Color.BLACK);
        descricao.setGravity(Gravity.CENTER);
        tela.addView(descricao);

        setContentView(tela);

        verificarAtualizacao();
    }

    void verificarAtualizacao() {
        new Thread(() -> {
            try {
                URL url = new URL(repositorio);
                HttpURLConnection conexao = (HttpURLConnection) url.openConnection();
                conexao.setRequestMethod("GET");
                conexao.setConnectTimeout(10000);
                conexao.setReadTimeout(10000);
                conexao.setRequestProperty("Accept", "application/vnd.github+json");

                BufferedReader leitor = new BufferedReader(
                    new InputStreamReader(conexao.getInputStream())
                );

                StringBuilder resposta = new StringBuilder();
                String linha;

                while ((linha = leitor.readLine()) != null) {
                    resposta.append(linha);
                }

                leitor.close();
                conexao.disconnect();

                JSONObject dados = new JSONObject(resposta.toString());

                String versaoNova = dados.getString("tag_name").replaceFirst("^[vV]", "");
                String versaoAtual = BuildConfig.VERSION_NAME;
                String link = dados.getString("html_url");

                if (compararVersoes(versaoNova, versaoAtual) > 0) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(this)
                            .setTitle("Nova atualização disponível!")
                            .setMessage("Versão instalada: " + versaoAtual +
                                "\nNova versão: " + versaoNova +
                                "\n\nDeseja abrir a página da atualização?")
                            .setPositiveButton("Ver atualização", (d,w) -> {
                                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
                                startActivity(i);
                            })
                            .setNegativeButton("Depois", null)
                            .show();
                    });
                }

            } catch (Exception e) {
                android.util.Log.e("Atualizacao", "Falha ao verificar", e);
            }
        }).start();
    }

    int compararVersoes(String nova, String atual) {
        String[] a = nova.split("\\.");
        String[] b = atual.split("\\.");

        for (int i = 0; i < Math.max(a.length,b.length); i++) {
            int x = i < a.length ? numero(a[i]) : 0;
            int y = i < b.length ? numero(b[i]) : 0;
            if (x > y) return 1;
            if (x < y) return -1;
        }
        return 0;
    }

    int numero(String valor) {
        try {
            return Integer.parseInt(valor.replaceAll("[^0-9].*", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
