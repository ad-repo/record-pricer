package com.recordpricer.api

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface VisionApi {
    @POST("v1/images:annotate")
    suspend fun annotate(
        @Query("key") key: String,
        @Body request: VisionRequest
    ): VisionResponse
}

data class VisionRequest(val requests: List<AnnotateRequest>)
data class AnnotateRequest(val image: VisionImage, val features: List<VisionFeature>)
data class VisionImage(val content: String)
data class VisionFeature(val type: String, val maxResults: Int = 10)

data class VisionResponse(val responses: List<AnnotateResponse>?)
data class AnnotateResponse(
    val webDetection: WebDetection?,
    val textAnnotations: List<TextAnnotation>?
)
data class WebDetection(
    val webEntities: List<WebEntity>?,
    val bestGuessLabels: List<BestGuessLabel>?,
    val pagesWithMatchingImages: List<MatchingPage>?,
    val fullMatchingImages: List<MatchingImage>?
)
data class WebEntity(val description: String?, val score: Float?)
data class BestGuessLabel(val label: String?)
data class MatchingPage(val url: String?, val pageTitle: String?)
data class MatchingImage(val url: String?)
data class TextAnnotation(val description: String?)
