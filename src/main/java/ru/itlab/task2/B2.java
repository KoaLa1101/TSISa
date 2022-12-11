package ru.itlab.task2;

import com.google.common.base.Strings;
import kotlin.Pair;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.*;
import java.util.Arrays;

public class B2 {

    static final String KEY_ALGORITHM = "RSA";
    static final String DIGEST_ALGORITHM = "SHA-256";
    static final String SIGN_ALGORITHM = "SHA256withRSA";

    private Pair<PublicKey, PrivateKey> keyPair;
    private Connection connection;

    private PublicKey arbPubKey;
    private OkHttpClient httpClient;

    public static void main(String[] args) {
        new B2();
    }

    public B2() {
        try {
            httpClient = new OkHttpClient();
            arbPubKey = KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(
                    new X509EncodedKeySpec(decodeHex(httpClient.newCall(
                            new Request.Builder().url("http://89.108.115.118/ts/public").get().build()
                    ).execute().body().string())));

            keyPair = loadKeys();
            connection = DriverManager.getConnection("jdbc:postgresql://localhost:5432/blockchain", "koala1101", "KoaLa1101");
            System.out.println(verification());
            System.out.println(validate(1));
            System.out.println(validate(2));
            System.out.println(validate(3));
        } catch (Exception e) {
            System.out.println(e.getMessage() + " " + e.getCause());
        }
    }

    private String toHexString(byte[] arr) {
        String[] newArr = new String[arr.length];
        for (int i = 0; i < arr.length; i++)
            newArr[i] = Strings.padStart(Integer.toString(Byte.toUnsignedInt(arr[i]), 16), 2, '0');
        return String.join("", newArr);
    }

    private byte[] decodeHex(String val) {
        try {
            return  Hex.decodeHex(val.toCharArray());
        } catch (DecoderException e) {
            throw new RuntimeException(e);
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

    private byte[] blockValidation(byte[] prevHash, ResultSet result) throws SQLException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {

        if (!Arrays.equals(prevHash, result.getBytes(6)))
            throw new IllegalStateException("block " + result.getInt(1) + " was corrupted");

        byte[] blockHash = result.getBytes(2);
        byte[] dataSign = result.getBytes(3);
        String arbTimestamp = result.getString(4);
        byte[] arbSign = result.getBytes(5);
        byte[] dataBytes = result.getString(7).getBytes();

        if (!verifyRSAPSSSignature(arbPubKey, concat(arbTimestamp.getBytes(), blockHash), arbSign)
                || !verifyRSAPSSSignature(keyPair.getFirst(), dataBytes, dataSign)
                || !Arrays.equals(blockHash, hash(concat(prevHash, dataBytes, dataSign)))) {
            throw new IllegalStateException("block " + result.getInt(1) + " was corrupted");
        }

        return blockHash;
    }

    private boolean validate(int id) throws SQLException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {

        ResultSet result = connection.createStatement().executeQuery("select * from abc where id = " + (id));
        if (!result.next())
            throw new IllegalArgumentException("block with id " + id + " wasn't found");

        byte[] prevHash = null;

        if (id == 1) {
            prevHash = new byte[0];
        } else {
            ResultSet hashRes = connection.createStatement().executeQuery("select hash from abc where id = " + (id - 1));
            if (!hashRes.next())
                throw new IllegalArgumentException("previous block wasn't found, blockchain may be corrupted");
            prevHash = hashRes.getBytes(1);
        }

        blockValidation(prevHash, result);
        return true;
    }

    private boolean verification() throws SQLException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        var result = connection.createStatement().executeQuery("select * from abc order by id");
        if (!result.next())
            return true;

        //prev hash, block hash (prev + data + sign), data sign, hash sign
        var prevHash = new byte[0];
        do {
            prevHash = blockValidation(prevHash, result);
        } while (result.next());

        return true;
    }

    private void genKeys() throws NoSuchAlgorithmException, IOException {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(1024, new SecureRandom());
        KeyPair keyPair = rsa.generateKeyPair();
        Files.write(Path.of("private.key"), keyPair.getPrivate().getEncoded());
        Files.write(Path.of("public.key"), keyPair.getPublic().getEncoded());
    }

    Pair<PublicKey, PrivateKey> loadKeys() throws NoSuchAlgorithmException, IOException {
        if (!Files.exists(Path.of(("public.key"))))
            genKeys();

        try {
            PublicKey publicKey
                    = KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of("public.key"))));
            PrivateKey privateKey
                    = KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Path.of("private.key"))));
            return new Pair<>(publicKey, privateKey);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private void addRecord(String data) {
        try {
            byte[] dataBytes = data.getBytes();
            ResultSet result = connection.createStatement().executeQuery("select hash from abc where id = (select max(id) from abc)");
            byte[] prevHash = null;
            if (result.next()) {
                prevHash = result.getBytes(1);
            } else {
                prevHash = new byte[0];
            }

            byte[] dataSign = generateRSAPSSSignature(keyPair.getSecond(), dataBytes);

            PreparedStatement prepStatement = connection.prepareStatement("insert into abc values (default, ?, ?, ?, ?, ?, ?)");
            byte[] blockHash = hash(concat(prevHash, dataBytes, dataSign));
            prepStatement.setBytes(1, blockHash);
            prepStatement.setBytes(2, dataSign);

            JSONObject resp = new JSONObject(httpClient.newCall(
                    new Request.Builder()
                            .get()
                            .url("http://89.108.115.118/ts?digest=" + toHexString(blockHash))
                            .build()
            ).execute().body().string()).getJSONObject("timeStampToken");

            prepStatement.setString(3, resp.getString("ts"));
            prepStatement.setBytes(4, decodeHex(resp.getString("signature")));
            prepStatement.setBytes(5, prevHash);
            prepStatement.setString(6, data);
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
