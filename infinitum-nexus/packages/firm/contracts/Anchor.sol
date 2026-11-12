// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/// @title FirmamentAnchor
/// @notice Records Merkle roots from Firmament on Base.
contract FirmamentAnchor {
    event Anchored(bytes32 indexed root, uint256 indexed timestamp, address indexed submitter);

    mapping(bytes32 => uint256) public roots;

    function anchor(bytes32 root) external {
        require(roots[root] == 0, "already anchored");
        roots[root] = block.timestamp;
        emit Anchored(root, block.timestamp, msg.sender);
    }

    function isAnchored(bytes32 root) external view returns (bool) {
        return roots[root] != 0;
    }
}
