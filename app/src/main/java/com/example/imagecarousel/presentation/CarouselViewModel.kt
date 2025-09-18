package com.example.imagecarousel.presentation

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.imagecarousel.domain.repository.DataState
import com.example.imagecarousel.domain.usecases.GetImagesUseCase
import com.example.imagecarousel.presentation.models.CanvasImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CarouselViewModel @Inject constructor(private val getImagesUseCase: GetImagesUseCase): ViewModel() {

    private val _uiState = MutableStateFlow<CarouselUiState>(CarouselUiState())
    val uiState: StateFlow<CarouselUiState> = _uiState

    private val _canvasImages = mutableStateListOf<CanvasImage>()
    val canvasImages: List<CanvasImage> get() = _canvasImages

    fun addCanvasImage(bm: Bitmap, offset: Offset, userScale: Float = 1f) {
        _canvasImages += CanvasImage(
            id = UUID.randomUUID().toString(),
            bitmap = bm,
            offset = offset,
            userScale = userScale
        )
    }

    fun updateOffset(id: String, offset: Offset) {
        _canvasImages.indexOfFirst { it.id == id }
            .takeIf { it >= 0 }?.let { idx ->
                _canvasImages[idx] = _canvasImages[idx].copy(offset = offset)
            }
    }

    fun updateUserScale(id: String, userScale: Float) {
        _canvasImages.indexOfFirst { it.id == id }
            .takeIf { it >= 0 }?.let { idx ->
                _canvasImages[idx] = _canvasImages[idx].copy(userScale = userScale)
            }
    }

    fun updateImageTranslation(id: String, translation: Offset) {
        _canvasImages.indexOfFirst { it.id == id }
            .takeIf { it >= 0 }?.let { idx ->
                _canvasImages[idx] = _canvasImages[idx].copy(imageTranslation = translation)
            }
    }

    fun loadImages(count: Int = 5) {
        viewModelScope.launch(Dispatchers.IO) {
            getImagesUseCase.getImages(count).onStart {
                _uiState.value = CarouselUiState(loading = true)
            }.catch {
                _uiState.value = CarouselUiState(error = it.message)
            }.collect { dataState ->
                when (dataState) {
                    is DataState.Success -> {
                        _uiState.value = CarouselUiState(images = dataState.data.images)
                    }
                    is DataState.Error -> {
                        _uiState.value = CarouselUiState(loading = false, error = dataState.message)
                    }
                    is DataState.Loading -> {
                        _uiState.value = CarouselUiState(loading = true)
                    }
                }
            }

        }
    }
}