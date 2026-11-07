package com.combiomae.mae12082;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.*;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.view.*;
import android.widget.*;
import android.media.ToneGenerator;
import android.media.AudioManager;
import androidx.core.content.FileProvider;
import org.json.*;
import java.net.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {

    String[] produtos = {"Diesel", "Óleo lubrificante (tanque)", "Óleo 68", "Óleo 10W30", "Óleo 15W40"};
    JSONArray movimentos = new JSONArray();
    LinearLayout raiz;
    TextView status;
    Button atualizar;
    ToneGenerator som;
    File apkBaixado;

    String repositorio = "https://api.github.com/repos/testeapp202627-star/ComboioMae12082-Atualizacoes/releases/latest";

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        som = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
        carregar();
        montarTela();
        verificarAtualizacao();
    }

    void beep() {
        try { som.startTone(ToneGenerator.TONE_PROP_BEEP, 180); } catch(Exception e) {}
    }

    void carregar() {
        try {
            String dados = getSharedPreferences("estoque",0).getString("movimentos","[]");
            movimentos = new JSONArray(dados);
        } catch(Exception e) { movimentos = new JSONArray(); }
    }

    void salvar() {
        getSharedPreferences("estoque",0).edit().putString("movimentos",movimentos.toString()).apply();
    }

    double saldo(int produto) {
        double total = 0;
        for(int i=0;i<movimentos.length();i++) {
            JSONObject m = movimentos.optJSONObject(i);
            if(m == null || m.optInt("produto") != produto) continue;
            double qtd = m.optDouble("qtd",0);
            total += m.optString("tipo").equals("Entrada") ? qtd : -qtd;
        }
        return total;
    }

    String litros(double n) {
        return String.format(Locale.getDefault(),"%.2f L",n);
    }

    Button botao(String nome, Runnable acao) {
        Button b = new Button(this);
        b.setText(nome);
        b.setOnClickListener(v -> { beep(); acao.run(); });
        return b;
    }

    TextView texto(String s, int tamanho) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(tamanho);
        t.setTextColor(Color.rgb(25,65,40));
        t.setPadding(8,8,8,8);
        return t;
    }

    void montarTela() {
        ScrollView scroll = new ScrollView(this);
        raiz = new LinearLayout(this);
        raiz.setOrientation(1);
        raiz.setPadding(14,14,14,24);
        raiz.setBackgroundColor(Color.rgb(230,245,233));
        scroll.addView(raiz);
        setContentView(scroll);
        atualizarTela();
    }

    void atualizarTela() {
        raiz.removeAllViews();

        TextView titulo = texto("COMBOIO\nMÃE 12082",27);
        titulo.setGravity(Gravity.CENTER);
        titulo.setTextColor(Color.rgb(15,90,40));
        raiz.addView(titulo);

        status = texto("Controle de estoque e movimentações",15);
        status.setGravity(Gravity.CENTER);
        raiz.addView(status);

        atualizar = botao("Verificar atualização", () -> verificarAtualizacao());
        raiz.addView(atualizar);

        raiz.addView(texto("ESTOQUES DISPONÍVEIS",21));

        for(int i=0;i<produtos.length;i++) {
            final int p = i;
            LinearLayout caixa = new LinearLayout(this);
            caixa.setOrientation(1);
            caixa.setPadding(10,10,10,10);
            caixa.setBackgroundColor(Color.WHITE);

            TextView nome = texto(produtos[i],19);
            nome.setTextColor(Color.rgb(20,90,45));
            caixa.addView(nome);

            TextView quantidade = texto("Saldo: "+litros(saldo(i)),22);
            quantidade.setTextColor(Color.BLACK);
            caixa.addView(quantidade);

            LinearLayout botoes = new LinearLayout(this);
            botoes.setOrientation(0);
            Button entrada = botao("＋ Entrada", () -> registrar(p,"Entrada"));
            Button saida = botao("－ Saída", () -> registrar(p,"Saída"));
            botoes.addView(entrada,new LinearLayout.LayoutParams(0,-2,1));
            botoes.addView(saida,new LinearLayout.LayoutParams(0,-2,1));
            caixa.addView(botoes);

            LinearLayout.LayoutParams margem = new LinearLayout.LayoutParams(-1,-2);
            margem.setMargins(0,8,0,8);
            raiz.addView(caixa,margem);
        }

        raiz.addView(texto("HISTÓRICO DE MOVIMENTAÇÕES",21));

        if(movimentos.length()==0) {
            raiz.addView(texto("Nenhuma movimentação registrada.",15));
        }

        for(int i=movimentos.length()-1;i>=0;i--) {
            final int indice = i;
            JSONObject m = movimentos.optJSONObject(i);
            if(m==null) continue;

            int p = m.optInt("produto");
            String nome = p>=0 && p<produtos.length ? produtos[p] : "Produto";
            String tipo = m.optString("tipo");
            String data = m.optString("data");
            double qtd = m.optDouble("qtd");

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(1);
            item.setPadding(8,8,8,8);
            item.setBackgroundColor(Color.WHITE);

            item.addView(texto(nome+" | "+tipo,17));
            item.addView(texto(litros(qtd)+" | "+data,14));

            LinearLayout acoes = new LinearLayout(this);
            acoes.addView(botao("Editar", () -> editar(indice)));
            acoes.addView(botao("Excluir", () -> excluir(indice)));
            item.addView(acoes);

            LinearLayout.LayoutParams margem = new LinearLayout.LayoutParams(-1,-2);
            margem.setMargins(0,5,0,5);
            raiz.addView(item,margem);
        }

        raiz.addView(botao("GERAR RELATÓRIO PDF", () -> gerarPDF()));
        raiz.addView(botao("RESETAR TODO O ESTOQUE", () -> resetar()));
    }

    void registrar(int produto, String tipo) {
        EditText campo = new EditText(this);
        campo.setHint("Quantidade em litros");
        campo.setInputType(8194);

        new AlertDialog.Builder(this)
            .setTitle(tipo+" - "+produtos[produto])
            .setView(campo)
            .setPositiveButton("Salvar",(d,w) -> {
                try {
                    double qtd = Double.parseDouble(campo.getText().toString().replace(",","."));
                    if(qtd<=0) throw new Exception();
                    if(tipo.equals("Saída") && qtd>saldo(produto)) {
                        aviso("Estoque insuficiente.");
                        return;
                    }
                    JSONObject m = new JSONObject();
                    m.put("produto",produto);
                    m.put("tipo",tipo);
                    m.put("qtd",qtd);
                    m.put("data",dataAtual());
                    movimentos.put(m);
                    salvar();
                    atualizarTela();
                    aviso("Movimentação registrada!");
                } catch(Exception e) { aviso("Informe uma quantidade válida."); }
            })
            .setNegativeButton("Cancelar",null).show();
    }

    String dataAtual() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date());
    }

    void editar(int indice) {
        JSONObject antigo = movimentos.optJSONObject(indice);
        if(antigo==null) return;

        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(20,5,20,5);
        layout.setOrientation(1);

        EditText qtd = new EditText(this);
        qtd.setInputType(8194);
        qtd.setHint("Quantidade em litros");
        qtd.setText(String.valueOf(antigo.optDouble("qtd")));
        layout.addView(qtd);

        Spinner tipo = new Spinner(this);
        tipo.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Entrada","Saída"}));
        tipo.setSelection(antigo.optString("tipo").equals("Saída")?1:0);
        layout.addView(tipo);

        new AlertDialog.Builder(this)
            .setTitle("Editar movimentação")
            .setView(layout)
            .setPositiveButton("Salvar",(d,w) -> {
                try {
                    double nova = Double.parseDouble(qtd.getText().toString().replace(",","."));
                    if(nova<=0) throw new Exception();

                    int p = antigo.optInt("produto");
                    double saldoSemAntiga = saldo(p);
                    saldoSemAntiga += antigo.optString("tipo").equals("Entrada") ? -antigo.optDouble("qtd") : antigo.optDouble("qtd");

                    String novoTipo = tipo.getSelectedItem().toString();
                    double saldoFinal = saldoSemAntiga + (novoTipo.equals("Entrada")?nova:-nova);

                    if(saldoFinal<0) {
                        aviso("Essa alteração deixaria o estoque negativo.");
                        return;
                    }

                    antigo.put("qtd",nova);
                    antigo.put("tipo",novoTipo);
                    antigo.put("data",dataAtual());
                    salvar();
                    atualizarTela();
                    aviso("Registro atualizado!");
                } catch(Exception e) { aviso("Quantidade inválida."); }
            })
            .setNegativeButton("Cancelar",null).show();
    }

    void excluir(int indice) {
        new AlertDialog.Builder(this)
            .setTitle("Excluir movimentação?")
            .setMessage("Deseja apagar este registro?")
            .setPositiveButton("Excluir",(d,w) -> {
                JSONArray novo = new JSONArray();
                for(int i=0;i<movimentos.length();i++)
                    if(i!=indice) novo.put(movimentos.opt(i));
                movimentos = novo;
                salvar();
                atualizarTela();
            })
            .setNegativeButton("Cancelar",null).show();
    }

    void resetar() {
        new AlertDialog.Builder(this)
            .setTitle("Resetar todo o estoque?")
            .setMessage("Todos os registros e saldos serão apagados. Essa ação não pode ser desfeita.")
            .setPositiveButton("APAGAR TUDO",(d,w) -> {
                movimentos = new JSONArray();
                salvar();
                atualizarTela();
                aviso("Estoque zerado.");
            })
            .setNegativeButton("Cancelar",null).show();
    }

    void aviso(String s) {
        Toast.makeText(this,s,Toast.LENGTH_SHORT).show();
    }

    void gerarPDF() {
        try {
            File pasta = new File(getFilesDir(),"relatorios");
            pasta.mkdirs();
            File arquivo = new File(pasta,"Relatorio_Comboio_Mae_12082.pdf");

            PdfDocument pdf = new PdfDocument();
            int pagina = 1;
            PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,pagina).create());
            Canvas c = page.getCanvas();
            Paint tinta = new Paint();
            tinta.setColor(Color.BLACK);
            tinta.setTextSize(18);
            c.drawText("COMBOIO MÃE 12082 - RELATÓRIO DE ESTOQUE",30,45,tinta);
            tinta.setTextSize(12);
            c.drawText("Gerado em: "+dataAtual(),30,70,tinta);

            int y = 110;
            tinta.setTextSize(15);
            c.drawText("SALDO ATUAL",30,y,tinta);
            y+=28;
            tinta.setTextSize(13);

            for(int i=0;i<produtos.length;i++) {
                c.drawText(produtos[i]+": "+litros(saldo(i)),30,y,tinta);
                y+=22;
            }

            y+=18;
            tinta.setTextSize(15);
            c.drawText("HISTÓRICO DE ENTRADAS E SAÍDAS",30,y,tinta);
            y+=28;
            tinta.setTextSize(11);

            for(int i=0;i<movimentos.length();i++) {
                JSONObject m = movimentos.optJSONObject(i);
                if(m==null) continue;
                int p = m.optInt("produto");
                String nome = p>=0 && p<produtos.length ? produtos[p] : "Produto";
                String linha = m.optString("data")+" | "+nome+" | "+m.optString("tipo")+" | "+litros(m.optDouble("qtd"));

                if(y>810) {
                    pdf.finishPage(page);
                    pagina++;
                    page = pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,pagina).create());
                    c = page.getCanvas();
                    y=45;
                }
                c.drawText(linha,30,y,tinta);
                y+=19;
            }

            pdf.finishPage(page);
            FileOutputStream out = new FileOutputStream(arquivo);
            pdf.writeTo(out);
            out.close();
            pdf.close();

            Uri uri = FileProvider.getUriForFile(this,getPackageName()+".fileprovider",arquivo);
            Intent enviar = new Intent(Intent.ACTION_SEND);
            enviar.setType("application/pdf");
            enviar.putExtra(Intent.EXTRA_STREAM,uri);
            enviar.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(enviar,"Enviar relatório PDF"));

        } catch(Exception e) {
            aviso("Erro ao gerar o relatório PDF.");
        }
    }

    void verificarAtualizacao() {
        status.setText("Consultando atualizações...");
        atualizar.setEnabled(false);

        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection)new URL(repositorio).openConnection();
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);
                c.setRequestProperty("Accept","application/vnd.github+json");

                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder resposta = new StringBuilder();
                String linha;
                while((linha=r.readLine())!=null) resposta.append(linha);
                r.close();
                c.disconnect();

                JSONObject release = new JSONObject(resposta.toString());
                String versao = release.getString("tag_name").replaceFirst("^[vV]","");
                JSONArray assets = release.getJSONArray("assets");
                String link = "";

                for(int i=0;i<assets.length();i++) {
                    JSONObject item = assets.getJSONObject(i);
                    if(item.getString("name").toLowerCase().endsWith(".apk")) {
                        link = item.getString("browser_download_url");
                        break;
                    }
                }

                final String urlAPK = link;
                runOnUiThread(() -> {
                    atualizar.setEnabled(true);
                    if(comparar(versao,BuildConfig.VERSION_NAME)>0 && !urlAPK.isEmpty()) {
                        status.setText("Nova versão disponível: "+versao);
                        atualizar.setText("Baixar atualização");
                        atualizar.setOnClickListener(v -> { beep(); baixar(urlAPK); });
                    } else {
                        status.setText("Aplicativo atualizado! Versão "+BuildConfig.VERSION_NAME);
                        atualizar.setText("Verificar novamente");
                        atualizar.setOnClickListener(v -> { beep(); verificarAtualizacao(); });
                    }
                });
            } catch(Exception e) {
                runOnUiThread(() -> {
                    status.setText("Não foi possível consultar o GitHub. Verifique a internet.");
                    atualizar.setEnabled(true);
                    atualizar.setText("Tentar novamente");
                });
            }
        }).start();
    }

    int comparar(String a,String b) {
        String[] x=a.split("\\.");
        String[] y=b.split("\\.");
        for(int i=0;i<Math.max(x.length,y.length);i++) {
            int n=i<x.length?numero(x[i]):0;
            int m=i<y.length?numero(y[i]):0;
            if(n>m)return 1;
            if(n<m)return -1;
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

                HttpURLConnection c = (HttpURLConnection)new URL(link).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(30000);
                c.setInstanceFollowRedirects(true);

                InputStream in=c.getInputStream();
                FileOutputStream out=new FileOutputStream(apkBaixado);
                byte[] buffer=new byte[8192];
                int n;
                while((n=in.read(buffer))!=-1) out.write(buffer,0,n);
                out.close();
                in.close();
                c.disconnect();

                if(apkBaixado.length()<100000) throw new Exception();

                runOnUiThread(() -> {
                    status.setText("Download concluído. Preparando instalação...");
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
            if(Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()) {
                Intent permissao=new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName()));
                startActivityForResult(permissao,100);
                status.setText("Autorize a instalação e volte ao aplicativo.");
                return;
            }

            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",apkBaixado);
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch(Exception e) { status.setText("Não foi possível abrir o instalador."); }
    }

    @Override
    protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==100 && Build.VERSION.SDK_INT>=26 && getPackageManager().canRequestPackageInstalls()) instalar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(som!=null) som.release();
    }
}
