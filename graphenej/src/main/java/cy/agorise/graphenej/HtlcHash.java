package cy.agorise.graphenej;

import com.google.common.primitives.Bytes;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import cy.agorise.graphenej.interfaces.ByteSerializable;
import cy.agorise.graphenej.interfaces.JsonSerializable;

/**
 * Class used to represent a HTLC hash.
 */
public class HtlcHash implements ByteSerializable, JsonSerializable {
    private HtlcHashType hashType;
    private byte[] hash;

    public HtlcHash(HtlcHashType hashType, byte[] hash) {
        this.hashType = hashType;
        this.hash = hash;
    }

    public HtlcHashType getType(){
        return this.hashType;
    }

    @Override
    public byte[] toBytes() {
        byte[] hashTypeBytes = new byte[] { Util.revertInteger(hashType.ordinal())[3] };
        return Bytes.concat(hashTypeBytes, hash);
    }

    @Override
    public String toJsonString() {
        JsonElement element = toJsonObject();
        return element.toString();
    }

    @Override
    public JsonElement toJsonObject() {
        JsonArray array = new JsonArray();
        array.add(hashType.ordinal());
        array.add(Util.byteToString(hash));
        return array;
    }
}
