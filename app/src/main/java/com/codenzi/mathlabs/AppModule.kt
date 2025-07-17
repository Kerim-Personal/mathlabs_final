package com.codenzi.mathlabs.di

import android.content.Context
import com.codenzi.mathlabs.CourseRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Bu bağımlılıkların uygulama ömrü boyunca yaşayacağını belirtir
object AppModule {

    @Provides
    @Singleton // Context'in tek bir örneğinin olmasını sağlar
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideCourseRepository(): CourseRepository {
        return CourseRepository()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        // Önbellek için bir klasör ve boyut belirle (100MB)
        val cacheSize = 100L * 1024 * 1024 // 100 MB
        val cacheDirectory = File(context.cacheDir, "http-cache")
        val cache = Cache(cacheDirectory, cacheSize)

        // OkHttpClient'ı oluştur ve önbelleği ata
        return OkHttpClient.Builder()
            .cache(cache)
            .build()
    }
}