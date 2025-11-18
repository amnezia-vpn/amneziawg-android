/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.amnezia.awg.config;

import org.amnezia.awg.config.BadConfigException.Location;
import org.amnezia.awg.config.BadConfigException.Reason;
import org.amnezia.awg.config.BadConfigException.Section;
import org.amnezia.awg.crypto.Key;
import org.amnezia.awg.crypto.KeyFormatException;
import org.amnezia.awg.crypto.KeyPair;
import org.amnezia.awg.util.NonNullForAll;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import androidx.annotation.Nullable;

/**
 * Represents the configuration for an AmneziaWG interface (an [Interface] block). Interfaces must
 * have a private key (used to initialize a {@code KeyPair}), and may optionally have several other
 * attributes.
 * <p>
 * Instances of this class are immutable.
 */
@NonNullForAll
public final class Interface {
    private static final int MAX_UDP_PORT = 65535;
    private static final int MIN_UDP_PORT = 0;

    private final Set<InetNetwork> addresses;
    private final Set<InetAddress> dnsServers;
    private final Set<String> dnsSearchDomains;
    private final Set<String> excludedApplications;
    private final Set<String> includedApplications;
    private final KeyPair keyPair;
    private final Optional<Integer> listenPort;
    private final Optional<Integer> mtu;
    private final Optional<Integer> junkPacketCount;
    private final Optional<Integer> junkPacketMinSize;
    private final Optional<Integer> junkPacketMaxSize;
    private final Optional<Integer> initPacketJunkSize;
    private final Optional<Integer> responsePacketJunkSize;
    private final Optional<Integer> cookieReplyPacketJunkSize;
    private final Optional<Integer> transportPacketJunkSize;
    private final Optional<String> initPacketMagicHeader;
    private final Optional<String> responsePacketMagicHeader;
    private final Optional<String> underloadPacketMagicHeader;
    private final Optional<String> transportPacketMagicHeader;
    private final Optional<String> specialJunkI1;
    private final Optional<String> specialJunkI2;
    private final Optional<String> specialJunkI3;
    private final Optional<String> specialJunkI4;
    private final Optional<String> specialJunkI5;

    private Interface(final Builder builder) {
        // Defensively copy to ensure immutability even if the Builder is reused.
        addresses = Collections.unmodifiableSet(new LinkedHashSet<>(builder.addresses));
        dnsServers = Collections.unmodifiableSet(new LinkedHashSet<>(builder.dnsServers));
        dnsSearchDomains = Collections.unmodifiableSet(new LinkedHashSet<>(builder.dnsSearchDomains));
        excludedApplications = Collections.unmodifiableSet(new LinkedHashSet<>(builder.excludedApplications));
        includedApplications = Collections.unmodifiableSet(new LinkedHashSet<>(builder.includedApplications));
        keyPair = Objects.requireNonNull(builder.keyPair, "Interfaces must have a private key");
        listenPort = builder.listenPort;
        mtu = builder.mtu;
        junkPacketCount = builder.junkPacketCount;
        junkPacketMinSize = builder.junkPacketMinSize;
        junkPacketMaxSize = builder.junkPacketMaxSize;
        initPacketJunkSize = builder.initPacketJunkSize;
        responsePacketJunkSize = builder.responsePacketJunkSize;
        cookieReplyPacketJunkSize = builder.cookieReplyPacketJunkSize;
        transportPacketJunkSize = builder.transportPacketJunkSize;
        initPacketMagicHeader = builder.initPacketMagicHeader;
        responsePacketMagicHeader = builder.responsePacketMagicHeader;
        underloadPacketMagicHeader = builder.underloadPacketMagicHeader;
        transportPacketMagicHeader = builder.transportPacketMagicHeader;
        specialJunkI1 = builder.specialJunkI1;
        specialJunkI2 = builder.specialJunkI2;
        specialJunkI3 = builder.specialJunkI3;
        specialJunkI4 = builder.specialJunkI4;
        specialJunkI5 = builder.specialJunkI5;
    }

