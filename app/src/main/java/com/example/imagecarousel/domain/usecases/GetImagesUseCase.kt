package com.example.imagecarousel.domain.usecases

import com.example.imagecarousel.domain.ImageResponse
import com.example.imagecarousel.domain.repository.DataState
import com.example.imagecarousel.domain.repository.ImageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject


class GetImagesUseCase @Inject constructor(
    private val repository: ImageRepository
) {
    fun getImages(count: Int = 5): Flow<DataState<ImageResponse>> = flow {
        emit(DataState.Loading)
        try {
            val images = repository.getImages(count)
            emit(DataState.Success(images))
        } catch (e: Exception) {
            emit(DataState.Error(e.message ?: "An unexpected error occurred"))
        }
    }
}