package cy.agorise.graphenej;

/**
 * Used to enumerate the possible hash algorithms used in HTLCs.
 * @see <a href="https://github.com/bitshares/bitshares-core/blob/623aea265f2711adade982fc3248e6528dc8ac51/libraries/chain/include/graphene/chain/protocol/htlc.hpp">htlc.hpp</a>
 */
public enum HtlcHashType {
    RIPEMD160,
    SHA1,
    SHA256
}
