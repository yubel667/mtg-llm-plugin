package com.mtgllm.plugin.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class ScryfallCollectionRequest(
    val identifiers: List<CardIdentifier>
)

data class CardIdentifier(
    val name: String
)

data class ScryfallCollectionResponse(
    val data: List<ScryfallCard>,
    @SerializedName("not_found") val notFound: List<CardIdentifier>?
)

data class ScryfallCard(
    val name: String,
    @SerializedName("mana_cost") val manaCost: String?,
    @SerializedName("type_line") val typeLine: String?,
    @SerializedName("oracle_text") val oracleText: String?,
    val power: String?,
    val toughness: String?,
    @SerializedName("card_faces") val cardFaces: List<CardFace>?
)

data class CardFace(
    val name: String,
    @SerializedName("mana_cost") val manaCost: String?,
    @SerializedName("type_line") val typeLine: String?,
    @SerializedName("oracle_text") val oracleText: String?,
    val power: String?,
    val toughness: String?
)

data class ScryfallSearchResponse(
    @SerializedName("total_cards") val totalCards: Int,
    @SerializedName("has_more") val hasMore: Boolean,
    @SerializedName("next_page") val nextPage: String?,
    val data: List<ScryfallCard>
)

interface ScryfallService {
    @GET("cards/search")
    suspend fun search(@Query("q") query: String): ScryfallSearchResponse

    @POST("cards/collection")
    suspend fun getCollection(@Body request: ScryfallCollectionRequest): ScryfallCollectionResponse
}
