import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClienteHandler extends Thread {
    LinkedList<ClienteHandler> listaUtilizadores;
    ArrayList<String> listaMensagens;
    ListaPresencas listaAtivos;
    Socket socketLigacao;
    BufferedReader entrada;
    PrintWriter saida;
    String username;
    Boolean receberPM;
    String publicKey;

    public ClienteHandler(Socket socketLigacao, ListaPresencas listaPresencas,
            LinkedList<ClienteHandler> listaUtilizadores, ArrayList<String> listaMensagens) {
        this.socketLigacao = socketLigacao;
        try {
            this.entrada = new BufferedReader(new InputStreamReader(socketLigacao.getInputStream()));
            this.saida = new PrintWriter(socketLigacao.getOutputStream());
            this.listaAtivos = listaPresencas;
            this.listaUtilizadores = listaUtilizadores;
            this.listaMensagens = listaMensagens;
        } catch (IOException e) {
            System.out.println("Erro ao estabelecer comunicação com cliente: " + e);
            System.exit(1);
        }
    }

    public void sessionUpdate(ClienteHandler user) {
        user.saida.println("SESSION_UPDATE:Username         IP           SuporteRMI");
        for (ClienteHandler u : listaUtilizadores) {
            user.saida.println("SESSION_UPDATE: " + u.username + "-"
                    + u.socketLigacao.getRemoteSocketAddress().toString() + "-" + u.receberPM + "-" + u.publicKey);
        }
        user.saida.println("SESSION_UPDATE:Ultimas 10 Mensagens:");
        for (String s : listaMensagens) {
            user.saida.println("SESSION_UPDATE:" + s);
        }
        user.saida.flush();
    }

    public void atualizarPresenca() {
        listaAtivos.getPresences(this.socketLigacao.getRemoteSocketAddress().toString(), this.username);
    }

    public void lerProtocolo(String msg) {
        String[] split = msg.split(":");
        String protocolo = split[0];

        int pos = msg.indexOf(':');
        String mensagem = msg.substring(pos + 2);
        if (protocolo.equals("AGENT_POST")) {
            atualizarPresenca();
            if (listaMensagens.size() >= 10) {
                listaMensagens.remove(0);
            }
            listaMensagens.add(mensagem);
            for (ClienteHandler u : listaUtilizadores) {
                sessionUpdate(u);
            }
        } else if (protocolo.equals("SESSION_UPDATE_REQUEST")) {
            if (mensagem.length() > 1) {
                String[] s = mensagem.split(":");
                String username = s[0];
                String receberPM = s[1];
                publicKey = s[2];
                System.out.println(publicKey);

                this.username = username;
                if (receberPM.equals("true")) {
                    this.receberPM = true;
                } else {
                    this.receberPM = false;
                }
                if (!listaUtilizadores.contains(this)) {
                    listaUtilizadores.add(this);
                }
                atualizarPresenca();
                for (ClienteHandler u : listaUtilizadores) {
                    sessionUpdate(u);
                }
            } else {
                atualizarPresenca();
                sessionUpdate(this);
            }
        } else {
            System.out.println("Erro ao receber mensagem");
        }
    }

    public void run() {
        while (socketLigacao.isConnected()) {
            try {
                String msg = entrada.readLine();
                lerProtocolo(msg);
            } catch (IOException e) {
                System.out.println("Cliente desconectou-se inesperadamente: " + e);
            }
        }
    }

}