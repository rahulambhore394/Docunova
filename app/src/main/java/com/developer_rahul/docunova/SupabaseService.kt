package com.developer_rahul.docunova
import retrofit2.http.*
import retrofit2.Response

interface SupabaseService {
    @POST("rest/v1/user")
    @Headers("Content-Type: application/json")
    suspend fun createUser(
        @Body user: User
    ): Response<Void>
}
