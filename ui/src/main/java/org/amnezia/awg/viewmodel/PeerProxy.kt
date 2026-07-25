/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.viewmodel

import android.os.Parcel
import android.os.Parcelable
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.databinding.Observable
import androidx.databinding.Observable.OnPropertyChangedCallback
import androidx.databinding.ObservableList
import org.amnezia.awg.BR
import org.amnezia.awg.config.Attribute
import org.amnezia.awg.config.BadConfigException
import org.amnezia.awg.config.Peer
import java.lang.ref.WeakReference

class PeerProxy : BaseObservable, Parcelable {
    private val dnsRoutes: MutableList<String?> = ArrayList()
    private var allowedIpsState = AllowedIpsState.INVALID
    private var interfaceDnsListener: InterfaceDnsListener? = null
    private var peerListListener: PeerListListener? = null
    private var owner: ConfigProxy? = null
    private var totalPeers = 0

    @get:Bindable
    var allowedIps: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.allowedIps)
            calculateAllowedIpsState()
        }

    @get:Bindable
    var endpoint: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.endpoint)
        }

    @get:Bindable
    var persistentKeepalive: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.persistentKeepalive)
        }

    @get:Bindable
    var preSharedKey: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.preSharedKey)
        }

    @get:Bindable
    var publicKey: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.publicKey)
        }

    @get:Bindable
    val isAbleToExcludePrivateIps: Boolean
        get() = allowedIpsState == AllowedIpsState.CONTAINS_PUBLIC_NETWORKS || allowedIpsState == AllowedIpsState.CONTAINS_WILDCARD

    @get:Bindable
    val isExcludingPrivateIps: Boolean
        get() = allowedIpsState == AllowedIpsState.CONTAINS_PUBLIC_NETWORKS

    private constructor(parcel: Parcel) {
        allowedIps = parcel.readString() ?: ""
        endpoint = parcel.readString() ?: ""
        persistentKeepalive = parcel.readString() ?: ""
        preSharedKey = parcel.readString() ?: ""
        publicKey = parcel.readString() ?: ""
    }

    constructor(other: Peer) {
        allowedIps = Attribute.join(other.allowedIps)
        endpoint = other.endpoint.map { it.toString() }.orElse("")
        persistentKeepalive = other.persistentKeepalive.map { it.toString() }.orElse("")
        preSharedKey = other.preSharedKey.map { it.toBase64() }.orElse("")
        publicKey = other.publicKey.toBase64()
    }

    constructor()

    fun bind(owner: ConfigProxy) {
        val interfaze: InterfaceProxy = owner.`interface`
        val peers = owner.peers
        if (interfaceDnsListener == null) interfaceDnsListener = InterfaceDnsListener(this)
        interfaze.addOnPropertyChangedCallback(interfaceDnsListener!!)
        setInterfaceDns(interfaze.dnsServers)
        if (peerListListener == null) peerListListener = PeerListListener(this)
        peers.addOnListChangedCallback(peerListListener)
        setTotalPeers(peers.size)
        this.owner = owner
    }

    private fun calculateAllowedIpsState() {
        val newState: AllowedIpsState
        newState = if (totalPeers == 1) {
            // String comparison works because we only care if allowedIps is a superset of one of
            // the above sets of (valid) *networks*. We are not checking for a superset based on
            // the individual addresses in each set.
            val networkStrings: Collection<String> = getAllowedIpsSet()
            // A wildcard in either family means private IPs can still be excluded.
            if (networkStrings.containsAll(IPV4_WILDCARD) || networkStrings.containsAll(IPV6_WILDCARD))
                AllowedIpsState.CONTAINS_WILDCARD
            else if (networkStrings.containsAll(IPV4_PUBLIC_NETWORKS) || networkStrings.containsAll(IPV6_PUBLIC_NETWORKS))
                AllowedIpsState.CONTAINS_PUBLIC_NETWORKS
            else
                AllowedIpsState.OTHER
        } else {
            AllowedIpsState.INVALID
        }
        if (newState != allowedIpsState) {
            allowedIpsState = newState
            notifyPropertyChanged(BR.ableToExcludePrivateIps)
            notifyPropertyChanged(BR.excludingPrivateIps)
        }
    }

    override fun describeContents() = 0

    private fun getAllowedIpsSet() = setOf(*Attribute.split(allowedIps))

    // For each family, replace the first instance of the wildcard with its public network list, or vice versa.
    fun setExcludingPrivateIps(excludingPrivateIps: Boolean) {
        if (!isAbleToExcludePrivateIps || isExcludingPrivateIps == excludingPrivateIps) return
        val replacements = listOf(IPV4_WILDCARD to IPV4_PUBLIC_NETWORKS, IPV6_WILDCARD to IPV6_PUBLIC_NETWORKS)
            .map { (wildcard, public) -> if (excludingPrivateIps) wildcard to public else public to wildcard }
        val input: Collection<String> = getAllowedIpsSet()
        val output: MutableCollection<String?> = LinkedHashSet(input.size)
        val replaced = BooleanArray(replacements.size)
        for (network in input) {
            val family = replacements.indexOfFirst { it.first.contains(network) }
            if (family >= 0) {
                if (!replaced[family]) {
                    for (replacement in replacements[family].second) if (!output.contains(replacement)) output.add(replacement)
                    replaced[family] = true
                }
            } else if (!output.contains(network)) {
                output.add(network)
            }
        }
        // DNS servers only need to be handled specially when we're excluding private IPs.
        if (excludingPrivateIps) output.addAll(dnsRoutes) else output.removeAll(dnsRoutes)
        allowedIps = Attribute.join(output)
        allowedIpsState = if (excludingPrivateIps) AllowedIpsState.CONTAINS_PUBLIC_NETWORKS else AllowedIpsState.CONTAINS_WILDCARD
        notifyPropertyChanged(BR.allowedIps)
        notifyPropertyChanged(BR.excludingPrivateIps)
    }

    @Throws(BadConfigException::class)
    fun resolve(): Peer {
        val builder = Peer.Builder()
        if (allowedIps.isNotEmpty()) builder.parseAllowedIPs(allowedIps)
        if (endpoint.isNotEmpty()) builder.parseEndpoint(endpoint)
        if (persistentKeepalive.isNotEmpty()) builder.parsePersistentKeepalive(persistentKeepalive)
        if (preSharedKey.isNotEmpty()) builder.parsePreSharedKey(preSharedKey)
        if (publicKey.isNotEmpty()) builder.parsePublicKey(publicKey)
        return builder.build()
    }

    private fun setInterfaceDns(dnsServers: CharSequence) {
        val newDnsRoutes = Attribute.split(dnsServers)
            .filter { it.contains(":") || it.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$""")) }
            .map { if (it.contains(":")) "$it/128" else "$it/32" }
        if (allowedIpsState == AllowedIpsState.CONTAINS_PUBLIC_NETWORKS) {
            val input = getAllowedIpsSet()
            // Yes, this is quadratic in the number of DNS servers, but most users have 1 or 2.
            val output = input.filter { !dnsRoutes.contains(it) || newDnsRoutes.contains(it) }.plus(newDnsRoutes).distinct()
            // None of the public networks are /32s, so this cannot change the AllowedIPs state.
            allowedIps = Attribute.join(output)
            notifyPropertyChanged(BR.allowedIps)
        }
        dnsRoutes.clear()
        dnsRoutes.addAll(newDnsRoutes)
    }

    private fun setTotalPeers(totalPeers: Int) {
        if (this.totalPeers == totalPeers) return
        this.totalPeers = totalPeers
        calculateAllowedIpsState()
    }

    fun unbind() {
        if (owner == null) return
        val interfaze: InterfaceProxy = owner!!.`interface`
        val peers = owner!!.peers
        if (interfaceDnsListener != null) interfaze.removeOnPropertyChangedCallback(interfaceDnsListener!!)
        if (peerListListener != null) peers.removeOnListChangedCallback(peerListListener)
        peers.remove(this)
        setInterfaceDns("")
        setTotalPeers(0)
        owner = null
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(allowedIps)
        dest.writeString(endpoint)
        dest.writeString(persistentKeepalive)
        dest.writeString(preSharedKey)
        dest.writeString(publicKey)
    }

    private enum class AllowedIpsState {
        CONTAINS_PUBLIC_NETWORKS, CONTAINS_WILDCARD, INVALID, OTHER
    }

    private class InterfaceDnsListener constructor(peerProxy: PeerProxy) : OnPropertyChangedCallback() {
        private val weakPeerProxy: WeakReference<PeerProxy> = WeakReference(peerProxy)
        override fun onPropertyChanged(sender: Observable, propertyId: Int) {
            val peerProxy = weakPeerProxy.get()
            if (peerProxy == null) {
                sender.removeOnPropertyChangedCallback(this)
                return
            }
            // This shouldn't be possible, but try to avoid a ClassCastException anyway.
            if (sender !is InterfaceProxy) return
            if (!(propertyId == BR._all || propertyId == BR.dnsServers)) return
            peerProxy.setInterfaceDns(sender.dnsServers)
        }
    }

    private class PeerListListener(peerProxy: PeerProxy) : ObservableList.OnListChangedCallback<ObservableList<PeerProxy?>>() {
        private val weakPeerProxy: WeakReference<PeerProxy> = WeakReference(peerProxy)
        override fun onChanged(sender: ObservableList<PeerProxy?>) {
            val peerProxy = weakPeerProxy.get()
            if (peerProxy == null) {
                sender.removeOnListChangedCallback(this)
                return
            }
            peerProxy.setTotalPeers(sender.size)
        }

        override fun onItemRangeChanged(
            sender: ObservableList<PeerProxy?>,
            positionStart: Int, itemCount: Int
        ) {
            // Do nothing.
        }

        override fun onItemRangeInserted(
            sender: ObservableList<PeerProxy?>,
            positionStart: Int, itemCount: Int
        ) {
            onChanged(sender)
        }

        override fun onItemRangeMoved(
            sender: ObservableList<PeerProxy?>,
            fromPosition: Int, toPosition: Int,
            itemCount: Int
        ) {
            // Do nothing.
        }

        override fun onItemRangeRemoved(
            sender: ObservableList<PeerProxy?>,
            positionStart: Int, itemCount: Int
        ) {
            onChanged(sender)
        }
    }

    private class PeerProxyCreator : Parcelable.Creator<PeerProxy> {
        override fun createFromParcel(parcel: Parcel): PeerProxy {
            return PeerProxy(parcel)
        }

        override fun newArray(size: Int): Array<PeerProxy?> {
            return arrayOfNulls(size)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<PeerProxy> = PeerProxyCreator()
        private val IPV4_PUBLIC_NETWORKS = setOf(
            "0.0.0.0/5", "8.0.0.0/7", "11.0.0.0/8", "12.0.0.0/6", "16.0.0.0/4", "32.0.0.0/3",
            "64.0.0.0/2", "128.0.0.0/3", "160.0.0.0/5", "168.0.0.0/6", "172.0.0.0/12",
            "172.32.0.0/11", "172.64.0.0/10", "172.128.0.0/9", "173.0.0.0/8", "174.0.0.0/7",
            "176.0.0.0/4", "192.0.0.0/9", "192.128.0.0/11", "192.160.0.0/13", "192.169.0.0/16",
            "192.170.0.0/15", "192.172.0.0/14", "192.176.0.0/12", "192.192.0.0/10",
            "193.0.0.0/8", "194.0.0.0/7", "196.0.0.0/6", "200.0.0.0/5", "208.0.0.0/4"
        )
        private val IPV4_WILDCARD = setOf("0.0.0.0/0")
        // ::/0 minus the private ranges fc00::/7 (unique local) and fe80::/10 (link-local).
        private val IPV6_PUBLIC_NETWORKS = setOf(
            "::/1", "8000::/2", "c000::/3", "e000::/4", "f000::/5", "f800::/6",
            "fe00::/9", "fec0::/10", "ff00::/8"
        )
        private val IPV6_WILDCARD = setOf("::/0")
    }
}
