package com.playwe.playlist.data

import com.playwe.playlist.model.Chapter
import com.playwe.playlist.model.Playlist
import com.playwe.playlist.model.PlaylistType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object VideoRepository {

    const val DATA_URL = "https://cdn.jsdelivr.net/gh/Areo-RGB/data.json@main/data.json"

    suspend fun fetchPlaylists(url: String = DATA_URL): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12000
                readTimeout = 12000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "PlaylistPlayerAndroid/1.0")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val parsedPlaylists = parsePlaylistsJson(jsonText)
                if (parsedPlaylists.isNotEmpty()) {
                    Result.success(parsedPlaylists)
                } else {
                    Result.failure(Exception("Empty playlist list received"))
                }
            } else {
                Result.failure(Exception("HTTP ${connection.responseCode}: ${connection.responseMessage}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePlaylistsJson(jsonText: String): List<Playlist> {
        val list = mutableListOf<Playlist>()
        try {
            val jsonArray = JSONArray(jsonText)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val name = obj.getNullableString("name") ?: "Playlist ${i + 1}"
                val typeStr = obj.getNullableString("type") ?: "direct"
                val type = if (typeStr.equals("youtube", ignoreCase = true)) {
                    PlaylistType.YOUTUBE
                } else {
                    PlaylistType.DIRECT
                }
                val videoId = obj.getNullableString("videoId")
                val videoUrl = obj.getNullableString("videoUrl")
                val thumbnailUrl = obj.getNullableString("thumbnailUrl")

                val chaptersList = mutableListOf<Chapter>()
                val chaptersArray = obj.optJSONArray("chapters")
                if (chaptersArray != null) {
                    for (j in 0 until chaptersArray.length()) {
                        val chObj = chaptersArray.optJSONObject(j) ?: continue
                        val chName = chObj.getNullableString("name") ?: "Chapter ${j + 1}"
                        val startSec = chObj.optDouble("startSeconds", 0.0).toLong()
                        val endSec = chObj.optDouble("endSeconds", 0.0).toLong()
                        val chVideoUrl = chObj.getNullableString("videoUrl")
                        val chThumbUrl = chObj.getNullableString("thumbnailUrl")

                        val chId = chObj.getNullableString("id") ?: "ch_${name.hashCode()}_${chName.hashCode()}_${startSec}_$j"
                        chaptersList.add(
                            Chapter(
                                id = chId,
                                name = chName,
                                startSeconds = startSec,
                                endSeconds = endSec,
                                videoUrl = chVideoUrl,
                                thumbnailUrl = chThumbUrl
                            )
                        )
                    }
                }

                val plId = obj.getNullableString("id") ?: "pl_${name.hashCode()}_${videoId ?: videoUrl ?: i}"
                list.add(
                    Playlist(
                        id = plId,
                        name = name,
                        type = type,
                        videoId = videoId,
                        videoUrl = videoUrl,
                        thumbnailUrl = thumbnailUrl,
                        chapters = chaptersList
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun JSONObject.getNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key)
        return if (value.isBlank() || value == "null") null else value
    }
}
