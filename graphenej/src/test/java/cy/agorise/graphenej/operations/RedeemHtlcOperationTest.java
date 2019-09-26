package cy.agorise.graphenej.operations;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.UnsignedLong;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Assert;
import org.junit.Test;

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
import cy.agorise.graphenej.Htlc;
import cy.agorise.graphenej.Transaction;
import cy.agorise.graphenej.UserAccount;
import cy.agorise.graphenej.Util;

public class RedeemHtlcOperationTest {
    private final String SERIALIZED_OP = "00000000000000000084017c06666f6f62617200";
    private final String SERIALIZED_TX = "f68585abf4dce7c80457013200000000000000000084017c06666f6f6261720000";
    private final Asset CORE = new Asset("1.3.0");
    private final String PREIMAGE_HEX = "666f6f626172";

    private RedeemHtlcOperation buildRedeemdHtlcOperation(){
        AssetAmount fee = new AssetAmount(UnsignedLong.valueOf("0"), CORE);
        UserAccount redeemer = new UserAccount("1.2.124");
        Htlc htlc = new Htlc("1.16.132");
        byte[] preimage = Util.hexToBytes(PREIMAGE_HEX);
        return new RedeemHtlcOperation(fee, redeemer, htlc, preimage);
    }

    @Test
    public void testOperationSerialization(){
        RedeemHtlcOperation redeemHtlcOperation =  this.buildRedeemdHtlcOperation();
        byte[] opBytes = redeemHtlcOperation.toBytes();
        Assert.assertArrayEquals(Util.hexToBytes(SERIALIZED_OP), opBytes);
    }

    @Test
    public void testTransactionSerialization() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date expirationDate = dateFormat.parse("2016-04-06T08:29:27");
        BlockData blockData = new BlockData(34294, 3707022213L, (expirationDate.getTime() / 1000));
        ArrayList<BaseOperation> operations = new ArrayList<>();
        operations.add(buildRedeemdHtlcOperation());
        Transaction transaction = new Transaction(blockData, operations);
        // Checking byte serialization
        byte[] txBytes = transaction.toBytes();
        byte[] expected = Bytes.concat(Util.hexToBytes(Chains.BITSHARES.CHAIN_ID), Util.hexToBytes(SERIALIZED_TX));
        Assert.assertArrayEquals(expected, txBytes);
        // Checking JSON serialization
        JsonObject jsonObject = transaction.toJsonObject();
        JsonArray operationsArray = jsonObject.get("operations").getAsJsonArray().get(0).getAsJsonArray();
        int operationId = operationsArray.get(0).getAsInt();
        JsonObject operationJson = operationsArray.get(1).getAsJsonObject();
        Assert.assertEquals("2016-04-06T08:29:27", jsonObject.get("expiration").getAsString());
        Assert.assertEquals(50, operationId);
        Assert.assertEquals("1.16.132", operationJson.getAsJsonObject().get("htlc_id").getAsString());
        Assert.assertEquals(PREIMAGE_HEX, operationJson.getAsJsonObject().get("preimage").getAsString());
        Assert.assertEquals("1.2.124", operationJson.getAsJsonObject().get("redeemer").getAsString());
    }
}
