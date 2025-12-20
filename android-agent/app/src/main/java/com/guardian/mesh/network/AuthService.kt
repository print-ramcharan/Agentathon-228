package com.guardian.mesh.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

data class ChallengeResponse(val challenge: String)

data class VerifyRequest(
    val deviceId: String,
    val publicKey: String,
    val challenge: String,
    val signature: String,
    val riskScore: Float
)

data class AgentRequest(
    val requestId: String,
    val service: String,
    val source: String,
    val status: String,
    val timestamp: Long,
    val publicKey: String? = null // Added for E2EE
)

data class AgentResponse(
    val requestId: String,
    val credentials: String
)

interface AuthService {
    @POST("/auth/challenge")
    fun getChallenge(): Call<ChallengeResponse>

    @POST("/auth/verify")
    fun verify(@Body request: VerifyRequest): Call<Void>

    @POST("/agent/pending") // Using POST for simplicity as server accepts it
    fun getPendingRequests(): Call<List<AgentRequest>>

    @POST("/agent/respond")
    fun respondToRequest(@Body response: AgentResponse): Call<Void>
}
