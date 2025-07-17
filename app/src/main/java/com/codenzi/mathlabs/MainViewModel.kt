package com.codenzi.mathlabs

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject

/**
 * MainActivity için ViewModel. Bu sınıf, UI ile ilgili verileri tutar ve iş mantığını yönetir.
 * Hilt tarafından yönetilir ve yaşam döngüsüne duyarlıdır.
 *
 * @param courseRepository Veri katmanına erişim sağlamak için Hilt tarafından enjekte edilen repository.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val courseRepository: CourseRepository
) : ViewModel() {

    // UI tarafından gözlemlenecek olan, filtrelenmiş kurs listesini tutan LiveData.
    // Bu _courses private'tır, böylece sadece ViewModel içinden değiştirilebilir.
    private val _courses = MutableLiveData<List<Course>>()

    // UI'ın gözlemlemesi için dışarıya açılan, değiştirilemez (immutable) LiveData.
    val courses: LiveData<List<Course>> = _courses

    // Filtrelenmemiş orijinal tam listeyi bellekte tutarız.
    private var fullCourseList: List<Course> = listOf()

    /**
     * Başlangıçta kursları CourseRepository'den yükler.
     * Bu fonksiyon, doğru dil kaynaklarına sahip olan güncel Context'i almalıdır.
     * @param context Güncel dil yapılandırmasına sahip olan Activity Context'i.
     */
    fun loadCourses(context: Context) {
        // Repository'den tüm kursları alırken parametre olarak gelen güncel context'i kullan.
        val allCourses = courseRepository.getCourses(context)
        // Tam listeyi daha sonra filtreleme için sakla.
        fullCourseList = allCourses
        // Gözlemlenen LiveData'nın değerini güncelle, bu da UI'ın yenilenmesini tetikler.
        _courses.value = allCourses
    }

    /**
     * Arama metnine göre kursları ve konuları filtreler.
     *
     * @param query Kullanıcının arama çubuğuna girdiği metin.
     */
    fun filter(query: String?) {
        val searchText = query?.lowercase(Locale.getDefault())?.trim()

        // Eğer arama metni boşsa, orijinal tam listeyi göster.
        if (searchText.isNullOrEmpty()) {
            _courses.value = fullCourseList
            return
        }

        // Arama metni varsa, tam liste üzerinden filtreleme yap.
        val filteredList = fullCourseList.mapNotNull { course ->
            // Konu başlıklarından arama metnini içerenleri bul.
            val matchingTopics = course.topics.filter { topic ->
                topic.title.lowercase(Locale.getDefault()).contains(searchText)
            }

            // Eğer kurs başlığı arama metnini içeriyorsa VEYA eşleşen bir konu varsa,
            // bu kursu filtrelenmiş listeye dahil et.
            if (course.title.lowercase(Locale.getDefault()).contains(searchText) || matchingTopics.isNotEmpty()) {
                // Eğer kurs başlığı eşleşiyorsa, o kursun tüm konularını göster.
                // Eğer sadece konu başlığı eşleşiyorsa, sadece eşleşen konuları göster.
                val topicsToShow = if (course.title.lowercase(Locale.getDefault()).contains(searchText)) {
                    course.topics
                } else {
                    matchingTopics
                }
                // Kursun bir kopyasını yeni konu listesiyle oluştur.
                course.copy(topics = topicsToShow)
            } else {
                null // Eşleşme yoksa bu kursu listeden çıkar.
            }
        }

        // Gözlemlenen LiveData'yı filtrelenmiş yeni liste ile güncelle.
        _courses.value = filteredList
    }
}