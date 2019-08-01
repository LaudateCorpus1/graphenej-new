package cy.agorise.graphenej.operations;

import com.google.common.primitives.Bytes;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import cy.agorise.graphenej.AssetAmount;
import cy.agorise.graphenej.BaseOperation;
import cy.agorise.graphenej.Htlc;
import cy.agorise.graphenej.OperationType;
import cy.agorise.graphenej.UserAccount;
import cy.agorise.graphenej.Util;
import cy.agorise.graphenej.Varint;

/**
 * Class used to encapsulate the redeem_htlc operation.
 */
public class RedeemHtlcOperation extends BaseOperation {
    static final String KEY_REDEEMER = "redeemer";
    static final String KEY_PREIMAGE = "preimage";
    static final String KEY_HTLC_ID = "htlc_id";

    private AssetAmount fee;
    private UserAccount redeemer;
    private Htlc htlc;
    private byte[] preimage;

    /**
     * Public constructor
     *
     * @param fee   The fee associated with this operation.
     * @param redeemer  The user account that will redeem the HTLC.
     * @param htlc    The existing HTLC operation.
     */
    public RedeemHtlcOperation(AssetAmount fee, UserAccount redeemer, Htlc htlc, byte[] preimage) {
        super(OperationType.HTLC_REDEEM_OPERATION);
        this.fee = fee;
        this.redeemer = redeemer;
        this.htlc = htlc;
        this.preimage = preimage;
    }

    @Override
    public void setFee(AssetAmount fee) {
        this.fee = fee;
    }

    public AssetAmount getFee(){
        return this.fee;
    }

    public UserAccount getRedeemer() {
        return redeemer;
    }

    public void setRedeemer(UserAccount redeemer) {
        this.redeemer = redeemer;
    }

    public Htlc getHtlc() {
        return htlc;
    }

    public void setHtlc(Htlc htlc) {
        this.htlc = htlc;
    }

    @Override
    public byte[] toBytes() {
        byte[] feeBytes = this.fee.toBytes();
        byte[] htlcBytes = this.htlc.toBytes();
        byte[] redeemerBytes = this.redeemer.toBytes();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(byteArrayOutputStream);
        try{
            Varint.writeUnsignedVarLong(this.preimage.length, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] preimageLength = byteArrayOutputStream.toByteArray();
        byte[] extensionsBytes = extensions.toBytes();
        return Bytes.concat(feeBytes, htlcBytes, redeemerBytes, preimageLength, this.preimage, extensionsBytes);
    }

    @Override
    public JsonElement toJsonObject() {
        JsonArray array = new JsonArray();
        array.add(this.getId());
        JsonObject jsonObject = new JsonObject();
        jsonObject.add(KEY_FEE, fee.toJsonObject());
        jsonObject.addProperty(KEY_REDEEMER, this.redeemer.getObjectId());
        jsonObject.addProperty(KEY_PREIMAGE, Util.bytesToHex(this.preimage));
        jsonObject.addProperty(KEY_HTLC_ID, this.htlc.getObjectId());
        jsonObject.add(KEY_EXTENSIONS, new JsonArray());
        array.add(jsonObject);
        return array;
    }

    @Override
    public String toJsonString() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        return gsonBuilder.create().toJson(this);
    }
}
