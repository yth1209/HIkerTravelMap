package com.main.htm.rest.record.dto

import com.main.htm.common.dto.Response
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path


interface RecordApiClient {
    @POST("record/start")
    suspend fun startRecord(
        @Body req: StartRecordReq
    ): Response

    @POST("record/data")
    suspend fun postData(
        @Body req: PostDataReq
    ): Response

    @PUT("record/{travelId}/end")
    suspend fun endRecord(
        @Path("travelId") travelId: String
    ): Response

    @GET("record/path/raw")
    suspend fun getRawPath(): GetRawPathsRes
}

val retrofit = Retrofit.Builder()
    .baseUrl("https://306e-116-125-129-89.ngrok-free.app/") // 반드시 '/'로 끝나야 함
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val recordApi = retrofit.create(RecordApiClient::class.java)