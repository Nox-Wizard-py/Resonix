package com.noxwizard.resonix.di

import com.noxwizard.resonix.services.MicRecordingService
import com.noxwizard.resonix.services.RecognitionApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing audio recognition services.
 * Both services are singletons — OkHttpClient reuse and recording safety.
 */
@Module
@InstallIn(SingletonComponent::class)
object RecognitionModule {

    @Provides
    @Singleton
    fun provideRecognitionApiService(): RecognitionApiService = RecognitionApiService()

    @Provides
    @Singleton
    fun provideMicRecordingService(): MicRecordingService = MicRecordingService()
}
