package com.example.helloworld.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

data class ScryfallCollectionRequest(
    val identifiers: List<CardIdentifier>
)

data class CardIdentifier(
    val name: String
)

data class ScryfallCollectionResponse(
    val data: List<ScryfallCard>
)

data class ScryfallCard(
    val name: String,
    @SerializedName("oracle_text") val oracleText: String?,
    @SerializedName("card_faces") val cardFaces: List<CardFace>?
)

data class CardFace(
    val name: String,
    @SerializedName("oracle_text") val oracleText: String?
)

interface ScryfallService {
    @POST("cards/collection")
    suspend fun getCollection(@Body request: ScryfallCollectionRequest): ScryfallCollectionResponse
}
