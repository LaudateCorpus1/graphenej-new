package cy.agorise.graphenej;

import com.google.gson.JsonElement;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;

import cy.agorise.graphenej.interfaces.ByteSerializable;
import cy.agorise.graphenej.interfaces.JsonSerializable;

/**
 * Class used to represent an existing HTLC contract.
 */
public class Htlc extends GrapheneObject implements ByteSerializable, JsonSerializable {

    public Htlc(String id) {
        super(id);
    }

    @Override
    public byte[] toBytes() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(byteArrayOutputStream);
        try {
            Varint.writeUnsignedVarLong(this.instance, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public String toJsonString() {
        return this.getObjectId();
    }

    @Override
    public JsonElement toJsonObject() {
        return null;
    }
}
