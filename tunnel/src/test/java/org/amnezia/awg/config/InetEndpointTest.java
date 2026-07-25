/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InetEndpointTest {

    @Test
    public void ipv6_literal_is_parsed_as_canonical_numeric_host() throws ParseException {
        final InetEndpoint endpoint = InetEndpoint.parse("[2001:db8::1]:51820");
        // URI.getHost() yields the bracketed form; the stored host must be the bare, canonical one.
        assertEquals("Host is stored without brackets", "2001:db8::1", endpoint.getHost());
        assertEquals("Port is parsed", 51820, endpoint.getPort());
        // toString() must re-add brackets so the userspace string is valid for amneziawg-go.
        assertEquals("toString re-brackets the literal", "[2001:db8::1]:51820", endpoint.toString());
    }

    @Test
    public void ipv4_endpoint_is_unchanged() throws ParseException {
        final InetEndpoint endpoint = InetEndpoint.parse("192.0.2.1:51820");
        assertEquals("192.0.2.1", endpoint.getHost());
        assertEquals(51820, endpoint.getPort());
        assertEquals("192.0.2.1:51820", endpoint.toString());
    }

    @Test
    public void hostname_endpoint_is_preserved() throws ParseException {
        final InetEndpoint endpoint = InetEndpoint.parse("vpn.example.com:51820");
        assertEquals("vpn.example.com", endpoint.getHost());
        assertEquals(51820, endpoint.getPort());
    }

    @Test
    public void ipv6_endpoint_round_trips_to_userspace_string() throws BadConfigException {
        final Peer peer = new Peer.Builder()
                .parsePublicKey("vBN7qyUTb5lJtWYJ8LhbPio1Z4RcyBPGnqFBGn6O6Qg=")
                .parseEndpoint("[2001:db8::1]:51820")
                .build();
        assertTrue(
                "Userspace string keeps the bracketed IPv6 endpoint",
                peer.toAwgUserspaceString().contains("endpoint=[2001:db8::1]:51820")
        );
    }
}
