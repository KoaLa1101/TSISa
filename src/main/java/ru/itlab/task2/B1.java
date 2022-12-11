package ru.itlab.task2;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.*;
import java.util.Arrays;

public class B1 {
    // Мы генерируем ключи RSA
    final String KEY_ALGORITHM = "RSA";

    // SHA-256 мы генерируем хэш
    final String DIGEST_ALGORITHM = "SHA-256";

    // Подпись происходит SHA256withRSA
    final String SIGN_ALGORITHM = "SHA256withRSA";
    // private Pair<PublicKey, PrivateKey> keyPair;
    private KeyPair keyPair;
    private Connection connection;

    public static void main(String[] args) {
        new B1();
    }

    public B1() {
        try {
            keyPair = loadKeys();
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/blockchain", "koala1101", "KoaLa1101");
            addRecord("1");
            addRecord("2");
            addRecord("3");
            addRecord("4");
            addRecord("5");
            System.out.println(verification());
            System.out.println(validate(1));
            System.out.println(validate(2));
            System.out.println(validate(3));
        } catch (Exception e) {
            System.out.println(e.getMessage() + " " + e.getCause());
        }
    }

    private byte[] hash(byte[] data) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(data);
    }

    private byte[] generateRSAPSSSignature(PrivateKey privateKey, byte[] input) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signature = Signature.getInstance(SIGN_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(input);
        return signature.sign();
    }

    private boolean verifyRSAPSSSignature(PublicKey publicKey, byte[] input, byte[] encSignature) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signature = Signature.getInstance(SIGN_ALGORITHM);
        signature.initVerify(publicKey);
        signature.update(input);
        return signature.verify(encSignature);
    }


    private void genKeys() throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(1024, new SecureRandom());
        KeyPair keyPair = rsa.generateKeyPair();
        Files.write(Path.of("private.key"), keyPair.getPrivate().getEncoded());
        Files.write(Path.of("public.key"), keyPair.getPublic().getEncoded());
    }

    // Первым делом проверяем, что предыдущий хэш равен вычесленному предыдущему
    // Потом проверяем подписи хешей и данных и равенство хеш фактического и записанного
    private byte[] blockValidation(byte[] prevHash, ResultSet result) throws SQLException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {

        if (!Arrays.equals(prevHash, result.getBytes(5)))
            throw new IllegalStateException("block " + result.getInt(1) + " was corrupted");

        byte[] blockHash = result.getBytes(2);
        byte[] dataSign = result.getBytes(3);
        byte[] hashSign = result.getBytes(4);
        byte[] dataBytes = result.getString(6).getBytes();

        if (!verifyRSAPSSSignature(keyPair.getPublic(), blockHash, hashSign)
                || !verifyRSAPSSSignature(keyPair.getPublic(), dataBytes, dataSign)
                || !Arrays.equals(blockHash, hash(concat(prevHash, dataBytes, dataSign)))) {
            throw new IllegalStateException("block " + result.getInt(1) + " was corrupted");
        }

        return blockHash;
    }

    private boolean validate(int id) throws SQLException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {

        ResultSet result = connection.createStatement().executeQuery("select * from bc where id = " + (id));
        if (!result.next())
            throw new IllegalArgumentException("block with id " + id + " wasn't found");

        byte[] prevHash = null;

        if (id == 1) {
            prevHash = new byte[0];
        } else {
            ResultSet hashRes = connection.createStatement().executeQuery("select hash from bc where id = " + (id - 1));
            if (!hashRes.next())
                throw new IllegalArgumentException("previous block wasn't found, blockchain may be corrupted");
            prevHash = hashRes.getBytes(1);
        }

        blockValidation(prevHash, result);
        return true;
    }

    private boolean verification() throws SQLException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        var result = connection.createStatement().executeQuery("select * from bc order by id");
        if (!result.next())
            return true;

        //prev hash, block hash (prev + data + sign), data sign, hash sign
        var prevHash = new byte[0];
        do {
            prevHash = blockValidation(prevHash, result);
        } while (result.next());

        return true;
    }

    KeyPair loadKeys() throws NoSuchAlgorithmException, IOException {
        if (!Files.exists(Path.of(("public.key"))))
            genKeys();

        try {
            PublicKey publicKey
                    = KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of("public.key"))));
            PrivateKey privateKey
                    = KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of("private.key"))));
            return new KeyPair(publicKey, privateKey);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private void addRecord(String data) {
        try {
            byte[] dataBytes = data.getBytes();
            ResultSet result = connection.createStatement().executeQuery("select hash from bc where id = (select max(id) from bc)");

            byte[] prevHash = null;
            if (result.next()) {
                prevHash = result.getBytes(1);
            } else {
                prevHash = new byte[0];
            }

            var dataSign = generateRSAPSSSignature(keyPair.getPrivate(), dataBytes);

            PreparedStatement prepStatement = connection.prepareStatement("insert into bc values (default, ?, ?, ?, ?, ?)");
            byte[] blockHash = hash(concat(prevHash, dataBytes, dataSign));
            prepStatement.setBytes(1, blockHash);
            prepStatement.setBytes(2, dataSign);
            prepStatement.setBytes(3, generateRSAPSSSignature(keyPair.getPrivate(), blockHash));
            prepStatement.setBytes(4, prevHash);
            prepStatement.setString(5, data);
            prepStatement.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private byte[] concat(byte[]... arrs) {

        int totalLen = 0;
        for (int i = 0; i < arrs.length; i++)
            totalLen += arrs[i].length;
        byte[] allByteArray = new byte[totalLen];

        ByteBuffer buff = ByteBuffer.wrap(allByteArray);
        for (int i = 0; i < arrs.length; i++)
            buff.put(arrs[i]);

        return buff.array();
    }
}
