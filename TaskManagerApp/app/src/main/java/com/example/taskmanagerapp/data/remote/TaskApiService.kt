package com.example.taskmanagerapp.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface TaskApiService {
    @GET("tasks")
    suspend fun getDeltaTasks(@Query("since") since: Long? = null): Response<DeltaResponseDto>

    @POST("tasks/sync")
    suspend fun syncBatch(@Body request: SyncRequestDto): Response<SyncResponseDto>

    @POST("tasks")
    suspend fun upsertTask(@Body task: TaskDto): Response<TaskDto>

    @PUT("tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body task: TaskDto): Response<TaskDto>
}
