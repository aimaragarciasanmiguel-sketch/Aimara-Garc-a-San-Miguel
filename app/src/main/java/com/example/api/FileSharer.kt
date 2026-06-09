package com.example.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object FileSharer {
    private const val TAG = "FileSharer"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads a file to file.io and returns the download link.
     * file.io automatically deletes files after download and has default expiry.
     */
    suspend fun uploadFile(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            throw IllegalArgumentException("El archivo no existe.")
        }

        val mediaType = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf".toMediaType()
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document".toMediaType()
            else -> "application/octet-stream".toMediaType()
        }

        // We append the ?expires=1d parameter so it naturally expires and cleans up after 1 day.
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody(mediaType)
            )
            .build()

        val request = Request.Builder()
            .url("https://file.io/?expires=1d")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            Log.d(TAG, "file.io Response code: ${response.code}, body: $bodyString")

            if (!response.isSuccessful) {
                throw Exception("Error de respuesta del servidor (${response.code})")
            }

            val json = JSONObject(bodyString)
            if (json.optBoolean("success", false)) {
                return@withContext json.getString("link")
            } else {
                throw Exception(json.optString("message", "Error desconocido de file.io"))
            }
        }
    }
}