    /**
     * Parses an series of "KEY = VALUE" lines into an {@code Interface}. Throws
     * {@link ParseException} if the input is not well-formed or contains unknown attributes.
     *
     * @param lines An iterable sequence of lines, containing at least a private key attribute
     * @return An {@code Interface} with all of the attributes from {@code lines} set
     */
    public static Interface parse(final Iterable<? extends CharSequence> lines)
            throws BadConfigException {
        final Builder builder = new Builder();
        for (final CharSequence line : lines) {
            final Attribute attribute = Attribute.parse(line).orElseThrow(() ->
                    new BadConfigException(Section.INTERFACE, Location.TOP_LEVEL,
                            Reason.SYNTAX_ERROR, line));
            switch (attribute.getKey().toLowerCase(Locale.ENGLISH)) {
                case "address":
                    builder.parseAddresses(attribute.getValue());
                    break;
                case "dns":
                    builder.parseDnsServers(attribute.getValue());
                    break;
                case "excludedapplications":
                    builder.parseExcludedApplications(attribute.getValue());
                    break;
                case "includedapplications":
                    builder.parseIncludedApplications(attribute.getValue());
                    break;
                case "listenport":
                    builder.parseListenPort(attribute.getValue());
                    break;
                case "mtu":
                    builder.parseMtu(attribute.getValue());
                    break;
                case "privatekey":
                    builder.parsePrivateKey(attribute.getValue());
                    break;
                case "jc":
                    builder.parseJunkPacketCount(attribute.getValue());
                    break;
                case "jmin":
                    builder.parseJunkPacketMinSize(attribute.getValue());
                    break;
                case "jmax":
                    builder.parseJunkPacketMaxSize(attribute.getValue());
                    break;
                case "s1":
                    builder.parseInitPacketJunkSize(attribute.getValue());
                    break;
                case "s2":
                    builder.parseResponsePacketJunkSize(attribute.getValue());
                    break;
                case "s3":
                    builder.parseCookieReplyPacketJunkSize(attribute.getValue());
                    break;
                case "s4":
                    builder.parseTransportPacketJunkSize(attribute.getValue());
                    break;
                case "h1":
                    builder.parseInitPacketMagicHeader(attribute.getValue());
                    break;
                case "h2":
                    builder.parseResponsePacketMagicHeader(attribute.getValue());
                    break;
                case "h3":
                    builder.parseUnderloadPacketMagicHeader(attribute.getValue());
                    break;
                case "h4":
                    builder.parseTransportPacketMagicHeader(attribute.getValue());
                    break;
                case "i1":
                    builder.parseSpecialJunkI1(attribute.getValue());
                    break;
                case "i2":
                    builder.parseSpecialJunkI2(attribute.getValue());
                    break;
                case "i3":
                    builder.parseSpecialJunkI3(attribute.getValue());
                    break;
                case "i4":
                    builder.parseSpecialJunkI4(attribute.getValue());
                    break;
                case "i5":
                    builder.parseSpecialJunkI5(attribute.getValue());
                    break;
                default:
                    throw new BadConfigException(Section.INTERFACE, Location.TOP_LEVEL,
                            Reason.UNKNOWN_ATTRIBUTE, attribute.getKey());
            }
        }
        return builder.build();
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Interface))
            return false;
        final Interface other = (Interface) obj;
        return addresses.equals(other.addresses)
                && dnsServers.equals(other.dnsServers)
                && dnsSearchDomains.equals(other.dnsSearchDomains)
                && excludedApplications.equals(other.excludedApplications)
                && includedApplications.equals(other.includedApplications)
                && keyPair.equals(other.keyPair)
                && listenPort.equals(other.listenPort)
                && mtu.equals(other.mtu)
                && junkPacketCount.equals(other.junkPacketCount)
                && junkPacketMinSize.equals(other.junkPacketMinSize)
                && junkPacketMaxSize.equals(other.junkPacketMaxSize)
                && initPacketJunkSize.equals(other.initPacketJunkSize)
                && responsePacketJunkSize.equals(other.responsePacketJunkSize)
                && cookieReplyPacketJunkSize.equals(other.cookieReplyPacketJunkSize)
                && transportPacketJunkSize.equals(other.transportPacketJunkSize)
                && initPacketMagicHeader.equals(other.initPacketMagicHeader)
                && responsePacketMagicHeader.equals(other.responsePacketMagicHeader)
                && underloadPacketMagicHeader.equals(other.underloadPacketMagicHeader)
                && transportPacketMagicHeader.equals(other.transportPacketMagicHeader)
                && specialJunkI1.equals(other.specialJunkI1)
                && specialJunkI2.equals(other.specialJunkI2)
                && specialJunkI3.equals(other.specialJunkI3)
                && specialJunkI4.equals(other.specialJunkI4)
                && specialJunkI5.equals(other.specialJunkI5);
    }

    /**
     * Returns the set of IP addresses assigned to the interface.
     *
     * @return a set of {@link InetNetwork}s
     */
    public Set<InetNetwork> getAddresses() {
        // The collection is already immutable.
        return addresses;
    }

    /**
     * Returns the set of DNS servers associated with the interface.
     *
     * @return a set of {@link InetAddress}es
     */
    public Set<InetAddress> getDnsServers() {
        // The collection is already immutable.
        return dnsServers;
    }

    /**
     * Returns the set of DNS search domains associated with the interface.
     *
     * @return a set of strings
     */
    public Set<String> getDnsSearchDomains() {
        // The collection is already immutable.
        return dnsSearchDomains;
    }

    /**
     * Returns the set of applications excluded from using the interface.
     *
     * @return a set of package names
     */
    public Set<String> getExcludedApplications() {
        // The collection is already immutable.
        return excludedApplications;
    }

    /**
     * Returns the set of applications included exclusively for using the interface.
     *
     * @return a set of package names
     */
    public Set<String> getIncludedApplications() {
        // The collection is already immutable.
        return includedApplications;
    }

    /**
     * Returns the public/private key pair used by the interface.
     *
     * @return a key pair
     */
    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * Returns the UDP port number that the AmneziaWG interface will listen on.
     *
     * @return a UDP port number, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getListenPort() {
        return listenPort;
    }

    /**
     * Returns the MTU used for the AmneziaWG interface.
     *
     * @return the MTU, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getMtu() {
        return mtu;
    }

    /**
     * Returns the junkPacketCount used for the AmneziaWG interface.
     *
     * @return the junkPacketCount, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getJunkPacketCount() {
        return junkPacketCount;
    }

    /**
     * Returns the junkPacketMinSize used for the AmneziaWG interface.
     *
     * @return the junkPacketMinSize, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getJunkPacketMinSize() {
        return junkPacketMinSize;
    }

    /**
     * Returns the junkPacketMaxSize used for the AmneziaWG interface.
     *
     * @return the junkPacketMaxSize, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getJunkPacketMaxSize() {
        return junkPacketMaxSize;
    }

    /**
     * Returns the initPacketJunkSize used for the AmneziaWG interface.
     *
     * @return the initPacketJunkSize, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getInitPacketJunkSize() {
        return initPacketJunkSize;
    }

    /**
     * Returns the responsePacketJunkSize used for the AmneziaWG interface.
     *
     * @return the responsePacketJunkSize, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getResponsePacketJunkSize() {
        return responsePacketJunkSize;
    }

    /**
     * Returns the cookieReplyPacketJunkSize used for the AmneziaWG interface.
     *
     * @return the cookieReplyPacketJunkSize, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getCookieReplyPacketJunkSize() {
        return cookieReplyPacketJunkSize;
    }

    /**
     * Returns the transportPacketJunkSize used for the AmneziaWG interface.
     *
     * @return the transportPacketJunkSize, or {@code Optional.empty()} if none is configured
     */
    public Optional<Integer> getTransportPacketJunkSize() {
        return transportPacketJunkSize;
    }

    /**
     * Returns the initPacketMagicHeader used for the AmneziaWG interface.
     *
     * @return the initPacketMagicHeader, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getInitPacketMagicHeader() {
        return initPacketMagicHeader;
    }

    /**
     * Returns the responsePacketMagicHeader used for the AmneziaWG interface.
     *
     * @return the responsePacketMagicHeader, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getResponsePacketMagicHeader() {
        return responsePacketMagicHeader;
    }

    /**
     * Returns the underloadPacketMagicHeader used for the AmneziaWG interface.
     *
     * @return the underloadPacketMagicHeader, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getUnderloadPacketMagicHeader() {
        return underloadPacketMagicHeader;
    }

    /**
     * Returns the transportPacketMagicHeader used for the AmneziaWG interface.
     *
     * @return the transportPacketMagicHeader, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getTransportPacketMagicHeader() {
        return transportPacketMagicHeader;
    }

    /**
     * Returns the specialJunkI1 used for the AmneziaWG interface.
     *
     * @return the specialJunkI1, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getSpecialJunkI1() {
        return specialJunkI1;
    }

    /**
     * Returns the specialJunkI2 used for the AmneziaWG interface.
     *
     * @return the specialJunkI2, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getSpecialJunkI2() {
        return specialJunkI2;
    }

    /**
     * Returns the specialJunkI3 used for the AmneziaWG interface.
     *
     * @return the specialJunkI3, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getSpecialJunkI3() {
        return specialJunkI3;
    }

    /**
     * Returns the specialJunkI4 used for the AmneziaWG interface.
     *
     * @return the specialJunkI4, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getSpecialJunkI4() {
        return specialJunkI4;
    }

    /**
     * Returns the specialJunkI5 used for the AmneziaWG interface.
     *
     * @return the specialJunkI5, or {@code Optional.empty()} if none is configured
     */
    public Optional<String> getSpecialJunkI5() {
        return specialJunkI5;
    }


    @Override
    public int hashCode() {
        int hash = 1;
        hash = 31 * hash + addresses.hashCode();
        hash = 31 * hash + dnsServers.hashCode();
        hash = 31 * hash + excludedApplications.hashCode();
        hash = 31 * hash + includedApplications.hashCode();
        hash = 31 * hash + keyPair.hashCode();
        hash = 31 * hash + listenPort.hashCode();
        hash = 31 * hash + mtu.hashCode();
        hash = 31 * hash + junkPacketCount.hashCode();
        hash = 31 * hash + junkPacketMinSize.hashCode();
        hash = 31 * hash + junkPacketMaxSize.hashCode();
        hash = 31 * hash + initPacketJunkSize.hashCode();
        hash = 31 * hash + responsePacketJunkSize.hashCode();
        hash = 31 * hash + cookieReplyPacketJunkSize.hashCode();
        hash = 31 * hash + transportPacketJunkSize.hashCode();
        hash = 31 * hash + initPacketMagicHeader.hashCode();
        hash = 31 * hash + responsePacketMagicHeader.hashCode();
        hash = 31 * hash + underloadPacketMagicHeader.hashCode();
        hash = 31 * hash + transportPacketMagicHeader.hashCode();
        hash = 31 * hash + specialJunkI1.hashCode();
        hash = 31 * hash + specialJunkI2.hashCode();
        hash = 31 * hash + specialJunkI3.hashCode();
        hash = 31 * hash + specialJunkI4.hashCode();
        hash = 31 * hash + specialJunkI5.hashCode();
        return hash;
    }

    /**
     * Converts the {@code Interface} into a string suitable for debugging purposes. The {@code
     * Interface} is identified by its public key and (if set) the port used for its UDP socket.
     *
     * @return A concise single-line identifier for the {@code Interface}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("(Interface ");
        sb.append(keyPair.getPublicKey().toBase64());
        listenPort.ifPresent(lp -> sb.append(" @").append(lp));
        sb.append(')');
        return sb.toString();
    }

    /**
     * Converts the {@code Interface} into a string suitable for inclusion in a {@code awg-quick}
     * configuration file.
     *
     * @return The {@code Interface} represented as a series of "Key = Value" lines
     */
    public String toAwgQuickString() {
        final StringBuilder sb = new StringBuilder();
        if (!addresses.isEmpty())
            sb.append("Address = ").append(Attribute.join(addresses)).append('\n');
        if (!dnsServers.isEmpty()) {
            final List<String> dnsServerStrings = dnsServers.stream().map(InetAddress::getHostAddress).collect(Collectors.toList());
            dnsServerStrings.addAll(dnsSearchDomains);
            sb.append("DNS = ").append(Attribute.join(dnsServerStrings)).append('\n');
        }
        if (!excludedApplications.isEmpty())
            sb.append("ExcludedApplications = ").append(Attribute.join(excludedApplications)).append('\n');
        if (!includedApplications.isEmpty())
            sb.append("IncludedApplications = ").append(Attribute.join(includedApplications)).append('\n');
        listenPort.ifPresent(lp -> sb.append("ListenPort = ").append(lp).append('\n'));
        mtu.ifPresent(m -> sb.append("MTU = ").append(m).append('\n'));
        junkPacketCount.ifPresent(jc -> sb.append("Jc = ").append(jc).append('\n'));
        junkPacketMinSize.ifPresent(jmin -> sb.append("Jmin = ").append(jmin).append('\n'));
        junkPacketMaxSize.ifPresent(jmax -> sb.append("Jmax = ").append(jmax).append('\n'));
        initPacketJunkSize.ifPresent(s1 -> sb.append("S1 = ").append(s1).append('\n'));
        responsePacketJunkSize.ifPresent(s2 -> sb.append("S2 = ").append(s2).append('\n'));
        cookieReplyPacketJunkSize.ifPresent(s3 -> sb.append("S3 = ").append(s3).append('\n'));
        transportPacketJunkSize.ifPresent(s4 -> sb.append("S4 = ").append(s4).append('\n'));
        initPacketMagicHeader.ifPresent(h1 -> sb.append("H1 = ").append(h1).append('\n'));
        responsePacketMagicHeader.ifPresent(h2 -> sb.append("H2 = ").append(h2).append('\n'));
        underloadPacketMagicHeader.ifPresent(h3 -> sb.append("H3 = ").append(h3).append('\n'));
        transportPacketMagicHeader.ifPresent(h4 -> sb.append("H4 = ").append(h4).append('\n'));
        specialJunkI1.ifPresent(i1 -> sb.append("I1 = ").append(i1).append('\n'));
        specialJunkI2.ifPresent(i2 -> sb.append("I2 = ").append(i2).append('\n'));
        specialJunkI3.ifPresent(i3 -> sb.append("I3 = ").append(i3).append('\n'));
        specialJunkI4.ifPresent(i4 -> sb.append("I4 = ").append(i4).append('\n'));
        specialJunkI5.ifPresent(i5 -> sb.append("I5 = ").append(i5).append('\n'));
        sb.append("PrivateKey = ").append(keyPair.getPrivateKey().toBase64()).append('\n');
        return sb.toString();
    }

    /**
     * Serializes the {@code Interface} for use with the AmneziaWG cross-platform userspace API.
     * Note that not all attributes are included in this representation.
     *
     * @return the {@code Interface} represented as a series of "KEY=VALUE" lines
     */
    public String toAwgUserspaceString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("private_key=").append(keyPair.getPrivateKey().toHex()).append('\n');
        listenPort.ifPresent(lp -> sb.append("listen_port=").append(lp).append('\n'));
        junkPacketCount.ifPresent(jc -> sb.append("jc=").append(jc).append('\n'));
        junkPacketMinSize.ifPresent(jmin -> sb.append("jmin=").append(jmin).append('\n'));
        junkPacketMaxSize.ifPresent(jmax -> sb.append("jmax=").append(jmax).append('\n'));
        initPacketJunkSize.ifPresent(s1 -> sb.append("s1=").append(s1).append('\n'));
        responsePacketJunkSize.ifPresent(s2 -> sb.append("s2=").append(s2).append('\n'));
        cookieReplyPacketJunkSize.ifPresent(s3 -> sb.append("s3=").append(s3).append('\n'));
        transportPacketJunkSize.ifPresent(s4 -> sb.append("s4=").append(s4).append('\n'));
        initPacketMagicHeader.ifPresent(h1 -> sb.append("h1=").append(h1).append('\n'));
        responsePacketMagicHeader.ifPresent(h2 -> sb.append("h2=").append(h2).append('\n'));
        underloadPacketMagicHeader.ifPresent(h3 -> sb.append("h3=").append(h3).append('\n'));
        transportPacketMagicHeader.ifPresent(h4 -> sb.append("h4=").append(h4).append('\n'));
        specialJunkI1.ifPresent(i1 -> sb.append("i1=").append(i1).append('\n'));
        specialJunkI2.ifPresent(i2 -> sb.append("i2=").append(i2).append('\n'));
        specialJunkI3.ifPresent(i3 -> sb.append("i3=").append(i3).append('\n'));
        specialJunkI4.ifPresent(i4 -> sb.append("i4=").append(i4).append('\n'));
        specialJunkI5.ifPresent(i5 -> sb.append("i5=").append(i5).append('\n'));
        return sb.toString();
    }

    @SuppressWarnings("UnusedReturnValue")
    public static final class Builder {
        // Defaults to an empty set.
        private final Set<InetNetwork> addresses = new LinkedHashSet<>();
        // Defaults to an empty set.
        private final Set<InetAddress> dnsServers = new LinkedHashSet<>();
        // Defaults to an empty set.
        private final Set<String> dnsSearchDomains = new LinkedHashSet<>();
        // Defaults to an empty set.
        private final Set<String> excludedApplications = new LinkedHashSet<>();
        // Defaults to an empty set.
        private final Set<String> includedApplications = new LinkedHashSet<>();
        // No default; must be provided before building.
        @Nullable private KeyPair keyPair;
        // Defaults to not present.
        private Optional<Integer> listenPort = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> mtu = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> junkPacketCount = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> junkPacketMinSize = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> junkPacketMaxSize = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> initPacketJunkSize = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> responsePacketJunkSize = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> cookieReplyPacketJunkSize = Optional.empty();
        // Defaults to not present.
        private Optional<Integer> transportPacketJunkSize = Optional.empty();
        // Defaults to not present.
        private Optional<String> initPacketMagicHeader = Optional.empty();
        // Defaults to not present.
        private Optional<String> responsePacketMagicHeader = Optional.empty();
        // Defaults to not present.
        private Optional<String> underloadPacketMagicHeader = Optional.empty();
        // Defaults to not present.
        private Optional<String> transportPacketMagicHeader = Optional.empty();
        // Defaults to not present.
        private Optional<String> specialJunkI1 = Optional.empty();
        // Defaults to not present.
        private Optional<String> specialJunkI2 = Optional.empty();
        // Defaults to not present.
        private Optional<String> specialJunkI3 = Optional.empty();
        // Defaults to not present.
        private Optional<String> specialJunkI4 = Optional.empty();
        // Defaults to not present.
        private Optional<String> specialJunkI5 = Optional.empty();


        public Builder addAddress(final InetNetwork address) {
            addresses.add(address);
            return this;
        }

        public Builder addAddresses(final Collection<InetNetwork> addresses) {
            this.addresses.addAll(addresses);
            return this;
        }

        public Builder addDnsServer(final InetAddress dnsServer) {
            dnsServers.add(dnsServer);
            return this;
        }

        public Builder addDnsServers(final Collection<? extends InetAddress> dnsServers) {
            this.dnsServers.addAll(dnsServers);
            return this;
        }

        public Builder addDnsSearchDomain(final String dnsSearchDomain) {
            dnsSearchDomains.add(dnsSearchDomain);
            return this;
        }

        public Builder addDnsSearchDomains(final Collection<String> dnsSearchDomains) {
            this.dnsSearchDomains.addAll(dnsSearchDomains);
            return this;
        }

        public Interface build() throws BadConfigException {
            if (keyPair == null)
                throw new BadConfigException(Section.INTERFACE, Location.PRIVATE_KEY,
                        Reason.MISSING_ATTRIBUTE, null);
            if (!includedApplications.isEmpty() && !excludedApplications.isEmpty())
                throw new BadConfigException(Section.INTERFACE, Location.INCLUDED_APPLICATIONS,
                        Reason.INVALID_KEY, null);
            return new Interface(this);
        }

        public Builder excludeApplication(final String application) {
            excludedApplications.add(application);
            return this;
        }

        public Builder excludeApplications(final Collection<String> applications) {
            excludedApplications.addAll(applications);
            return this;
        }

        public Builder includeApplication(final String application) {
            includedApplications.add(application);
            return this;
        }

        public Builder includeApplications(final Collection<String> applications) {
            includedApplications.addAll(applications);
            return this;
        }

        public Builder parseAddresses(final CharSequence addresses) throws BadConfigException {
            try {
                for (final String address : Attribute.split(addresses))
                    addAddress(InetNetwork.parse(address));
                return this;
            } catch (final ParseException e) {
                throw new BadConfigException(Section.INTERFACE, Location.ADDRESS, e);
            }
        }

        public Builder parseDnsServers(final CharSequence dnsServers) throws BadConfigException {
            try {
                for (final String dnsServer : Attribute.split(dnsServers)) {
                    try {
                        addDnsServer(InetAddresses.parse(dnsServer));
                    } catch (final ParseException e) {
                        if (e.getParsingClass() != InetAddress.class || !InetAddresses.isHostname(dnsServer))
                            throw e;
                        addDnsSearchDomain(dnsServer);
                    }
                }
                return this;
            } catch (final ParseException e) {
                throw new BadConfigException(Section.INTERFACE, Location.DNS, e);
            }
        }

        public Builder parseExcludedApplications(final CharSequence apps) {
            return excludeApplications(List.of(Attribute.split(apps)));
        }

        public Builder parseIncludedApplications(final CharSequence apps) {
            return includeApplications(List.of(Attribute.split(apps)));
        }

        public Builder parseListenPort(final String listenPort) throws BadConfigException {
            try {
                return setListenPort(Integer.parseInt(listenPort));
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.LISTEN_PORT, listenPort, e);
            }
        }

        public Builder parseMtu(final String mtu) throws BadConfigException {
            try {
                return setMtu(Integer.parseInt(mtu));
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.MTU, mtu, e);
            }
        }

        public Builder parseJunkPacketCount(final String junkPacketCount) throws BadConfigException {
            try {
                return setJunkPacketCount(Integer.parseInt(junkPacketCount));
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_COUNT, junkPacketCount, e);
            }
        }

        public Builder parseJunkPacketMinSize(final String junkPacketMinSize) throws BadConfigException {
            try {
                return setJunkPacketMinSize(Integer.parseInt(junkPacketMinSize));
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MIN_SIZE, junkPacketMinSize, e);
            }
        }

        public Builder parseJunkPacketMaxSize(final String junkPacketMaxSize) throws BadConfigException {
            try {
                return setJunkPacketMaxSize(Integer.parseInt(junkPacketMaxSize));
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MAX_SIZE, junkPacketMaxSize, e);
            }
        }

        public Builder parseInitPacketJunkSize(final String initPacketJunkSize) throws BadConfigException {
            try {
                return setInitPacketJunkSize(Integer.parseInt(initPacketJunkSize));
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_JUNK_SIZE, initPacketJunkSize, e);
            }
        }

        public Builder parseResponsePacketJunkSize(final String responsePacketJunkSize) throws BadConfigException {
            try {
                return setResponsePacketJunkSize(Integer.parseInt(responsePacketJunkSize));
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.RESPONSE_PACKET_JUNK_SIZE, responsePacketJunkSize, e);
            }
        }

        public Builder parseCookieReplyPacketJunkSize(final String cookieReplyPacketJunkSize) throws BadConfigException {
            try {
                return setCookieReplyPacketJunkSize(Integer.parseInt(cookieReplyPacketJunkSize));
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.COOKIE_REPLY_PACKET_JUNK_SIZE, cookieReplyPacketJunkSize, e);
            }
        }

        public Builder parseTransportPacketJunkSize(final String transportPacketJunkSize) throws BadConfigException {
            try {
                return setTransportPacketJunkSize(Integer.parseInt(transportPacketJunkSize));
            } catch (final NumberFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.TRANSPORT_PACKET_JUNK_SIZE, transportPacketJunkSize, e);
            }
        }

        public Builder parseInitPacketMagicHeader(final String initPacketMagicHeader) throws BadConfigException {
            if (initPacketMagicHeader == null || initPacketMagicHeader.trim().isEmpty()) {
                this.initPacketMagicHeader = Optional.empty();
            } else {
                this.initPacketMagicHeader = Optional.of(initPacketMagicHeader.trim());
            }
            return this;
        }

        public Builder parseResponsePacketMagicHeader(final String responsePacketMagicHeader) throws BadConfigException {
            if (responsePacketMagicHeader == null || responsePacketMagicHeader.trim().isEmpty()) {
                this.responsePacketMagicHeader = Optional.empty();
            } else {
                this.responsePacketMagicHeader = Optional.of(responsePacketMagicHeader.trim());
            }
            return this;
        }

        public Builder parseUnderloadPacketMagicHeader(final String underloadPacketMagicHeader) throws BadConfigException {
            if (underloadPacketMagicHeader == null || underloadPacketMagicHeader.trim().isEmpty()) {
                this.underloadPacketMagicHeader = Optional.empty();
            } else {
                this.underloadPacketMagicHeader = Optional.of(underloadPacketMagicHeader.trim());
            }
            return this;
        }
        
        public Builder parseTransportPacketMagicHeader(final String transportPacketMagicHeader) throws BadConfigException {
            if (transportPacketMagicHeader == null || transportPacketMagicHeader.trim().isEmpty()) {
                this.transportPacketMagicHeader = Optional.empty();
            } else {
                this.transportPacketMagicHeader = Optional.of(transportPacketMagicHeader.trim());
            }
            return this;
        }

        public Builder parseSpecialJunkI1(final String specialJunkI1) throws BadConfigException {
            if (specialJunkI1 == null || specialJunkI1.trim().isEmpty()) {
                this.specialJunkI1 = Optional.empty();
            } else {
                this.specialJunkI1 = Optional.of(specialJunkI1.trim());
            }
            return this;
        }

        public Builder parseSpecialJunkI2(final String specialJunkI2) throws BadConfigException {
            if (specialJunkI2 == null || specialJunkI2.trim().isEmpty()) {
                this.specialJunkI2 = Optional.empty();
            } else {
                this.specialJunkI2 = Optional.of(specialJunkI2.trim());
            }
            return this;
        }

        public Builder parseSpecialJunkI3(final String specialJunkI3) throws BadConfigException {
            if (specialJunkI3 == null || specialJunkI3.trim().isEmpty()) {
                this.specialJunkI3 = Optional.empty();
            } else {
                this.specialJunkI3 = Optional.of(specialJunkI3.trim());
            }
            return this;
        }

        public Builder parseSpecialJunkI4(final String specialJunkI4) throws BadConfigException {
            if (specialJunkI4 == null || specialJunkI4.trim().isEmpty()) {
                this.specialJunkI4 = Optional.empty();
            } else {
                this.specialJunkI4 = Optional.of(specialJunkI4.trim());
            }
            return this;
        }

        public Builder parseSpecialJunkI5(final String specialJunkI5) throws BadConfigException {
            if (specialJunkI5 == null || specialJunkI5.trim().isEmpty()) {
                this.specialJunkI5 = Optional.empty();
            } else {
                this.specialJunkI5 = Optional.of(specialJunkI5.trim());
            }
            return this;
        }

        public Builder parsePrivateKey(final String privateKey) throws BadConfigException {
            try {
                return setKeyPair(new KeyPair(Key.fromBase64(privateKey)));
            } catch (final KeyFormatException e) {
                throw new BadConfigException(Section.INTERFACE, Location.PRIVATE_KEY, e);
            }
        }

        public Builder setKeyPair(final KeyPair keyPair) {
            this.keyPair = keyPair;
            return this;
        }

        public Builder setListenPort(final int listenPort) throws BadConfigException {
            if (listenPort < MIN_UDP_PORT || listenPort > MAX_UDP_PORT)
                throw new BadConfigException(Section.INTERFACE, Location.LISTEN_PORT,
                        Reason.INVALID_VALUE, String.valueOf(listenPort));
            this.listenPort = listenPort == 0 ? Optional.empty() : Optional.of(listenPort);
            return this;
        }

        public Builder setMtu(final int mtu) throws BadConfigException {
            if (mtu < 0)
                throw new BadConfigException(Section.INTERFACE, Location.MTU,
                        Reason.INVALID_VALUE, String.valueOf(mtu));
            this.mtu = mtu == 0 ? Optional.empty() : Optional.of(mtu);
            return this;
        }

        public Builder setJunkPacketCount(final int junkPacketCount) throws BadConfigException {
            if (junkPacketCount < 0)
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_COUNT,
                        Reason.INVALID_VALUE, String.valueOf(junkPacketCount));
            this.junkPacketCount = junkPacketCount == 0 ? Optional.empty() : Optional.of(junkPacketCount);
            return this;
        }

        public Builder setJunkPacketMinSize(final int junkPacketMinSize) throws BadConfigException {
            if (junkPacketMinSize < 0)
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MIN_SIZE,
                        Reason.INVALID_VALUE, String.valueOf(junkPacketMinSize));
            this.junkPacketMinSize = junkPacketMinSize == 0 ? Optional.empty() : Optional.of(junkPacketMinSize);
            return this;
        }

        public Builder setJunkPacketMaxSize(final int junkPacketMaxSize) throws BadConfigException {
            if (junkPacketMaxSize < 0)
                throw new BadConfigException(Section.INTERFACE, Location.JUNK_PACKET_MAX_SIZE,
                        Reason.INVALID_VALUE, String.valueOf(junkPacketMaxSize));
            this.junkPacketMaxSize = junkPacketMaxSize == 0 ? Optional.empty() : Optional.of(junkPacketMaxSize);
            return this;
        }

        public Builder setInitPacketJunkSize(final int initPacketJunkSize) throws BadConfigException {
            if (initPacketJunkSize < 0)
                throw new BadConfigException(Section.INTERFACE, Location.INIT_PACKET_JUNK_SIZE,
                        Reason.INVALID_VALUE, String.valueOf(initPacketJunkSize));
            this.initPacketJunkSize = initPacketJunkSize == 0 ? Optional.empty() : Optional.of(initPacketJunkSize);
            return this;
        }

        public Builder setResponsePacketJunkSize(final int responsePacketJunkSize) throws BadConfigException {
            if (responsePacketJunkSize < 0)
                throw new BadConfigException(Section.INTERFACE, Location.RESPONSE_PACKET_JUNK_SIZE,
                        Reason.INVALID_VALUE, String.valueOf(responsePacketJunkSize));
            this.responsePacketJunkSize = responsePacketJunkSize == 0 ? Optional.empty() : Optional.of(responsePacketJunkSize);
            return this;
        }

        public Builder setCookieReplyPacketJunkSize(final int cookieReplyPacketJunkSize) throws BadConfigException {
            if (cookieReplyPacketJunkSize < 0)
                throw new BadConfigException(Section.INTERFACE, Location.COOKIE_REPLY_PACKET_JUNK_SIZE,
                        Reason.INVALID_VALUE, String.valueOf(cookieReplyPacketJunkSize));
            this.cookieReplyPacketJunkSize = cookieReplyPacketJunkSize == 0 ? Optional.empty() : Optional.of(cookieReplyPacketJunkSize);
            return this;
        }

        public Builder setTransportPacketJunkSize(final int transportPacketJunkSize) throws BadConfigException {
            if (transportPacketJunkSize < 0)
                throw new BadConfigException(Section.INTERFACE, Location.TRANSPORT_PACKET_JUNK_SIZE,
                        Reason.INVALID_VALUE, String.valueOf(transportPacketJunkSize));
            this.transportPacketJunkSize = transportPacketJunkSize == 0 ? Optional.empty() : Optional.of(transportPacketJunkSize);
            return this;
        }

        public Builder setInitPacketMagicHeader(final String initPacketMagicHeader) throws BadConfigException {
            if (initPacketMagicHeader == null || initPacketMagicHeader.trim().isEmpty()) {
                this.initPacketMagicHeader = Optional.empty();
            } else {
                this.initPacketMagicHeader = Optional.of(initPacketMagicHeader.trim());
            }
            return this;
        }

        public Builder setResponsePacketMagicHeader(final String responsePacketMagicHeader) throws BadConfigException {
            if (responsePacketMagicHeader == null || responsePacketMagicHeader.trim().isEmpty()) {
                this.responsePacketMagicHeader = Optional.empty();
            } else {
                this.responsePacketMagicHeader = Optional.of(responsePacketMagicHeader.trim());
            }
            return this;
        }

        public Builder setUnderloadPacketMagicHeader(final String underloadPacketMagicHeader) throws BadConfigException {
            if (underloadPacketMagicHeader == null || underloadPacketMagicHeader.trim().isEmpty()) {
                this.underloadPacketMagicHeader = Optional.empty();
            } else {
                this.underloadPacketMagicHeader = Optional.of(underloadPacketMagicHeader.trim());
            }
            return this;
        }

        public Builder setTransportPacketMagicHeader(final String transportPacketMagicHeader) throws BadConfigException {
            if (transportPacketMagicHeader == null || transportPacketMagicHeader.trim().isEmpty()) {
                this.transportPacketMagicHeader = Optional.empty();
            } else {
                this.transportPacketMagicHeader = Optional.of(transportPacketMagicHeader.trim());
            }
            return this;
        }

        public Builder setSpecialJunkI1(final String specialJunkI1) throws BadConfigException {
            if (specialJunkI1 == null || specialJunkI1.trim().isEmpty()) {
                this.specialJunkI1 = Optional.empty();
            } else {
                this.specialJunkI1 = Optional.of(specialJunkI1.trim());
            }
            return this;
        }

        public Builder setSpecialJunkI2(final String specialJunkI2) throws BadConfigException {
            if (specialJunkI2 == null || specialJunkI2.trim().isEmpty()) {
                this.specialJunkI2 = Optional.empty();
            } else {
                this.specialJunkI2 = Optional.of(specialJunkI2.trim());
            }
            return this;
        }

        public Builder setSpecialJunkI3(final String specialJunkI3) throws BadConfigException {
            if (specialJunkI3 == null || specialJunkI3.trim().isEmpty()) {
                this.specialJunkI3 = Optional.empty();
            } else {
                this.specialJunkI3 = Optional.of(specialJunkI3.trim());
            }
            return this;
        }

        public Builder setSpecialJunkI4(final String specialJunkI4) throws BadConfigException {
            if (specialJunkI4 == null || specialJunkI4.trim().isEmpty()) {
                this.specialJunkI4 = Optional.empty();
            } else {
                this.specialJunkI4 = Optional.of(specialJunkI4.trim());
            }
            return this;
        }

        public Builder setSpecialJunkI5(final String specialJunkI5) throws BadConfigException {
            if (specialJunkI5 == null || specialJunkI5.trim().isEmpty()) {
                this.specialJunkI5 = Optional.empty();
            } else {
                this.specialJunkI5 = Optional.of(specialJunkI5.trim());
            }
            return this;
        }
    }
}
