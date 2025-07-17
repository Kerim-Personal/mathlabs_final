package com.codenzi.mathlabs

/**
 * Tek bir konunun detaylarını temsil eden veri sınıfı.
 *
 * @param title Konunun kullanıcı arayüzünde gösterilecek adı (örn: "Limit ve Süreklilik").
 * @param pdfAssetName Konuyla ilişkili PDF dosyasının 'assets' klasöründeki adı.
 * @param hasPdf Bu konu için 'assets' klasöründe bir PDF dosyasının bulunup bulunmadığını belirtir.
 */
data class Topic(
    val title: String,
    val pdfAssetName: String,
    val hasPdf: Boolean
)

/**
 * Bir dersi ve o derse ait konuları temsil eden ana veri sınıfı.
 *
 * @param title Dersin adı (örn: "Kalkülüs").
 * @param topics Bu derse ait olan [Topic] nesnelerinin bir listesi.
 * @param isExpanded Kullanıcı arayüzündeki akordiyon menünün o an açık mı kapalı mı olduğunu
 * belirtmek için kullanılır. Varsayılan olarak kapalıdır (false).
 */
data class Course(
    val title: String,
    val topics: List<Topic>,
    var isExpanded: Boolean = false
)