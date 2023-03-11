import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.*;
import java.util.*;

import javax.crypto.*;

public class PrivateMessagingSecure extends UnicastRemoteObject implements PrivateMessagingSecureInterface {

    private String nome;
    private boolean receberPM;
    private HashMap<String, PublicKey> public_keys;

    public PrivateMessagingSecure(String nome, boolean receberPM, HashMap<String, PublicKey> public_keys)
            throws RemoteException {
        super();
        this.nome = nome;
        this.receberPM = receberPM;
        this.public_keys = public_keys;
    }

    public String sendMessage(String enviou, String msg) throws RemoteException {
        if (this.receberPM == true) {
            System.out.println("Mensagem privada de " + enviou + ": " + msg);
            return this.nome;
        } else {
            return "false";
        }
    }

    public String sendMessageSecure(String enviou, String msg, String assinatura) {
        if (this.receberPM == true) {
            try {
                byte[] decodedBytes = Base64.getDecoder().decode(assinatura);
                Cipher cipher = Cipher.getInstance("RSA");

                PublicKey pk = public_keys.get(enviou);
                cipher.init(Cipher.DECRYPT_MODE, pk);
                byte[] decipheredDigest = cipher.doFinal(decodedBytes);

                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(msg.getBytes());
                byte[] digest = md.digest();

                if (Arrays.equals(decipheredDigest, digest)) {
                    System.out.println("Mensagem privada de" + enviou + " (Mensagem Segura): " + msg);
                } else {
                    System.out.println(enviou + " enviou uma mensagem que sofreu alteracoes");
                }
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (NoSuchPaddingException e) {
                e.printStackTrace();
            } catch (InvalidKeyException e) {
                e.printStackTrace();
            } catch (IllegalBlockSizeException e) {
                e.printStackTrace();
            } catch (BadPaddingException e) {
                e.printStackTrace();
            }
            return this.nome;
        } else {
            return "false";
        }
    }

}