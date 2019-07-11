package cy.agorise.graphenej;

import com.google.common.primitives.Bytes;

import cy.agorise.graphenej.interfaces.ByteSerializable;

/**
 * Class used to represent a HTLC hash.
 */
public class HtlcHash implements ByteSerializable {
    private HtlcHashType hashType;
    private byte[] hash;

    public HtlcHash(HtlcHashType hashType, byte[] hash) {
        this.hashType = hashType;
        this.hash = hash;
    }

    @Override
    public byte[] toBytes() {
        byte[] hashTypeBytes = new byte[] { Util.revertInteger(hashType.ordinal())[3] };
        return Bytes.concat(hashTypeBytes, hash);
    }
}
