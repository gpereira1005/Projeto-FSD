import java.net.*;
import java.util.ArrayList;
import java.util.TimerTask;
import java.io.*;
import java.util.*;

public class Servidor {
    // Valores default
    static int porta = 2000;
    static int SESSION_TIMEOUT = 120;

    static LinkedList<ClienteHandler> listaUtilizadores;
    static ListaPresencas listaPresencas;
    static ArrayList<String> listaMensagens;

    public static ArrayList<ClienteHandler> verInativos() {
        Vector<String> ativos = listaPresencas.getIPList();
        ArrayList<ClienteHandler> inativos = new ArrayList<>();
        for (ClienteHandler u : listaUtilizadores) {
            if (!ativos.contains(u.username)) {
                inativos.add(u);
            }
        }
        return inativos;
    }

    public static void sessionTimeout() {
        ArrayList<ClienteHandler> inativos = verInativos();
        for (ClienteHandler u : inativos) {
            u.saida.println("SESSION_TIMEOUT: A sua sessão vai terminar");
            u.saida.flush();
            try {
                u.entrada.close();
                u.saida.flush();
                u.saida.close();
                u.socketLigacao.close();
                listaUtilizadores.remove(u);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void timerTimeout() {
        new java.util.Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                sessionTimeout();
            }
        }, 1000 * 5, 1000 * 5); // 5 segundos
    }

    public static void main(String[] args) {

        if (args.length == 2) {
            porta = Integer.parseInt(args[0]);
            SESSION_TIMEOUT = Integer.parseInt(args[1]);
        }

        listaUtilizadores = new LinkedList<>();
        listaPresencas = new ListaPresencas(SESSION_TIMEOUT);
        listaMensagens = new ArrayList<>();
        ServerSocket servidor = null;

        try {
            servidor = new ServerSocket(porta);
            System.out.println("Servidor iniciado com sucesso na porta " + porta);
        } catch (IOException e) {
            System.out.println("Erro ao iniciar o servidor: " + e);
            System.exit(1);
        }

        timerTimeout();

        while (!servidor.isClosed()) {

            try {
                Socket conexao = servidor.accept();
                ClienteHandler utilizadores = new ClienteHandler(conexao, listaPresencas, listaUtilizadores,
                        listaMensagens);
                utilizadores.start();
            } catch (IOException e) {

                System.out.println("Erro na execução do servidor: " + e);
            }
        }

    }
}
