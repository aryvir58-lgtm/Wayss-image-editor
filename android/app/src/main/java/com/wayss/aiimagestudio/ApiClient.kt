package com.wayss.aiimagestudio

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {
    // Replace this with your deployed backend URL.
    // Keep the trailing slash.
    private const val BASE_URL = "https://YOUR-RAILWAY-BACKEND.up.railway.app/"

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}
