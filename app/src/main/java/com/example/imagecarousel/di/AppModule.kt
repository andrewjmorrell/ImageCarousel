package com.example.imagecarousel.di

import android.content.Context
import com.example.imagecarousel.data.local.LocalImageRepository
import com.example.imagecarousel.domain.repository.ImageRepository
import com.example.imagecarousel.domain.usecases.GetImagesUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMarkerUseCase(repository: ImageRepository): GetImagesUseCase = GetImagesUseCase(repository)

     @Provides
     @Singleton
     fun providesImageRepository(@ApplicationContext context: Context): ImageRepository = LocalImageRepository(context)
     //swap here to use a different data source
     //fun providesImageRepository(@ApplicationContext context: Context): ImageRepository = MediaStoreImageRepository(context)
}