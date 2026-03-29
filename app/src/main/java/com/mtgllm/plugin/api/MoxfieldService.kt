package com.mtgllm.plugin.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path

data class MoxfieldDeckResponse(
    val name: String,
    val commanders: Map<String, MoxfieldCard>?,
    val companions: Map<String, MoxfieldCard>?,
    val mainboard: Map<String, MoxfieldCard>,
    val sideboard: Map<String, MoxfieldCard>?,
    val maybeboard: Map<String, MoxfieldCard>?
)

data class MoxfieldCard(
    val quantity: Int,
    val card: MoxfieldCardDetails
)

data class MoxfieldCardDetails(
    val name: String
)

interface MoxfieldService {
    @GET("v2/decks/all/{deckId}")
    suspend fun getDeck(@Path("deckId") deckId: String): MoxfieldDeckResponse
}
