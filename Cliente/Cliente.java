import java.util.*;
import javax.crypto.*;
import java.io.*;
import java.net.Socket;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class Cliente {
    // Valores default
    static int porta = 2000;
    static String host = "127.0.0.1";
    private static String SERVICE_NAME = "/PrivateMessaging";
    private static boolean receberPM = true;
    private static HashMap<String, String> clientes = new HashMap<>();
    private static PublicKey publicKey;
    private static PrivateKey privateKey;
    private static HashMap<String, PublicKey> public_keys = new HashMap<>();

    public static void main(String[] args) {
        String username = "";
        if (args.length == 1) {
            username = args[0];
        } else if (args.length >= 3) {
            username = args[0];
            host = args[1];
            porta = Integer.parseInt(args[2]);
            if (args[3].equalsIgnoreCase("true")) {
                receberPM = true;
            } else {
                receberPM = false;
            }
        } else {
            System.out.println("Utilize java Cliente <username>");
            System.out.println("Utilize java Cliente <username> <host> <porta> <suporteRMI>");
            System.exit(-1);
        }

        try {
            Socket socketCliente = new Socket(host, porta);
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socketCliente.getInputStream()));
            PrintWriter saida = new PrintWriter(socketCliente.getOutputStream(), true);
            try {
                LocateRegistry.createRegistry(1099);
                PrivateMessagingSecureInterface ref = new PrivateMessagingSecure(username, receberPM, public_keys);
                LocateRegistry.getRegistry("127.0.0.1", 1099).rebind(SERVICE_NAME, ref);
            } catch (RemoteException e) {
                System.out.println("Erro ao criar registo");
            }
            try {
                KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("RSA");
                keyPairGen.initialize(2048);
                KeyPair pair = keyPairGen.generateKeyPair();
                publicKey = pair.getPublic();
                privateKey = pair.getPrivate();
            } catch (NoSuchAlgorithmException e1) {
                e1.printStackTrace();
            }

            String encodedString = Base64.getEncoder().encodeToString(publicKey.getEncoded());
            saida.println("SESSION_UPDATE_REQUEST: " + username + ":" + receberPM + ":" + encodedString);

            new Thread(new Runnable() {
                public void run() {
                    while (socketCliente.isConnected()) {
                        try {
                            String msg = entrada.readLine();
                            String[] split = msg.split(":");
                            String protocolo = split[0];
                            int pos = msg.indexOf(':');
                            String mensagem = msg.substring(pos + 1);
                            if (protocolo.equalsIgnoreCase("SESSION_UPDATE")) {
                                if (mensagem.startsWith(" ")) {
                                    mensagem = mensagem.substring(1);
                                    String[] split2 = mensagem.split("-");
                                    String nick = split2[0];
                                    int p = split2[1].indexOf(":");
                                    String IP = split2[1].substring(1, p);
                                    String pk = split2[3];
                                    if (!clientes.keySet().contains(nick)) {
                                        clientes.put(nick, IP);
                                    }
                                    if (!public_keys.keySet().contains(nick)) {
                                        byte[] decodedBytes = Base64.getDecoder().decode(pk);
                                        KeyFactory factory = KeyFactory.getInstance("RSA", "SunRsaSign");
                                        PublicKey public_key = (PublicKey) factory
                                                .generatePublic(new X509EncodedKeySpec(decodedBytes));
                                        public_keys.put(nick, public_key);
                                    }
                                    System.out.println(split2[0] + "      " + split2[1] + "      " + split2[2]);
                                } else {
                                    System.out.println(mensagem);
                                }
                            } else if (protocolo.equalsIgnoreCase("SESSION_TIMEOUT")) {
                                System.out.println(mensagem);
                                entrada.close();
                                saida.flush();
                                saida.close();
                                socketCliente.close();
                            }
                        } catch (IOException e) {
                            System.out.println("Erro ao comunicar com o servidor: " + e);
                            System.exit(-1);
                        } catch (NoSuchAlgorithmException e) {
                            e.printStackTrace();
                        } catch (NoSuchProviderException e) {
                            e.printStackTrace();
                        } catch (InvalidKeySpecException e) {
                            e.printStackTrace();
                        }

                    }
                }
            }).start();

            new java.util.Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    if (socketCliente.isConnected()) {
                        saida.println("SESSION_UPDATE_REQUEST: ");
                        saida.flush();
                    }
                }
            }, 1000 * 60, 1000 * 60);

            while (socketCliente.isConnected()) {
                Scanner input = new Scanner(System.in);
                System.out.print("");
                String msg = input.nextLine();
                String[] split = msg.split(" ");
                if (split[0].equalsIgnoreCase("/msg")) {
                    String destino = split[1];
                    String ip = clientes.get(destino);
                    msg = msg.substring(msg.indexOf(" ") + 1);
                    String corpo = msg.substring(msg.indexOf(" ") + 1);
                    PrivateMessagingSecureInterface ref;
                    try {
                        ref = (PrivateMessagingSecureInterface) LocateRegistry.getRegistry(ip).lookup(SERVICE_NAME);
                        System.out.println("1. Enviar mensagem privada normal");
                        System.out.println("2. Enviar mensagem privada segura");
                        Scanner opc = new Scanner(System.in);
                        String recebeu = null;
                        if (opc.nextLine().equalsIgnoreCase("1")) {
                            recebeu = ref.sendMessage(username, corpo);
                        } else {
                            MessageDigest md = MessageDigest.getInstance("SHA-256");
                            md.update(corpo.getBytes());
                            byte[] digest = md.digest();

                            Cipher cipher = Cipher.getInstance("RSA");
                            cipher.init(Cipher.ENCRYPT_MODE, privateKey);
                            cipher.update(digest);

                            byte[] cipherText = cipher.doFinal();

                            String msgBase = Base64.getEncoder().encodeToString(cipherText);

                            recebeu = ref.sendMessageSecure(username, corpo, msgBase);
                        }
                        if (recebeu.equalsIgnoreCase("false")) {
                            System.out.println("Este utilizador não quer receber mensagens");
                        } else {
                            System.out.println("Mensagem enviada a " + recebeu);
                        }
                    } catch (NotBoundException e) {
                        e.printStackTrace();
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                    } catch (IllegalBlockSizeException e) {
                        e.printStackTrace();
                    } catch (BadPaddingException e) {
                        e.printStackTrace();
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    } catch (NoSuchPaddingException e) {
                        e.printStackTrace();
                    }
                } else {
                    saida.println("AGENT_POST: " + username + ": " + msg);
                }
            }

        } catch (IOException e) {
            System.out.println("Erro ao comunicar com o servidor: " + e);
        }
    }
}
