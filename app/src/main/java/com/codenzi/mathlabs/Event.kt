package com.codenzi.mathlabs

/**
 * LiveData tarafından kullanılan ve yalnızca bir kez işlenmesi gereken olaylar için bir sarmalayıcı.
 * Bu, yapılandırma değişikliklerinde (örneğin ekran döndürme) bir olayın
 * (örneğin bir Toast mesajı) tekrar tetiklenmesini önler.
 */
open class Event<out T>(private val content: T) {

    var hasBeenHandled = false
        private set // Dışarıdan yazmayı engelle

    /**
     * İçeriği döndürür ve olayın işlendi olarak işaretlenmesini sağlar.
     * Eğer olay zaten işlenmişse null döner.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * İşlenip işlenmediğine bakmaksızın içeriği döndürür.
     */
    fun peekContent(): T = content
}