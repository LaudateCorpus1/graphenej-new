package cy.agorise.graphenej;

import org.junit.Assert;
import org.junit.Test;

public class HtlcTest {
    private final Htlc htlc = new Htlc("1.16.124");

    @Test
    public void testByteSerialization(){
        Htlc htlc1 = new Htlc("1.16.1");
        Htlc htlc2 = new Htlc("1.16.100");
        Htlc htlc3 = new Htlc("1.16.500");
        Htlc htlc4 = new Htlc("1.16.1000");

        byte[] expected_1 = Util.hexToBytes("01");
        byte[] expected_2 = Util.hexToBytes("64");
        byte[] expected_3 = Util.hexToBytes("f403");
        byte[] expected_4 = Util.hexToBytes("e807");

        Assert.assertArrayEquals(expected_1, htlc1.toBytes());
        Assert.assertArrayEquals(expected_2, htlc2.toBytes());
        Assert.assertArrayEquals(expected_3, htlc3.toBytes());
        Assert.assertArrayEquals(expected_4, htlc4.toBytes());
    }
}
