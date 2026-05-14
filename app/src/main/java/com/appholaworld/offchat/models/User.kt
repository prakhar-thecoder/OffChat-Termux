package com.appholaworld.offchat.models

import android.os.Parcel
import android.os.Parcelable

data class User(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val deviceName: String,
    val isProfileCompleted: Boolean = false,
    val googleAdvId: String = "",
    val deviceId: String = "",
    val ipAddress: String = ""
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(phoneNumber)
        parcel.writeString(deviceName)
        parcel.writeByte(if (isProfileCompleted) 1 else 0)
        parcel.writeString(googleAdvId)
        parcel.writeString(deviceId)
        parcel.writeString(ipAddress)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<User> {
        override fun createFromParcel(parcel: Parcel): User = User(parcel)
        override fun newArray(size: Int): Array<User?> = arrayOfNulls(size)
    }
}
