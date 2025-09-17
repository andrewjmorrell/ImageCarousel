package com.example.imagecarousel.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.imagecarousel.domain.repository.DataState
import com.example.imagecarousel.domain.usecases.GetImagesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CarouselViewModel @Inject constructor(private val getImagesUseCase: GetImagesUseCase): ViewModel() {

    private val _uiState = MutableStateFlow<CarouselUiState>(CarouselUiState())
    val uiState: StateFlow<CarouselUiState> = _uiState

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