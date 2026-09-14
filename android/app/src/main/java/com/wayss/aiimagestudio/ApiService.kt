package com.wayss.aiimagestudio

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class EditResponse(
    val success: Boolean,
    val image: String?,
    val error: String?
)

interface ApiService {
    @Multipart
    @POST("api/edit-image")
    suspend fun editImage(
        @Part image: MultipartBody.Part,
        @Part("prompt") prompt: RequestBody
    ): Response<EditResponse>
}
