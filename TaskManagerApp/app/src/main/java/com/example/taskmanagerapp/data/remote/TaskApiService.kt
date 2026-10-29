package com.example.taskmanagerapp.data.remote

import retrofit2.Response
import retrofit2.http.*

interface TaskApiService {
    @GET("tasks")
    suspend fun getDeltaTasks(@Query("updatedAfter") since: Long = 0): Response<DeltaResponseDto>

    @PUT("tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body task: TaskDto): Response<TaskDto>

    @POST("admin/simulate-edit/{id}")
    suspend fun simulateEdit(@Path("id") id: String): Response<TaskDto>

    @POST("admin/chaos")
    suspend fun chaos(@Query("failRate") failRate: Double, @Query("delayMs") delayMs: Long): Response<Unit>
}
