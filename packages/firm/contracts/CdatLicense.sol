// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * CDAT — Cumulus Data license token.
 * One soulbound token per dataset sale. The licensee may use the dataset
 * under `terms` until `expiresAt`. Tokens are not transferable.
 * Source data is never stored on-chain; `datasetId` is the off-chain catalog id.
 */
contract CdatLicense {
    address public issuer;
    uint256 public nextId = 1;

    struct License {
        address licensee;
        bytes32 datasetHash;
        string datasetId;
        string terms;
        uint64 issuedAt;
        uint64 expiresAt;
        uint256 amountUsdCents;
        bool burned;
    }

    mapping(uint256 => License) public licenses;
    mapping(uint256 => address) public ownerOf;

    event Issued(uint256 indexed tokenId, address indexed licensee, string datasetId, string terms, uint64 expiresAt);
    event Burned(uint256 indexed tokenId);

    error NotIssuer();
    error Missing();
    error ExpiredOrBurned();

    modifier onlyIssuer() {
        if (msg.sender != issuer) revert NotIssuer();
        _;
    }

    constructor() {
        issuer = msg.sender;
    }

    function mint(
        address licensee,
        string calldata datasetId,
        string calldata terms,
        uint64 expiresAt,
        uint256 amountUsdCents
    ) external onlyIssuer returns (uint256 tokenId) {
        tokenId = nextId++;
        licenses[tokenId] = License({
            licensee: licensee,
            datasetHash: keccak256(bytes(datasetId)),
            datasetId: datasetId,
            terms: terms,
            issuedAt: uint64(block.timestamp),
            expiresAt: expiresAt,
            amountUsdCents: amountUsdCents,
            burned: false
        });
        ownerOf[tokenId] = licensee;
        emit Issued(tokenId, licensee, datasetId, terms, expiresAt);
    }

    function isActive(uint256 tokenId) external view returns (bool) {
        License storage lic = licenses[tokenId];
        return !lic.burned && lic.licensee != address(0) && block.timestamp < lic.expiresAt;
    }

    /// Soulbound: issuer can burn (consent revoked or refund). No transfer.
    function burn(uint256 tokenId) external onlyIssuer {
        if (ownerOf[tokenId] == address(0)) revert Missing();
        licenses[tokenId].burned = true;
        ownerOf[tokenId] = address(0);
        emit Burned(tokenId);
    }
}
