package com.example.imagecarousel.data.mediastore

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.example.imagecarousel.data.mappers.toDomain
import com.example.imagecarousel.data.models.ImageDto
import com.example.imagecarousel.data.models.ImageResponseDto
import com.example.imagecarousel.domain.ImageResponse
import com.example.imagecarousel.domain.repository.ImageRepository
import java.io.IOException
import javax.inject.Inject

class MediaStoreImageRepository @Inject constructor(
    private val context: Context
) : ImageRepository {
    override suspend fun getImages(count: Int): ImageResponse {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC LIMIT $count"
        return try {
            val images = mutableListOf<ImageDto>()
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var readCount = 0
                while (cursor.moveToNext() && readCount < count) {
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    try {
                        resolver.openInputStream(uri)?.use { input ->
                            val bitmap = BitmapFactory.decodeStream(input)
                            if (bitmap != null) {
                                images.add(ImageDto(filename = id.toString(), image = bitmap))
                                readCount++
                            }
                        }
                    } catch (e: IOException) {
                        // skip file if it fails
                    }
                }
            }
            ImageResponseDto(images).toDomain()
        } catch (e: SecurityException) {
            ImageResponseDto(emptyList()).toDomain()
        }
    }
}