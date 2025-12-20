package com.guardian.mesh.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {
    // Use 192.168.0.150 for physical device testing
    // private const val BASE_URL = "http://10.0.2.2:8080" // Emulator
    private const val BASE_URL = "https://unexempt-danial-unousted.ngrok-free.dev"

    val authService: AuthService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthService::class.java)
    }
}
