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

    ArrayList<String> produtos = new ArrayList<>(Arrays.asList("Diesel", "Óleo 68", "Óleo 10W30", "Óleo 15W40"));
    JSONArray movimentos = new JSONArray();
    LinearLayout raiz;
    int telaProduto = -1;
    boolean submenuOleos = false;
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
            android.content.SharedPreferences prefs = getSharedPreferences("estoque",0);
            movimentos = new JSONArray(prefs.getString("movimentos","[]"));
            if(prefs.contains("produtos")) {
                JSONArray listaSalva = new JSONArray(prefs.getString("produtos","[]"));
                produtos.clear();
                produtos.add("Diesel");
                for(int i=0;i<listaSalva.length();i++) produtos.add(listaSalva.optString(i));
            } else {
                salvarProdutos();
            }
            if(!prefs.getBoolean("migracaoV13",false)) {
                JSONArray corrigidos = new JSONArray();
                for(int i=0;i<movimentos.length();i++) {
                    JSONObject m = movimentos.optJSONObject(i);
                    if(m==null) continue;
                    int p = m.optInt("produto",-1);
                    if(p==1) continue;
                    if(p>1) m.put("produto",p-1);
                    if(p>=0 && p<=4) corrigidos.put(m);
                }
                movimentos = corrigidos;
                prefs.edit().putBoolean("migracaoV13",true).apply();
                salvar();
            }
        } catch(Exception e) {
            movimentos = new JSONArray();
        }
    }

    void salvarProdutos() {
        JSONArray lista = new JSONArray();
        for(int i=1;i<produtos.size();i++) lista.put(produtos.get(i));
        getSharedPreferences("estoque",0).edit().putString("produtos",lista.toString()).apply();
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

    void cabecalho() {
        TextView titulo = texto("COMBOIO\\nMÃE 12082",27);
        titulo.setGravity(Gravity.CENTER);
        titulo.setTextColor(Color.rgb(15,90,40));
        raiz.addView(titulo);

        status = texto("Controle de estoque e movimentações",15);
        status.setGravity(Gravity.CENTER);
        raiz.addView(status);

        atualizar = botao("Verificar atualização", () -> verificarAtualizacao());
        raiz.addView(atualizar);
    }

    void atualizarTela() {
        telaProduto = -1;
        submenuOleos = false;
        raiz.removeAllViews();
        cabecalho();

        raiz.addView(texto("CONTROLE DE ESTOQUE",21));
        raiz.addView(botao("⛽ TANQUE DE DIESEL", () -> abrirProduto(0)));
        raiz.addView(botao("ÓLEOS LUBRIFICANTES", () -> abrirOleos()));
        raiz.addView(botao("REGISTRAR SAÍDA POR FRENTE", () -> selecionarFrente()));
        raiz.addView(botao("GERAR RELATÓRIO PDF", () -> gerarPDF()));
        raiz.addView(botao("RESETAR TODO O ESTOQUE", () -> resetar()));
    }

    void abrirOleos() {
        telaProduto = -1;
        submenuOleos = true;
        raiz.removeAllViews();
        cabecalho();
        raiz.addView(texto("ÓLEOS LUBRIFICANTES",22));

        for(int i=1;i<produtos.size();i++) {
            final int indice = i;
            raiz.addView(botao(produtos.get(i).toUpperCase(),
                () -> abrirProduto(indice)));
        }

        raiz.addView(botao("＋ ADICIONAR ÓLEO", () -> adicionarOleo()));
        raiz.addView(botao("EDITAR ÓLEO", () -> selecionarOleo(false)));
        raiz.addView(botao("EXCLUIR ÓLEO", () -> selecionarOleo(true)));
        raiz.addView(botao("VOLTAR", () -> atualizarTela()));
    }

    void adicionarOleo() {
        EditText campo = new EditText(this);
        campo.setHint("Nome do novo óleo");
        new AlertDialog.Builder(this)
            .setTitle("Adicionar óleo")
            .setView(campo)
            .setPositiveButton("Salvar",(d,w) -> {
                String nome = campo.getText().toString().trim();
                if(nome.isEmpty() || nome.equalsIgnoreCase("Diesel")) {
                    aviso("Informe um nome válido.");
                    return;
                }
                if(nomeExiste(nome,-1)) {
                    aviso("Esse óleo já está cadastrado.");
                    return;
                }
                produtos.add(nome);
                salvarProdutos();
                abrirOleos();
                aviso("Óleo cadastrado!");
            })
            .setNegativeButton("Cancelar",null).show();
    }

    boolean nomeExiste(String nome, int ignorar) {
        for(int i=0;i<produtos.size();i++) {
            if(i!=ignorar && produtos.get(i).equalsIgnoreCase(nome)) return true;
        }
        return false;
    }

    void selecionarOleo(boolean excluir) {
        if(produtos.size()<=1) {
            aviso("Nenhum óleo cadastrado.");
            return;
        }
        String[] nomes = new String[produtos.size()-1];
        for(int i=1;i<produtos.size();i++) nomes[i-1]=produtos.get(i);

        new AlertDialog.Builder(this)
            .setTitle(excluir ? "Qual óleo deseja excluir?" : "Qual óleo deseja editar?")
            .setItems(nomes,(d,qual) -> {
                int indice = qual+1;
                if(excluir) excluirOleo(indice);
                else editarOleo(indice);
            }).setNegativeButton("Cancelar",null).show();
    }

    void editarOleo(int indice) {
        EditText campo = new EditText(this);
        campo.setText(produtos.get(indice));
        campo.setHint("Nome do óleo");
        new AlertDialog.Builder(this)
            .setTitle("Editar óleo")
            .setView(campo)
            .setPositiveButton("Salvar",(d,w) -> {
                String nome = campo.getText().toString().trim();
                if(nome.isEmpty() || nome.equalsIgnoreCase("Diesel")) {
                    aviso("Informe um nome válido.");
                    return;
                }
                if(nomeExiste(nome,indice)) {
                    aviso("Esse óleo já está cadastrado.");
                    return;
                }
                produtos.set(indice,nome);
                salvarProdutos();
                abrirOleos();
                aviso("Nome atualizado!");
            })
            .setNegativeButton("Cancelar",null).show();
    }

    void excluirOleo(int indice) {
        for(int i=0;i<movimentos.length();i++) {
            JSONObject m = movimentos.optJSONObject(i);
            if(m!=null && m.optInt("produto",-1)==indice) {
                aviso("Não é possível excluir: esse óleo possui histórico.");
                return;
            }
        }
        new AlertDialog.Builder(this)
            .setTitle("Excluir óleo?")
            .setMessage("Deseja excluir "+produtos.get(indice)+"?")
            .setPositiveButton("Excluir",(d,w) -> {
                for(int i=0;i<movimentos.length();i++) {
                    JSONObject m = movimentos.optJSONObject(i);
                    if(m!=null && m.optInt("produto",-1)>indice) {
                        try { m.put("produto",m.optInt("produto")-1); }
                        catch(Exception e) {}
                    }
                }
                produtos.remove(indice);
                salvar();
                salvarProdutos();
                abrirOleos();
                aviso("Óleo excluído!");
            })
            .setNegativeButton("Cancelar",null).show();
    }

    void abrirProduto(int produto) {
        telaProduto = produto;
        submenuOleos = false;
        raiz.removeAllViews();
        cabecalho();

        raiz.addView(texto(produtos.get(produto).toUpperCase(),23));
        TextView quantidade = texto("Saldo disponível: "+litros(saldo(produto)),22);
        quantidade.setTextColor(Color.BLACK);
        raiz.addView(quantidade);

        raiz.addView(botao("＋ ENTRADA", () -> registrar(produto,"Entrada")));
        raiz.addView(botao("－ SAÍDA", () -> registrar(produto,"Saída")));
        raiz.addView(texto("HISTÓRICO DE MOVIMENTAÇÕES",20));

        boolean encontrou = false;
        for(int i=movimentos.length()-1;i>=0;i--) {
            JSONObject m = movimentos.optJSONObject(i);
            if(m==null || m.optInt("produto")!=produto) continue;
            encontrou = true;
            final int indice = i;

            LinearLayout item = new LinearLayout(this);
            item.setOrientation(1);
            item.setPadding(8,8,8,8);
            item.setBackgroundColor(Color.WHITE);
            item.addView(texto(m.optString("tipo")+(m.optString("frente","").isEmpty()?"":" | "+m.optString("frente"))+" | "+litros(m.optDouble("qtd")),17));
            item.addView(texto(m.optString("data"),14));

            LinearLayout acoes = new LinearLayout(this);
            acoes.addView(botao("Editar", () -> editar(indice)));
            acoes.addView(botao("Excluir", () -> excluir(indice)));
            item.addView(acoes);

            LinearLayout.LayoutParams margem = new LinearLayout.LayoutParams(-1,-2);
            margem.setMargins(0,5,0,5);
            raiz.addView(item,margem);
        }

        if(!encontrou) raiz.addView(texto("Nenhuma movimentação registrada.",15));
        raiz.addView(botao("GERAR RELATÓRIO PDF", () -> gerarPDF()));
        raiz.addView(botao("VOLTAR", () -> {
            if(produto==0) atualizarTela();
            else abrirOleos();
        }));
    }


    void selecionarFrente() {
        String[] frentes = {"Frente 1", "Frente 2", "Frente 3", "Frente 4"};
        new AlertDialog.Builder(this)
            .setTitle("Selecione a frente")
            .setItems(frentes, (d, qual) -> selecionarProdutoSaida(frentes[qual]))
            .setNegativeButton("Cancelar", null).show();
    }

    void selecionarProdutoSaida(String frente) {
        String[] nomes = produtos.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle(frente + " - Selecione o produto")
            .setItems(nomes, (d, qual) -> registrarSaidaFrente(qual, frente))
            .setNegativeButton("Cancelar", null).show();
    }

    void registrarSaidaFrente(int produto, String frente) {
        EditText campo = new EditText(this);
        campo.setHint("Quantidade em litros");
        campo.setInputType(8194);
        new AlertDialog.Builder(this)
            .setTitle("Saída - " + frente + " - " + produtos.get(produto))
            .setView(campo)
            .setPositiveButton("Registrar saída", (d, w) -> {
                try {
                    double qtd = Double.parseDouble(campo.getText().toString().replace(",", "."));
                    if (qtd <= 0) throw new Exception();
                    if (qtd > saldo(produto)) {
                        aviso("Estoque insuficiente. Disponível: " + litros(saldo(produto)));
                        return;
                    }
                    JSONObject m = new JSONObject();
                    m.put("produto", produto);
                    m.put("tipo", "Saída");
                    m.put("qtd", qtd);
                    m.put("frente", frente);
                    m.put("data", dataAtual());
                    movimentos.put(m);
                    salvar();
                    atualizarTela();
                    aviso("Saída registrada para " + frente + "!");
                } catch(Exception e) {
                    aviso("Informe uma quantidade válida.");
                }
            })
            .setNegativeButton("Cancelar", null).show();
    }

    void registrar(int produto, String tipo) {
        EditText campo = new EditText(this);
        campo.setHint("Quantidade em litros");
        campo.setInputType(8194);

        new AlertDialog.Builder(this)
            .setTitle(tipo+" - "+produtos.get(produto))
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
                    abrirProduto(produto);
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
                    abrirProduto(antigo.optInt("produto"));
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
                int produto = movimentos.optJSONObject(indice).optInt("produto");
                JSONArray novo = new JSONArray();
                for(int i=0;i<movimentos.length();i++)
                    if(i!=indice) novo.put(movimentos.opt(i));
                movimentos = novo;
                salvar();
                abrirProduto(produto);
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

            for(int i=0;i<produtos.size();i++) {
                c.drawText(produtos.get(i)+": "+litros(saldo(i)),30,y,tinta);
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
                String nome = p>=0 && p<produtos.size() ? produtos.get(p) : "Produto";
                String linha = m.optString("data")+" | "+nome+" | "+m.optString("tipo")+(m.optString("frente","").isEmpty()?"":" | "+m.optString("frente"))+" | "+litros(m.optDouble("qtd"));

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
