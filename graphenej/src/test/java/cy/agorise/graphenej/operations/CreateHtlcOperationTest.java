package cy.agorise.graphenej.operations;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.UnsignedLong;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Assert;
import org.junit.Test;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import cy.agorise.graphenej.Asset;
import cy.agorise.graphenej.AssetAmount;
import cy.agorise.graphenej.BaseOperation;
import cy.agorise.graphenej.BlockData;
import cy.agorise.graphenej.Chains;
import cy.agorise.graphenej.HtlcHash;
import cy.agorise.graphenej.HtlcHashType;
import cy.agorise.graphenej.Transaction;
import cy.agorise.graphenej.UserAccount;
import cy.agorise.graphenej.Util;

public class CreateHtlcOperationTest {
    private final String SERIALIZED_OP = "0000000000000000007b7c80241100000000000000a06e327ea7388c18e4740e350ed4e60f2e04fc41c8007800000000";
    private final String SERIALIZED_TX = "f68585abf4dce7c8045701310000000000000000007b7c80241100000000000000a06e327ea7388c18e4740e350ed4e60f2e04fc41c800780000000000";
    private final String JSON_SERIALIZED_TX = "{\"expiration\":\"2016-04-06T08:29:27\",\"extensions\":[],\"operations\":[[49,{\"amount\":{\"amount\":1123456,\"asset_id\":\"1.3.0\"},\"claim_period_seconds\":120,\"extensions\":[],\"fee\":{\"amount\":0,\"asset_id\":\"1.3.0\"},\"from\":\"1.2.123\",\"preimage_hash\":[0,\"a06e327ea7388c18e4740e350ed4e60f2e04fc41\"],\"preimage_size\":200,\"to\":\"1.2.124\"}]],\"ref_block_num\":34294,\"ref_block_prefix\":3707022213,\"signatures\":[]}";
    private final String PREIMAGE_HEX = "666f6f626172";
    private final String HASH_RIPEMD160 = "a06e327ea7388c18e4740e350ed4e60f2e04fc41";
    private final String HASH_SHA1 = "8843d7f92416211de9ebb963ff4ce28125932878";
    private final String HASH_SHA256 = "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2";

    private final Asset CORE = new Asset("1.3.0");

    private CreateHtlcOperation buildCreateHtlcOperation() throws NoSuchAlgorithmException {
        UserAccount from = new UserAccount("1.2.123");
        UserAccount to = new UserAccount("1.2.124");
        AssetAmount fee = new AssetAmount(UnsignedLong.valueOf(0), CORE);
        AssetAmount amount = new AssetAmount(UnsignedLong.valueOf(1123456), CORE);
        byte[] hashBytes = Util.htlcHash("foobar".getBytes(), HtlcHashType.RIPEMD160);
        HtlcHash preimageHash = new HtlcHash(HtlcHashType.RIPEMD160, hashBytes);
        return new CreateHtlcOperation(fee, from, to, amount, preimageHash, (short) 200, 120);
    }

    @Test
    public void testRipemd160(){
        try {
            byte[] hashRipemd160 = Util.htlcHash(Util.hexToBytes(PREIMAGE_HEX), HtlcHashType.RIPEMD160);
            String hexHash = Util.bytesToHex(hashRipemd160);
            Assert.assertEquals(HASH_RIPEMD160, hexHash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSha1(){
        try {
            byte[] hashSha1 = Util.htlcHash(Util.hexToBytes(PREIMAGE_HEX), HtlcHashType.SHA1);
            String hexHash = Util.bytesToHex(hashSha1);
            Assert.assertEquals(HASH_SHA1, hexHash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testSha256(){
        try {
            byte[] hashSha256 = Util.htlcHash(Util.hexToBytes(PREIMAGE_HEX), HtlcHashType.SHA256);
            String hexHash = Util.bytesToHex(hashSha256);
            Assert.assertEquals(HASH_SHA256, hexHash);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testOperationSerialization() throws NoSuchAlgorithmException {
        CreateHtlcOperation operation = this.buildCreateHtlcOperation();
        byte[] opBytes = operation.toBytes();
        Assert.assertArrayEquals(Util.hexToBytes(SERIALIZED_OP), opBytes);
    }

    @Test
    public void testTransactionSerialization() throws NoSuchAlgorithmException, ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date expirationDate = dateFormat.parse("2016-04-06T08:29:27");
        BlockData blockData = new BlockData(34294, 3707022213L, (expirationDate.getTime() / 1000));
        ArrayList<BaseOperation> operations = new ArrayList<>();
        operations.add(buildCreateHtlcOperation());
        Transaction transaction = new Transaction(blockData, operations);
        // Checking byte serialization
        byte[] txBytes = transaction.toBytes();
        byte[] expected = Bytes.concat(Util.hexToBytes(Chains.BITSHARES.CHAIN_ID), Util.hexToBytes(SERIALIZED_TX));
        Assert.assertArrayEquals(expected, txBytes);
        // Checking JSON serialization
        JsonObject jsonObject = transaction.toJsonObject();
        JsonArray operationsArray = jsonObject.get("operations").getAsJsonArray().get(0).getAsJsonArray();
        JsonArray hashArray = operationsArray.get(1).getAsJsonObject().get("preimage_hash").getAsJsonArray();
        Assert.assertEquals("2016-04-06T08:29:27", jsonObject.get("expiration").getAsString());
        Assert.assertEquals(49, operationsArray.get(0).getAsInt());
        Assert.assertEquals("1.2.123", operationsArray.get(1).getAsJsonObject().get("from").getAsString());
        Assert.assertEquals("1.2.124", operationsArray.get(1).getAsJsonObject().get("to").getAsString());
        Assert.assertEquals(0, hashArray.get(0).getAsInt());
        Assert.assertEquals(HASH_RIPEMD160, hashArray.get(1).getAsString());
    }
}
