package cy.agorise.graphenej.operations;

import com.google.common.primitives.Bytes;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import cy.agorise.graphenej.AssetAmount;
import cy.agorise.graphenej.BaseOperation;
import cy.agorise.graphenej.HtlcHash;
import cy.agorise.graphenej.OperationType;
import cy.agorise.graphenej.UserAccount;
import cy.agorise.graphenej.Util;

public class CreateHtlcOperation extends BaseOperation {
    static final String KEY_FROM = "from";
    static final String KEY_TO = "to";
    static final String KEY_AMOUNT = "amount";
    static final String KEY_PREIMAGE_HASH = "preimage_hash";
    static final String KEY_PREIMAGE_SIZE = "preimage_size";
    static final String KEY_CLAIM_PERIOD_SECONDS = "claim_period_seconds";

    private AssetAmount fee;
    private UserAccount from;
    private UserAccount to;
    private AssetAmount amount;
    private HtlcHash preimageHash;
    private short preimageSize;
    private int claimPeriodSeconds;

    /**
     * Public constructor
     *
     * @param fee The operation fee.
     * @param from The source account.
     * @param to The destination account.
     * @param amount The amount to be traded.
     * @param hash The pre-image hash.
     * @param preimageSize The pre-image size.
     * @param claimPeriodSeconds The claim period, in seconds.
     */
    public CreateHtlcOperation(AssetAmount fee, UserAccount from, UserAccount to, AssetAmount amount, HtlcHash hash, short preimageSize, int claimPeriodSeconds) {
        super(OperationType.HTLC_CREATE_OPERATION);
        this.fee = fee;
        this.from = from;
        this.to = to;
        this.amount = amount;
        this.preimageHash = hash;
        this.preimageSize = preimageSize;
        this.claimPeriodSeconds = claimPeriodSeconds;
    }

    @Override
    public void setFee(AssetAmount newFee){
        this.fee = newFee;
    }

    public AssetAmount getFee() {
        return fee;
    }

    public UserAccount getFrom() {
        return from;
    }

    public void setFrom(UserAccount from) {
        this.from = from;
    }

    public UserAccount getTo() {
        return to;
    }

    public void setTo(UserAccount to) {
        this.to = to;
    }

    public AssetAmount getAmount() {
        return amount;
    }

    public void setAmount(AssetAmount amount) {
        this.amount = amount;
    }

    public HtlcHash getPreimageHash() {
        return preimageHash;
    }

    public void setPreimageHash(HtlcHash preimageHash) {
        this.preimageHash = preimageHash;
    }

    public short getPreimageSize() {
        return preimageSize;
    }

    public void setPreimageSize(short preimageSize) {
        this.preimageSize = preimageSize;
    }

    public int getClaimPeriodSeconds() {
        return claimPeriodSeconds;
    }

    public void setClaimPeriodSeconds(int claimPeriodSeconds) {
        this.claimPeriodSeconds = claimPeriodSeconds;
    }

    @Override
    public byte[] toBytes() {
        byte[] feeBytes = fee.toBytes();
        byte[] fromBytes = from.toBytes();
        byte[] toBytes = to.toBytes();
        byte[] amountBytes = amount.toBytes();
        byte[] htlcHashBytes = preimageHash.toBytes();
        byte[] preimageSizeBytes = Util.revertShort(preimageSize);
        byte[] claimPeriodBytes = Util.revertInteger(claimPeriodSeconds);
        byte[] extensionsBytes = extensions.toBytes();
        return Bytes.concat(feeBytes, fromBytes, toBytes, amountBytes, htlcHashBytes, preimageSizeBytes, claimPeriodBytes, extensionsBytes);
    }

    @Override
    public JsonElement toJsonObject() {
        JsonArray array = new JsonArray();
        array.add(this.getId());
        JsonObject jsonObject = new JsonObject();
        jsonObject.add(KEY_FEE, fee.toJsonObject());
        jsonObject.addProperty(KEY_FROM, from.getObjectId());
        jsonObject.addProperty(KEY_TO, to.getObjectId());
        jsonObject.add(KEY_AMOUNT, amount.toJsonObject());
        jsonObject.add(KEY_PREIMAGE_HASH, preimageHash.toJsonObject());
        jsonObject.addProperty(KEY_PREIMAGE_SIZE, preimageSize);
        jsonObject.addProperty(KEY_CLAIM_PERIOD_SECONDS, claimPeriodSeconds);
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
