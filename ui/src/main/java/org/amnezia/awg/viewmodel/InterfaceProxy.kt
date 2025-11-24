/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.viewmodel

import android.os.Parcel
import android.os.Parcelable
import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import androidx.databinding.ObservableArrayList
import androidx.databinding.ObservableList
import org.amnezia.awg.BR
import org.amnezia.awg.config.Attribute
import org.amnezia.awg.config.BadConfigException
import org.amnezia.awg.config.Interface
import org.amnezia.awg.crypto.Key
import org.amnezia.awg.crypto.KeyFormatException
import org.amnezia.awg.crypto.KeyPair

class InterfaceProxy : BaseObservable, Parcelable {
    @get:Bindable
    val excludedApplications: ObservableList<String> = ObservableArrayList()

    @get:Bindable
    val includedApplications: ObservableList<String> = ObservableArrayList()

    @get:Bindable
    var addresses: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.addresses)
        }

    @get:Bindable
    var dnsServers: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.dnsServers)
        }

    @get:Bindable
    var listenPort: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.listenPort)
        }

    @get:Bindable
    var mtu: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.mtu)
        }

    @get:Bindable
    var junkPacketCount: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.junkPacketCount)
        }

    @get:Bindable
    var junkPacketMinSize: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.junkPacketMinSize)
        }

    @get:Bindable
    var junkPacketMaxSize: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.junkPacketMaxSize)
        }

    @get:Bindable
    var initPacketJunkSize: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.initPacketJunkSize)
        }

    @get:Bindable
    var responsePacketJunkSize: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.responsePacketJunkSize)
        }

    @get:Bindable
    var cookieReplyPacketJunkSize: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.cookieReplyPacketJunkSize)
        }

    @get:Bindable
    var transportPacketJunkSize: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.transportPacketJunkSize)
        }

    @get:Bindable
    var initPacketMagicHeader: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.initPacketMagicHeader)
        }

    @get:Bindable
    var responsePacketMagicHeader: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.responsePacketMagicHeader)
        }

    @get:Bindable
    var underloadPacketMagicHeader: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.underloadPacketMagicHeader)
        }

    @get:Bindable
    var transportPacketMagicHeader: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.transportPacketMagicHeader)
        }

    @get:Bindable
    var specialJunkI1: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.specialJunkI1)
        }

    @get:Bindable
    var specialJunkI2: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.specialJunkI2)
        }

    @get:Bindable
    var specialJunkI3: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.specialJunkI3)
        }

    @get:Bindable
    var specialJunkI4: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.specialJunkI4)
        }

    @get:Bindable
    var specialJunkI5: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.specialJunkI5)
        }

    @get:Bindable
    var privateKey: String = ""
        set(value) {
            field = value
            notifyPropertyChanged(BR.privateKey)
            notifyPropertyChanged(BR.publicKey)
        }

    @get:Bindable
    val publicKey: String
        get() = try {
            KeyPair(Key.fromBase64(privateKey)).publicKey.toBase64()
        } catch (ignored: KeyFormatException) {
            ""
        }

    private constructor(parcel: Parcel) {
        addresses = parcel.readString() ?: ""
        dnsServers = parcel.readString() ?: ""
        parcel.readStringList(excludedApplications)
        parcel.readStringList(includedApplications)
        listenPort = parcel.readString() ?: ""
        mtu = parcel.readString() ?: ""
        junkPacketCount = parcel.readString() ?: ""
        junkPacketMinSize = parcel.readString() ?: ""
        junkPacketMaxSize = parcel.readString() ?: ""
        initPacketJunkSize = parcel.readString() ?: ""
        responsePacketJunkSize = parcel.readString() ?: ""
        cookieReplyPacketJunkSize = parcel.readString() ?: ""
        transportPacketJunkSize = parcel.readString() ?: ""
        initPacketMagicHeader = parcel.readString() ?: ""
        responsePacketMagicHeader = parcel.readString() ?: ""
        underloadPacketMagicHeader = parcel.readString() ?: ""
        transportPacketMagicHeader = parcel.readString() ?: ""
        specialJunkI1 = parcel.readString() ?: ""
        specialJunkI2 = parcel.readString() ?: ""
        specialJunkI3 = parcel.readString() ?: ""
        specialJunkI4 = parcel.readString() ?: ""
        specialJunkI5 = parcel.readString() ?: ""
        privateKey = parcel.readString() ?: ""
    }

    constructor(other: Interface) {
        addresses = Attribute.join(other.addresses)
        val dnsServerStrings = other.dnsServers.map { it.hostAddress }.plus(other.dnsSearchDomains)
        dnsServers = Attribute.join(dnsServerStrings)
        excludedApplications.addAll(other.excludedApplications)
        includedApplications.addAll(other.includedApplications)
        listenPort = other.listenPort.map { it.toString() }.orElse("")
        mtu = other.mtu.map { it.toString() }.orElse("")
        junkPacketCount = other.junkPacketCount.map { it.toString() }.orElse("")
        junkPacketMinSize = other.junkPacketMinSize.map { it.toString() }.orElse("")
        junkPacketMaxSize = other.junkPacketMaxSize.map { it.toString() }.orElse("")
        initPacketJunkSize = other.initPacketJunkSize.map { it.toString() }.orElse("")
        responsePacketJunkSize = other.responsePacketJunkSize.map { it.toString() }.orElse("")
        cookieReplyPacketJunkSize = other.cookieReplyPacketJunkSize.map { it.toString() }.orElse("")
        transportPacketJunkSize = other.transportPacketJunkSize.map { it.toString() }.orElse("")
        initPacketMagicHeader = other.initPacketMagicHeader.orElse("")
        responsePacketMagicHeader = other.responsePacketMagicHeader.orElse("")
        underloadPacketMagicHeader = other.underloadPacketMagicHeader.orElse("")
        transportPacketMagicHeader = other.transportPacketMagicHeader.orElse("")
        specialJunkI1 = other.specialJunkI1.orElse("")
        specialJunkI2 = other.specialJunkI2.orElse("")
        specialJunkI3 = other.specialJunkI3.orElse("")
        specialJunkI4 = other.specialJunkI4.orElse("")
        specialJunkI5 = other.specialJunkI5.orElse("")
        val keyPair = other.keyPair
        privateKey = keyPair.privateKey.toBase64()
    }

    constructor()

    override fun describeContents() = 0

    fun generateKeyPair() {
        val keyPair = KeyPair()
        privateKey = keyPair.privateKey.toBase64()
        notifyPropertyChanged(BR.privateKey)
        notifyPropertyChanged(BR.publicKey)
    }

    @Throws(BadConfigException::class)
    fun resolve(): Interface {
        val builder = Interface.Builder()
        if (addresses.isNotEmpty()) builder.parseAddresses(addresses)
        if (dnsServers.isNotEmpty()) builder.parseDnsServers(dnsServers)
        if (excludedApplications.isNotEmpty()) builder.excludeApplications(excludedApplications)
        if (includedApplications.isNotEmpty()) builder.includeApplications(includedApplications)
        if (listenPort.isNotEmpty()) builder.parseListenPort(listenPort)
        if (mtu.isNotEmpty()) builder.parseMtu(mtu)
        if (junkPacketCount.isNotEmpty()) builder.parseJunkPacketCount(junkPacketCount)
        if (junkPacketMinSize.isNotEmpty()) builder.parseJunkPacketMinSize(junkPacketMinSize)
        if (junkPacketMaxSize.isNotEmpty()) builder.parseJunkPacketMaxSize(junkPacketMaxSize)
        if (initPacketJunkSize.isNotEmpty()) builder.parseInitPacketJunkSize(initPacketJunkSize)
        if (responsePacketJunkSize.isNotEmpty()) builder.parseResponsePacketJunkSize(responsePacketJunkSize)
        if (cookieReplyPacketJunkSize.isNotEmpty()) builder.parseCookieReplyPacketJunkSize(cookieReplyPacketJunkSize)
        if (transportPacketJunkSize.isNotEmpty()) builder.parseTransportPacketJunkSize(transportPacketJunkSize)
        if (initPacketMagicHeader.isNotEmpty()) builder.parseInitPacketMagicHeader(initPacketMagicHeader)
        if (responsePacketMagicHeader.isNotEmpty()) builder.parseResponsePacketMagicHeader(responsePacketMagicHeader)
        if (underloadPacketMagicHeader.isNotEmpty()) builder.parseUnderloadPacketMagicHeader(underloadPacketMagicHeader)
        if (transportPacketMagicHeader.isNotEmpty()) builder.parseTransportPacketMagicHeader(transportPacketMagicHeader)
        if (specialJunkI1.isNotEmpty()) builder.parseSpecialJunkI1(specialJunkI1)
        if (specialJunkI2.isNotEmpty()) builder.parseSpecialJunkI2(specialJunkI2)
        if (specialJunkI3.isNotEmpty()) builder.parseSpecialJunkI3(specialJunkI3)
        if (specialJunkI4.isNotEmpty()) builder.parseSpecialJunkI4(specialJunkI4)
        if (specialJunkI5.isNotEmpty()) builder.parseSpecialJunkI5(specialJunkI5)
        if (privateKey.isNotEmpty()) builder.parsePrivateKey(privateKey)
        return builder.build()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(addresses)
        dest.writeString(dnsServers)
        dest.writeStringList(excludedApplications)
        dest.writeStringList(includedApplications)
        dest.writeString(listenPort)
        dest.writeString(mtu)
        dest.writeString(junkPacketCount)
        dest.writeString(junkPacketMinSize)
        dest.writeString(junkPacketMaxSize)
        dest.writeString(initPacketJunkSize)
        dest.writeString(responsePacketJunkSize)
        dest.writeString(cookieReplyPacketJunkSize)
        dest.writeString(transportPacketJunkSize)
        dest.writeString(initPacketMagicHeader)
        dest.writeString(responsePacketMagicHeader)
        dest.writeString(underloadPacketMagicHeader)
        dest.writeString(transportPacketMagicHeader)
        dest.writeString(specialJunkI1)
        dest.writeString(specialJunkI2)
        dest.writeString(specialJunkI3)
        dest.writeString(specialJunkI4)
        dest.writeString(specialJunkI5)
        dest.writeString(privateKey)
    }

    private class InterfaceProxyCreator : Parcelable.Creator<InterfaceProxy> {
        override fun createFromParcel(parcel: Parcel): InterfaceProxy {
            return InterfaceProxy(parcel)
        }

        override fun newArray(size: Int): Array<InterfaceProxy?> {
            return arrayOfNulls(size)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<InterfaceProxy> = InterfaceProxyCreator()
    }
}
