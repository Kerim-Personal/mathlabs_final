package com.codenzi.mathlabs.di

import android.content.Context
import androidx.room.Room
import com.codenzi.mathlabs.CourseRepository
import com.codenzi.mathlabs.database.AppDatabase
import com.codenzi.mathlabs.database.DrawingDao
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
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideCourseRepository(): CourseRepository {
        return CourseRepository()
    }

    // --- YENİ VERİTABANI SAĞLAYICILARI ---
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mathlabs_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideDrawingDao(database: AppDatabase): DrawingDao {
        return database.drawingDao()
    }
    // --- BİTİŞ ---

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cacheSize = 100L * 1024 * 1024 // 100 MB
        val cacheDirectory = File(context.cacheDir, "http-cache")
        val cache = Cache(cacheDirectory, cacheSize)

        return OkHttpClient.Builder()
            .cache(cache)
            .build()
    }
}