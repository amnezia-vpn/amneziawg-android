/*
 * Copyright © 2024 AmneziaWG Contributors. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.util;

import org.amnezia.awg.config.InetNetwork;
import org.amnezia.awg.config.ParseException;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Utility for subtracting specific IP addresses from a set of CIDR routes.
 * Used to implement domain-based split tunneling by removing resolved IPs
 * from the VPN routing table.
 */
@NonNullForAll
public final class IpSubtractor {

    private IpSubtractor() {}

    /**
     * Returns a new route list equivalent to {@code allowedIps} but with each address
     * in {@code excludedIps} removed. Works by recursively splitting CIDR blocks that
     * contain an excluded address into two halves, discarding the half-block that is the
     * excluded address itself.
     */
    public static List<InetNetwork> subtract(
            final Collection<InetNetwork> allowedIps,
            final Collection<InetAddress> excludedIps) {

        List<InetNetwork> result = new ArrayList<>(allowedIps);
        for (final InetAddress excluded : excludedIps) {
            final List<InetNetwork> next = new ArrayList<>(result.size() + 64);
            for (final InetNetwork cidr : result) {
                if (contains(cidr, excluded)) {
                    next.addAll(splitExcluding(cidr, excluded));
                } else {
                    next.add(cidr);
                }
            }
            result = next;
        }
        return result;
    }

    /** Returns true if {@code network} contains {@code address}. */
    private static boolean contains(final InetNetwork network, final InetAddress address) {
        if (!network.getAddress().getClass().equals(address.getClass())) return false;
        final byte[] netBytes = network.getAddress().getAddress();
        final byte[] addrBytes = address.getAddress();
        final int mask = network.getMask();
        final int fullBytes = mask / 8;
        final int remBits = mask % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (netBytes[i] != addrBytes[i]) return false;
        }
        if (remBits > 0) {
            final int bitMask = 0xFF & (0xFF << (8 - remBits));
            if ((netBytes[fullBytes] & bitMask) != (addrBytes[fullBytes] & bitMask)) return false;
        }
        return true;
    }

    /**
     * Splits {@code cidr} into sub-blocks covering everything in it except {@code excludeIp}.
     * Iteratively bisects until reaching a /32 (IPv4) or /128 (IPv6) for the excluded host.
     */
    private static List<InetNetwork> splitExcluding(
            final InetNetwork cidr, final InetAddress excludeIp) {
        final List<InetNetwork> result = new ArrayList<>();
        final int maxMask = (excludeIp instanceof Inet4Address) ? 32 : 128;
        InetNetwork current = cidr;
        while (current.getMask() < maxMask) {
            final InetNetwork[] halves = bisect(current);
            if (halves == null) break;
            if (contains(halves[0], excludeIp)) {
                result.add(halves[1]);
                current = halves[0];
            } else {
                result.add(halves[0]);
                current = halves[1];
            }
        }
        // current is now the exact /32 or /128 of the excluded address — omit it.
        return result;
    }

    /** Splits a CIDR block into its two equal halves with mask+1. Returns null on error. */
    private static InetNetwork[] bisect(final InetNetwork cidr) {
        try {
            final byte[] addr = cidr.getAddress().getAddress();
            final int newMask = cidr.getMask() + 1;
            final int byteIdx = cidr.getMask() / 8;
            final int bitIdx = cidr.getMask() % 8;

            final InetAddress first = InetAddress.getByAddress(addr);

            final byte[] secondAddr = addr.clone();
            secondAddr[byteIdx] = (byte) (secondAddr[byteIdx] | (0x80 >> bitIdx));
            final InetAddress second = InetAddress.getByAddress(secondAddr);

            final InetNetwork firstNet = InetNetwork.parse(first.getHostAddress() + "/" + newMask);
            final InetNetwork secondNet = InetNetwork.parse(second.getHostAddress() + "/" + newMask);
            return new InetNetwork[]{firstNet, secondNet};
        } catch (final Exception ignored) {
            return null;
        }
    }
}
