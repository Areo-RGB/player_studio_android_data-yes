package com.playwe.playlist.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
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
    private const val PREFERENCES_NAME = "video_storage"
    private const val LOCAL_FOLDER_URI_KEY = "local_folder_uri"
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp")

    suspend fun fetchPlaylists(url: String = DATA_URL): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "PlaylistPlayerAndroid/1.0")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val parsedPlaylists = parsePlaylistsJson(jsonText)
                if (parsedPlaylists.isNotEmpty()) {
                    return@withContext Result.success(parsedPlaylists)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback to local default JSON (R2 videos) if network fetch fails
        val fallback = parsePlaylistsJson(FALLBACK_JSON)
        if (fallback.isNotEmpty()) {
            Result.success(fallback)
        } else {
            Result.failure(Exception("Failed to load playlists"))
        }
    }

    suspend fun fetchPlaylistsWithLocal(context: Context, url: String = DATA_URL): Result<List<Playlist>> {
        val remoteResult = fetchPlaylists(url)
        val savedFolderUri = getSavedLocalFolderUri(context)
            ?: return remoteResult
        val localResult = loadLocalPlaylists(context, savedFolderUri)

        return when {
            remoteResult.isSuccess -> Result.success(
                remoteResult.getOrThrow() + localResult.getOrElse { emptyList() }
            )
            localResult.isSuccess -> localResult
            else -> remoteResult
        }
    }

    fun getSavedLocalFolderUri(context: Context): Uri? {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(LOCAL_FOLDER_URI_KEY, null)
            ?.let(Uri::parse)
    }

    fun saveLocalFolderUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(LOCAL_FOLDER_URI_KEY, uri.toString())
            .apply()
    }

    suspend fun loadLocalPlaylists(context: Context, treeUri: Uri): Result<List<Playlist>> =
        withContext(Dispatchers.IO) {
            try {
                val rootEntries = queryChildren(context, treeUri)
                val playlists = rootEntries
                    .filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
                    .sortedBy { it.name.lowercase() }
                    .mapNotNull { folder ->
                        val chapters = queryChildren(context, folder.uri)
                            .filter(::isVideo)
                            .sortedBy { it.name.lowercase() }
                            .mapIndexed { index, video ->
                                Chapter(
                                    id = "local_${video.documentId}",
                                    name = video.name.substringBeforeLast('.', video.name),
                                    startSeconds = 0,
                                    endSeconds = readDurationSeconds(context, video.uri),
                                    localPath = video.uri.toString()
                                )
                            }
                        if (chapters.isEmpty()) {
                            null
                        } else {
                            Playlist(
                                id = "local_${folder.documentId}",
                                name = folder.name,
                                type = PlaylistType.DIRECT,
                                chapters = chapters
                            )
                        }
                    }
                    .toMutableList()

                val rootVideos = rootEntries
                    .filter(::isVideo)
                    .sortedBy { it.name.lowercase() }
                    .map { video ->
                        Chapter(
                            id = "local_${video.documentId}",
                            name = video.name.substringBeforeLast('.', video.name),
                            startSeconds = 0,
                            endSeconds = readDurationSeconds(context, video.uri),
                            localPath = video.uri.toString()
                        )
                    }
                if (rootVideos.isNotEmpty()) {
                    playlists += Playlist(
                        id = "local_root",
                        name = "Videos",
                        type = PlaylistType.DIRECT,
                        chapters = rootVideos
                    )
                }

                Result.success(playlists)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private data class DocumentEntry(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val uri: Uri
    )

    private fun queryChildren(context: Context, parentUri: Uri): List<DocumentEntry> {
        val documentId = DocumentsContract.getDocumentId(parentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, documentId)
        val entries = mutableListOf<DocumentEntry>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                val mimeType = cursor.getString(mimeColumn) ?: ""
                entries += DocumentEntry(
                    documentId = childId,
                    name = name,
                    mimeType = mimeType,
                    uri = DocumentsContract.buildDocumentUriUsingTree(parentUri, childId)
                )
            }
        }
        return entries
    }

    private fun isVideo(entry: DocumentEntry): Boolean {
        return entry.mimeType.startsWith("video/") ||
            entry.name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS
    }

    private fun readDurationSeconds(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L) / 1000L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
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

    private val FALLBACK_JSON = """
    [
      {
        "name": "FIFA+",
        "type": "direct",
        "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/6._Running_-_Quick_Forwards_Backwards_q7stn3.jpg",
        "chapters": [
          {
            "name": "1. Running – Straight Ahead",
            "startSeconds": 0.0,
            "endSeconds": 17.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/1._Running_-_Straight_Ahead_odwbwn.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/1._Running_-_Straight_Ahead_odwbwn.jpg"
          },
          {
            "name": "1. Running – Straight Ahead (h.265)",
            "startSeconds": 0.0,
            "endSeconds": 17.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/1._Running_-_Straight_Ahead_h265_vzn9dc.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/1._Running_-_Straight_Ahead_h265_vzn9dc.jpg"
          },
          {
            "name": "2. Running – Hip Out",
            "startSeconds": 0.0,
            "endSeconds": 26.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/2._Running_-_Hip_Out_bpo3mt.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/2._Running_-_Hip_Out_bpo3mt.jpg"
          },
          {
            "name": "3. Running – Hip In",
            "startSeconds": 0.0,
            "endSeconds": 28.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/3._Running_-_Hip_In_syffjr.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/3._Running_-_Hip_In_syffjr.jpg"
          },
          {
            "name": "4. Running – Circling Partner",
            "startSeconds": 0.0,
            "endSeconds": 39.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/4._Running_-_Circling_Partner_jas1zq.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/4._Running_-_Circling_Partner_jas1zq.jpg"
          },
          {
            "name": "5. Running – Shoulder Contact",
            "startSeconds": 0.0,
            "endSeconds": 34.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/5._Running_-_Shoulder_Contact_td0urf.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/5._Running_-_Shoulder_Contact_td0urf.jpg"
          },
          {
            "name": "6. Running – Quick Forwards Backwards",
            "startSeconds": 0.0,
            "endSeconds": 52.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/6._Running_-_Quick_Forwards_Backwards_q7stn3.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/6._Running_-_Quick_Forwards_Backwards_q7stn3.jpg"
          },
          {
            "name": "7. The Bench – Static – Level 1",
            "startSeconds": 0.0,
            "endSeconds": 11.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/7._The_Bench_-_Static_-_Level_1_bx31xq.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/7._The_Bench_-_Static_-_Level_1_bx31xq.jpg"
          },
          {
            "name": "7. The Bench – Alternate Legs – Level 2",
            "startSeconds": 0.0,
            "endSeconds": 15.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/7._The_Bench_-_Alternate_Legs_-_Level_2_cxkow2.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/7._The_Bench_-_Alternate_Legs_-_Level_2_cxkow2.jpg"
          },
          {
            "name": "7. The Bench – One-Leg Lift Hold – Level 3",
            "startSeconds": 0.0,
            "endSeconds": 21.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/7._The_Bench_-_One_Leg_Lift_Hold_-_Level_3_shksc2.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/7._The_Bench_-_One_Leg_Lift_Hold_-_Level_3_shksc2.jpg"
          },
          {
            "name": "8. Sideways Bench – Static – Level 1",
            "startSeconds": 0.0,
            "endSeconds": 14.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/8._Sideways_Bench_-_Static_-_Level_1_zzefp4.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/8._Sideways_Bench_-_Static_-_Level_1_zzefp4.jpg"
          },
          {
            "name": "8. Sideways Bench – Raise Lower Hip – Level 2",
            "startSeconds": 0.0,
            "endSeconds": 12.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/8._Sideways_Bench_-_Raise_Lower_Hip_-_Level_2_ahxikr.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/8._Sideways_Bench_-_Raise_Lower_Hip_-_Level_2_ahxikr.jpg"
          },
          {
            "name": "8. Sideways Bench – With Leg Lift – Level 3",
            "startSeconds": 0.0,
            "endSeconds": 12.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/8._Sideways_Bench_-_With_Leg_Lift_-_Level_3_kspni5.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/8._Sideways_Bench_-_With_Leg_Lift_-_Level_3_kspni5.jpg"
          },
          {
            "name": "9. Hamstrings – Beginner – Level 1",
            "startSeconds": 0.0,
            "endSeconds": 19.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/9._Hamstrings_-_Beginner_-_Level_1_bvwr2t.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/9._Hamstrings_-_Beginner_-_Level_1_bvwr2t.jpg"
          },
          {
            "name": "9. Hamstrings – Intermediate – Level 2",
            "startSeconds": 0.0,
            "endSeconds": 19.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/9._Hamstrings_-_Intermediate_-_Level_2_az0fiv.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/9._Hamstrings_-_Intermediate_-_Level_2_az0fiv.jpg"
          },
          {
            "name": "9. Hamstrings – Advanced – Level 3",
            "startSeconds": 0.0,
            "endSeconds": 17.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/9._Hamstrings_-_Advanced_-_Level_3_z0kpgc.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/9._Hamstrings_-_Advanced_-_Level_3_z0kpgc.jpg"
          },
          {
            "name": "10. Single-Leg Stance – Hold the Ball – Level 1",
            "startSeconds": 0.0,
            "endSeconds": 13.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/10._Single-Leg_Stance_-_Hold_the_Ball_-_Level_1_l32se9.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/10._Single-Leg_Stance_-_Hold_the_Ball_-_Level_1_l32se9.jpg"
          },
          {
            "name": "10. Single-Leg Stance – Throwing the Ball – Level 2",
            "startSeconds": 0.0,
            "endSeconds": 30.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/10._Single-Leg_Stance_-_Throwing_the_Ball_-_Level_2_s6mvck.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/10._Single-Leg_Stance_-_Throwing_the_Ball_-_Level_2_s6mvck.jpg"
          },
          {
            "name": "10. Single-Leg Stance – Test Your Partner – Level 3",
            "startSeconds": 0.0,
            "endSeconds": 19.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/10._Single-Leg_Stance_-_Test_Your_Partner_-_Level_3_nwiptt.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/10._Single-Leg_Stance_-_Test_Your_Partner_-_Level_3_nwiptt.jpg"
          },
          {
            "name": "11. Squats – With Toe Raise – Level 1",
            "startSeconds": 0.0,
            "endSeconds": 18.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/11._Squats_-_With_Toe_Raise_-_Level_1_pt3qu3.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/11._Squats_-_With_Toe_Raise_-_Level_1_pt3qu3.jpg"
          },
          {
            "name": "11. Squats – Walking Lunges – Level 2",
            "startSeconds": 0.0,
            "endSeconds": 13.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/11._Squats_-_Walking_Lunges_-_Level_2_b5oxaf.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/11._Squats_-_Walking_Lunges_-_Level_2_b5oxaf.jpg"
          },
          {
            "name": "11. Squats – One-Leg Squats – Level 3",
            "startSeconds": 0.0,
            "endSeconds": 21.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/11._Squats_-_One-Leg_Squats_-_Level_3_fvihr2.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/11._Squats_-_One-Leg_Squats_-_Level_3_fvihr2.jpg"
          },
          {
            "name": "12. Jumping – Vertical Jumps – Level 1",
            "startSeconds": 0.0,
            "endSeconds": 15.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/12._Jumping_-_Vertical_Jumps_-_Level_1_tar7i5.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/12._Jumping_-_Vertical_Jumps_-_Level_1_tar7i5.jpg"
          },
          {
            "name": "12. Jumping – Lateral Jumps – Level 2",
            "startSeconds": 0.0,
            "endSeconds": 17.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/12._Jumping_-_Lateral_Jumps_-_Level_2_t0w5qi.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/12._Jumping_-_Lateral_Jumps_-_Level_2_t0w5qi.jpg"
          },
          {
            "name": "12. Jumping – Box Jumps – Level 3",
            "startSeconds": 0.0,
            "endSeconds": 23.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/12._Jumping_-_Box_Jumps_-_Level_3_k76ws6.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/12._Jumping_-_Box_Jumps_-_Level_3_k76ws6.jpg"
          },
          {
            "name": "13. Running – Across the Pitch",
            "startSeconds": 0.0,
            "endSeconds": 17.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/13._Running_-_Across_the_Pitch_fae0gp.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/13._Running_-_Across_the_Pitch_fae0gp.jpg"
          },
          {
            "name": "14. Running – Bounding",
            "startSeconds": 0.0,
            "endSeconds": 19.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/14._Running_-_Bounding_quslql.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/14._Running_-_Bounding_quslql.jpg"
          },
          {
            "name": "15. Running – Plant & Cut",
            "startSeconds": 0.0,
            "endSeconds": 21.0,
            "videoUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/15._Running_-_Plant_Cut_gwmoqd.mp4",
            "thumbnailUrl": "https://res.cloudinary.com/dds2p6fmc/video/upload/so_0/15._Running_-_Plant_Cut_gwmoqd.jpg"
          }
        ]
      },
      {
        "name": "10 Fast Feet Exercises",
        "type": "direct",
        "videoId": "WwOYqwSEb5A",
        "thumbnailUrl": "https://img.youtube.com/vi/WwOYqwSEb5A/hqdefault.jpg",
        "chapters": [
          {
            "name": "1. Hopscotch",
            "startSeconds": 0.0,
            "endSeconds": 42.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/10-Fast-Feet-Exercises/01-1.-Hopscotch-WwOYqwSEb5A-kf2.mp4"
          },
          {
            "name": "2. Diagonal Forwards Backwards",
            "startSeconds": 0.0,
            "endSeconds": 44.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/10-Fast-Feet-Exercises/02-2.-Diagonal-Forwards-Backwards-WwOYqwSEb5A-kf2.mp4"
          },
          {
            "name": "3. Inside Outside Forwards",
            "startSeconds": 0.0,
            "endSeconds": 49.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/10-Fast-Feet-Exercises/03-3.-Inside-Outside-Forwards-WwOYqwSEb5A-kf2.mp4"
          },
          {
            "name": "4. Inside Outside Across",
            "startSeconds": 0.0,
            "endSeconds": 38.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/10-Fast-Feet-Exercises/04-4.-Inside-Outside-Across-WwOYqwSEb5A-kf2.mp4"
          },
          {
            "name": "5. Crossover Shuffle",
            "startSeconds": 0.0,
            "endSeconds": 40.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/10-Fast-Feet-Exercises/05-5.-Crossover-Shuffle-WwOYqwSEb5A-kf2.mp4"
          },
          {
            "name": "6. Behind Foot Inside Outside",
            "startSeconds": 0.0,
            "endSeconds": 38.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/10-Fast-Feet-Exercises/06-6.-Behind-Foot-Inside-Outside-WwOYqwSEb5A-kf2.mp4"
          },
          {
            "name": "7. Behind Foot Inside Outside Across",
            "startSeconds": 0.0,
            "endSeconds": 42.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/10-Fast-Feet-Exercises/07-7.-Behind-Foot-Inside-Outside-Across-WwOYqwSEb5A-kf2.mp4"
          },
          {
            "name": "8. Advanced Hopscotch",
            "startSeconds": 0.0,
            "endSeconds": 61.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/10-Fast-Feet-Exercises/08-8.-Advanced-Hopscotch-WwOYqwSEb5A-kf2.mp4"
          },
          {
            "name": "9. Inside Outside Crossovers",
            "startSeconds": 0.0,
            "endSeconds": 45.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/10-Fast-Feet-Exercises/09-9.-Inside-Outside-Crossovers-WwOYqwSEb5A-kf2.mp4"
          },
          {
            "name": "10. Footwork Combo",
            "startSeconds": 0.0,
            "endSeconds": 51.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/10-Fast-Feet-Exercises/10-10.-Footwork-Combo-WwOYqwSEb5A-kf2.mp4"
          }
        ]
      },
      {
        "name": "Ball Mastery List 1",
        "type": "direct",
        "videoId": "dRS5EgJp-98",
        "thumbnailUrl": "https://img.youtube.com/vi/dRS5EgJp-98/hqdefault.jpg",
        "chapters": [
          {
            "name": "1. Inside Outside Single Leg Two Touch",
            "startSeconds": 55.0,
            "endSeconds": 106.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/Ball_Mastery_List_1/01_1._Inside_Outside_Single_Leg_Two_Touch.mp4"
          },
          {
            "name": "2. Inside Outside Single Leg One Touch",
            "startSeconds": 106.0,
            "endSeconds": 147.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/Ball_Mastery_List_1/02_2._Inside_Outside_Single_Leg_One_Touch.mp4"
          },
          {
            "name": "3. Inside Outside Both Feet",
            "startSeconds": 147.0,
            "endSeconds": 188.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/Ball_Mastery_List_1/03_3._Inside_Outside_Both_Feet.mp4"
          },
          {
            "name": "4. Inside Inside (La Croqueta action)",
            "startSeconds": 188.0,
            "endSeconds": 229.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/Ball_Mastery_List_1/04_4._Inside_Inside_La_Croqueta_action_.mp4"
          },
          {
            "name": "5. Sole Rolls",
            "startSeconds": 229.0,
            "endSeconds": 269.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/Ball_Mastery_List_1/05_5._Sole_Rolls.mp4"
          },
          {
            "name": "6. Pull and Push (V-Pattern)",
            "startSeconds": 269.0,
            "endSeconds": 321.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/Ball_Mastery_List_1/06_6._Pull_and_Push_V-Pattern_.mp4"
          },
          {
            "name": "7. Inside Sole (Tap Tap Roll)",
            "startSeconds": 321.0,
            "endSeconds": 373.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/Ball_Mastery_List_1/07_7._Inside_Sole_Tap_Tap_Roll_.mp4"
          },
          {
            "name": "8. Inside Outside Sole Single Leg",
            "startSeconds": 373.0,
            "endSeconds": 418.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/Ball_Mastery_List_1/08_8._Inside_Outside_Sole_Single_Leg.mp4"
          },
          {
            "name": "9. Inside Inside Outside",
            "startSeconds": 418.0,
            "endSeconds": 468.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/Ball_Mastery_List_1/09_9._Inside_Inside_Outside.mp4"
          },
          {
            "name": "10. La Croqueta Outside",
            "startSeconds": 468.0,
            "endSeconds": 522.0,
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/Ball_Mastery_List_1/10_10._La_Croqueta_Outside.mp4"
          }
        ]
      },
      {
        "name": "5 Easy Juggling Skills",
        "type": "direct",
        "thumbnailUrl": "https://img.youtube.com/vi/xSpvUfTBWx8/hqdefault.jpg",
        "chapters": [
          {
            "name": "1. Toe Bounce",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/5_Easy_Juggling_Skills/01_1._Toe_Bounce.mp4"
          },
          {
            "name": "2. Half Around The World",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/5_Easy_Juggling_Skills/02_2._Half_Around_The_World.mp4"
          },
          {
            "name": "3. Crossover",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/5_Easy_Juggling_Skills/03_3._Crossover.mp4"
          },
          {
            "name": "4. Heel Juggling",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/5_Easy_Juggling_Skills/04_4._Heel_Juggling.mp4"
          },
          {
            "name": "5. Slap Juggling",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/5_Easy_Juggling_Skills/05_5._Slap_Juggling.mp4"
          }
        ]
      },
      {
        "name": "20 Fast Feet Exercises",
        "type": "direct",
        "thumbnailUrl": "https://img.youtube.com/vi/GfceBvZjuPQ/hqdefault.jpg",
        "chapters": [
          {
            "name": "1. Stationary Fast Feet",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/01_1._Stationary_Fast_Feet.mp4"
          },
          {
            "name": "2. Forwards - Backwards",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/02_2._Forwards_-_Backwards.mp4"
          },
          {
            "name": "3. Side To Side",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/03_3._Side_To_Side.mp4"
          },
          {
            "name": "4. Side To Side With Step",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/04_4._Side_To_Side_With_Step.mp4"
          },
          {
            "name": "5. Forwards - Backwards Hops",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/05_5._Forwards_-_Backwards_Hops.mp4"
          },
          {
            "name": "6. Lateral Hops",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/06_6._Lateral_Hops.mp4"
          },
          {
            "name": "7. Crossover",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/07_7._Crossover.mp4"
          },
          {
            "name": "8. Crossover With Step",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/08_8._Crossover_With_Step.mp4"
          },
          {
            "name": "9. Reverse Crossover",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/09_9._Reverse_Crossover.mp4"
          },
          {
            "name": "10. Reverse Crossover With Step",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/10_10._Reverse_Crossover_With_Step.mp4"
          },
          {
            "name": "11. In - Out",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/11_11._In_-_Out.mp4"
          },
          {
            "name": "12. Forwards-Backwards-Lateral In - Out",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/12_12._Forwards-Backwards-Lateral_In_-_Out.mp4"
          },
          {
            "name": "13. Single Leg Forwards - Lateral",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/13_13._Single_Leg_Forwards_-_Lateral.mp4"
          },
          {
            "name": "14. Around The Clock",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/14_14._Around_The_Clock.mp4"
          },
          {
            "name": "15. Hop Scotch",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/15_15._Hop_Scotch.mp4"
          },
          {
            "name": "16. Over And Around",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/16_16._Over_And_Around.mp4"
          },
          {
            "name": "17. Shuffle To Lateral Bound",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/17_17._Shuffle_To_Lateral_Bound.mp4"
          },
          {
            "name": "18. Double Forwards - Backwards",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/18_18._Double_Forwards_-_Backwards.mp4"
          },
          {
            "name": "19. Diagonal Forwards - Backwards",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/19_19._Diagonal_Forwards_-_Backwards.mp4"
          },
          {
            "name": "20. Diagonal Lateral Shuffle",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/20_Fast_Feet_Exercises/20_20._Diagonal_Lateral_Shuffle.mp4"
          }
        ]
      },
      {
        "name": "32 Ball Mastery Exercises",
        "type": "direct",
        "thumbnailUrl": "https://img.youtube.com/vi/NMfLJynwyTk/hqdefault.jpg",
        "chapters": [
          {
            "name": "1. Single Leg Weave (right foot)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/01_1._Single_Leg_Weave_right_foot_.mp4"
          },
          {
            "name": "2. Single Leg Weave (left foot)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/02_2._Single_Leg_Weave_left_foot_.mp4"
          },
          {
            "name": "3. Outside Foot Only",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/03_3._Outside_Foot_Only.mp4"
          },
          {
            "name": "4. Two touch outside inside (right foot)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/04_4._Two_touch_outside_inside_right_foot_.mp4"
          },
          {
            "name": "5. Two touch outside inside (left foot)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/05_5._Two_touch_outside_inside_left_foot_.mp4"
          },
          {
            "name": "6. La Croqueta",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/06_6._La_Croqueta.mp4"
          },
          {
            "name": "7. Inside Inside",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/07_7._Inside_Inside.mp4"
          },
          {
            "name": "8. Croqueta Outside left",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/08_8._Croqueta_Outside_left.mp4"
          },
          {
            "name": "9. Croqueta Outside Right",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/09_9._Croqueta_Outside_Right.mp4"
          },
          {
            "name": "10. Inside Outside",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/10_10._Inside_Outside.mp4"
          },
          {
            "name": "11. Sole Roll Stop",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/11_11._Sole_Roll_Stop.mp4"
          },
          {
            "name": "12. Sole Rolls",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/12_12._Sole_Rolls.mp4"
          },
          {
            "name": "13. Toe Taps Forward",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/13_13._Toe_Taps_Forward.mp4"
          },
          {
            "name": "14. Toe Taps Backwards",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/14_14._Toe_Taps_Backwards.mp4"
          },
          {
            "name": "15. Roll Stepover",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/15_15._Roll_Stepover.mp4"
          },
          {
            "name": "16. L Drag Push",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/16_16._L_Drag_Push.mp4"
          },
          {
            "name": "17. Backwards L Drag",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/17_17._Backwards_L_Drag.mp4"
          },
          {
            "name": "18. Inside Foot V Cut",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/18_18._Inside_Foot_V_Cut.mp4"
          },
          {
            "name": "19. Outside Foot V Cut",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/19_19._Outside_Foot_V_Cut.mp4"
          },
          {
            "name": "20. Alternating Feet V Cuts",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/20_20._Alternating_Feet_V_Cuts.mp4"
          },
          {
            "name": "21. Stepover La Croqueta",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/21_21._Stepover_La_Croqueta.mp4"
          },
          {
            "name": "22. Inside Pull Push (left)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/22_22._Inside_Pull_Push_left_.mp4"
          },
          {
            "name": "23. Inside Pull Push (right)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/23_23._Inside_Pull_Push_right_.mp4"
          },
          {
            "name": "24. Outside Pull Push (left)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/24_24._Outside_Pull_Push_left_.mp4"
          },
          {
            "name": "25. Outside Pull Push (right)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/25_25._Outside_Pull_Push_right_.mp4"
          },
          {
            "name": "26. Single Leg Toe Taps (right)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/26_26._Single_Leg_Toe_Taps_right_.mp4"
          },
          {
            "name": "27. Single Leg Toe Taps (left)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/27_27._Single_Leg_Toe_Taps_left_.mp4"
          },
          {
            "name": "28. Lateral Sole (right side lead)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/28_28._Lateral_Sole_right_side_lead_.mp4"
          },
          {
            "name": "29. Lateral Sole (left side lead)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/29_29._Lateral_Sole_left_side_lead_.mp4"
          },
          {
            "name": "30. L Drag Push (right side lead)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/30_30._L_Drag_Push_right_side_lead_.mp4"
          },
          {
            "name": "31. L Drag Push (left side lead)",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/31_31._L_Drag_Push_left_side_lead_.mp4"
          },
          {
            "name": "32. Outside Foot Stepover",
            "videoUrl": "https://pub-8bc2aeb8f4064d06915701c5f2e02c9c.r2.dev/youtube-clips/32_Ball_Mastery_Exercises/32_32._Outside_Foot_Stepover.mp4"
          }
        ]
      },
      {
        "name": "Prellwand",
        "type": "direct",
        "chapters": []
      }
    ]
    """
}
