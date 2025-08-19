# kerim-personal/mathlabs_final/mathlabs_final-f49787796173bd93b9413051b0018b2349ef86c8/app/proguard-rules.pro

# ===============================================
# Android Varsayılan Kuralları
# ===============================================
# Hata ayıklama için satır numarası bilgilerini koru.
-keepattributes SourceFile,LineNumberTable

# ===============================================
# Data Sınıfları (Models)
# Proguard'ın bu sınıfların alanlarını kaldırmaması için.
# ===============================================
-keep class com.codenzi.mathlabs.Course { *; }
-keep class com.codenzi.mathlabs.Topic { *; }

# ===============================================
# Room Veritabanı
# Entity sınıfları ve alanları korunmalıdır.
# ===============================================
-keep class com.codenzi.mathlabs.database.DrawingPath { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
   public <init>();
}

# ===============================================
# Dagger / Hilt
# Hilt'in kullandığı annotation'ları ve generate ettiği sınıfları koru.
# ===============================================
-keepattributes Signature
-keepattributes *Annotation*
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class com.codenzi.mathlabs.Hilt_** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class dagger.hilt.android.internal.lifecycle.** { *; }
-keep class dagger.hilt.android.internal.managers.** { *; }
-keep class dagger.hilt.android.internal.modules.** { *; }
-keep class dagger.hilt.internal.preconditions.** { *; }

# ===============================================
# Google Play Billing (Ödeme Sistemi)
# Release sürümünde ödeme sisteminin çökmemesi için bu kural zorunludur.
# ===============================================
-keep class com.android.billingclient.** { *; }

# ===============================================
# PDFBox Kütüphanesi
# Bu kütüphane reflection ve kaynakları yoğun kullandığı için özel kurallar gerektirir.
# ===============================================
-keep class org.apache.pdfbox.** { *; }
-dontwarn org.apache.pdfbox.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ===============================================
# PDFBox-Android (com.tom_roush.*)
# Android portu, paketleri com.tom_roush altında yeniden adlandırır.
# Reflection ve kaynak yüklemeleri sebebiyle koruma gerekiyor.
# ===============================================
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.xmpbox.** { *; }
-dontwarn com.tom_roush.**

# Bazı derlemelerde commons-logging referansları uyarı üretebilir
-dontwarn org.apache.commons.logging.**

# ===============================================
# Glide (Görüntü Yükleme)
# AppGlideModule veya eski GlideModule tanımları için.
# ===============================================
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** { *; }
-dontwarn com.bumptech.glide.**

# ===============================================
# AndroidPdfViewer -> Pdfium (com.shockwave.pdfium)
# Bazı cihaz/sürümlerde eksik sınıf uyarılarını susturmak için
# ===============================================
-dontwarn com.shockwave.**

# ===============================================
# Custom View'ler
# XML'den çağrılan custom view'ların constructor'ları korunmalı.
# ===============================================
-keep public class com.codenzi.mathlabs.DrawingView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ===============================================
# Gemini AI (Google Generative AI)
# API'nin kullandığı veri modellerini koruyalım.
# ===============================================
-keep class com.google.ai.client.generativeai.** { *; }
-dontwarn com.google.ai.client.generativeai.**

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.gemalto.jp2.JP2Decoder
