package com.guardian.mesh.ui

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Credential(
    val domain: String,
    val username: String,
    val serviceName: String,
    val password: String
) : Parcelable
