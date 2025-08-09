package com.codenzi.mathlabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatAiDialogFragment : BottomSheetDialogFragment() {

    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var editTextQuestion: EditText
    private lateinit var buttonSend: View
    private lateinit var thinkingIndicator: LinearLayout
    private lateinit var suggestionScrollView: View
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var pdfViewActivity: PdfViewActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Dialog'un layout dosyasını inflate ediyoruz.
        return inflater.inflate(R.layout.dialog_ai_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bu fragment'ı açan PdfViewActivity'ye erişiyoruz.
        pdfViewActivity = activity as PdfViewActivity

        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()

        // Eğer kullanıcının sorgu hakkı yoksa ve sohbet boşsa, giriş alanlarını devre dışı bırak.
        if (!AiQueryManager.canPerformQuery(requireContext()) && pdfViewActivity.chatMessages.isEmpty()) {
            disableInputWithQuotaMessage()
        }
    }

    private fun initializeViews(view: View) {
        recyclerViewChat = view.findViewById(R.id.recyclerViewChat)
        editTextQuestion = view.findViewById(R.id.editTextQuestion)
        buttonSend = view.findViewById(R.id.buttonSend)
        thinkingIndicator = view.findViewById(R.id.thinkingIndicator)
        suggestionScrollView = view.findViewById(R.id.suggestionScrollView)
    }

    private fun setupRecyclerView() {
        // Sohbet mesajları listesi PdfViewActivity'de tutulduğu için oradan alıyoruz.
        chatAdapter = ChatAdapter(pdfViewActivity.chatMessages)
        recyclerViewChat.adapter = chatAdapter
        recyclerViewChat.layoutManager = LinearLayoutManager(context)
        // Her yeni mesajda en alta kaydır.
        recyclerViewChat.scrollToPosition(pdfViewActivity.chatMessages.size - 1)
    }

    private fun setupClickListeners() {
        buttonSend.setOnClickListener {
            val question = editTextQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                sendQueryToServer(question)
                editTextQuestion.text.clear()
            }
        }

        // Öneri butonları
        view?.findViewById<Button>(R.id.btnSuggestion1)?.setOnClickListener { sendQueryToServer((it as Button).text.toString()) }
        view?.findViewById<Button>(R.id.btnSuggestion2)?.setOnClickListener { sendQueryToServer((it as Button).text.toString()) }
        view?.findViewById<Button>(R.id.btnSuggestion3)?.setOnClickListener { sendQueryToServer((it as Button).text.toString()) }

        // Sohbeti temizle butonu
        view?.findViewById<ImageButton>(R.id.buttonClearChat)?.setOnClickListener {
            val oldSize = pdfViewActivity.chatMessages.size
            pdfViewActivity.chatMessages.clear()
            pdfViewActivity.conversationHistory.clear()
            chatAdapter.notifyItemRangeRemoved(0, oldSize)

            if (AiQueryManager.canPerformQuery(requireContext())) {
                enableInput()
            } else {
                disableInputWithQuotaMessage()
            }
        }
    }

    /**
     * Kullanıcının sorgu hakkı bittiğinde UI'ı günceller.
     */
    private fun disableInputWithQuotaMessage() {
        editTextQuestion.isEnabled = false
        buttonSend.isEnabled = false
        suggestionScrollView.visibility = View.GONE

        // Ekranda zaten kota aşıldı mesajı yoksa ekle.
        if (pdfViewActivity.chatMessages.none { it.message.contains(getString(R.string.ai_quota_exceeded,"")) }) {
            val quotaMessage = AiQueryManager.getQuotaExceededMessage(requireContext())
            addMessage(ChatMessage(quotaMessage, false))
        }
    }

    /**
     * Kullanıcı giriş alanlarını (metin kutusu, butonlar) aktif hale getirir.
     */
    private fun enableInput() {
        editTextQuestion.isEnabled = true
        buttonSend.isEnabled = true
        suggestionScrollView.visibility = View.VISIBLE
    }

    /**
     * Kullanıcının sorusunu alır ve AiQueryManager aracılığıyla Firebase sunucusuna gönderir.
     */
    private fun sendQueryToServer(question: String) {
        // Sorgu hakkı kontrolü
        if (!AiQueryManager.canPerformQuery(requireContext())) {
            dismiss() // Dialog'u kapat
            pdfViewActivity.showWatchAdDialog() // Reklam izleme dialog'unu göster
            return
        }

        // Kullanıcının mesajını anında ekrana ekle
        addMessage(ChatMessage(question, true))

        // UI'ı "düşünüyor" moduna al
        suggestionScrollView.visibility = View.GONE
        thinkingIndicator.visibility = View.VISIBLE
        editTextQuestion.isEnabled = false
        buttonSend.isEnabled = false

        // Coroutine ile arka planda sunucuya istek gönder
        lifecycleScope.launch(Dispatchers.IO) {
            val relevantContext = pdfViewActivity.extractTextForAI()
            val prompt = createPrompt(question, relevantContext)

            AiQueryManager.getResponseFromServer(prompt) { result ->
                // Sunucudan gelen cevabı ana iş parçacığında (Main Thread) işle
                lifecycleScope.launch(Dispatchers.Main) {
                    thinkingIndicator.visibility = View.GONE

                    result.onSuccess { aiResponse ->
                        // Başarılı cevap geldiyse
                        addMessage(ChatMessage(aiResponse, false))
                        AiQueryManager.incrementQueryCount(requireContext()) // Kota sayacını artır
                    }.onFailure { error ->
                        // Hata geldiyse
                        val errorMessage = getString(R.string.ai_chat_error_with_details, error.localizedMessage)
                        addMessage(ChatMessage(errorMessage, false))
                    }

                    // Sorgu hakkı kalıp kalmadığını tekrar kontrol et ve UI'ı güncelle
                    if (AiQueryManager.canPerformQuery(requireContext())) {
                        enableInput()
                    } else {
                        disableInputWithQuotaMessage()
                    }
                }
            }
        }
    }

    /**
     * Sunucuya gönderilecek olan tam metni (prompt) oluşturur.
     */
    private fun createPrompt(question: String, context: String): String {
        val historyText = pdfViewActivity.conversationHistory.joinToString("\n")
        val currentLanguage = SharedPreferencesManager.getLanguage(requireContext()) ?: "tr"

        return if (currentLanguage == "en") {
            """
            Conversation History:
            $historyText
            --- Text from the current PDF page ---
            $context
            ------------------------------------
            Based on the conversation history and the text from the PDF, answer the user's LAST question.
            Format your response using Markdown (e.g., **bold**, *italics*, lists).
            If the answer is not in the text, clearly state that the information is not in the provided pages.
            USER'S LAST QUESTION: "$question"
            """.trimIndent()
        } else {
            """
            Önceki Konuşma Geçmişi:
            $historyText
            --- Mevcut PDF sayfasından metin ---
            $context
            -----------------------
            Konuşma geçmişini ve PDF metnini dikkate alarak kullanıcının SON SORUSUNU yanıtla.
            Cevabını Markdown formatı kullanarak (örneğin, **kalın**, *italik*, listeler) oluştur.
            Eğer cevap metinde yoksa, bu bilginin sağlanan sayfalarda bulunmadığını açıkça belirt.
            KULLANICININ SON SORUSU: "$question"
            """.trimIndent()
        }
    }

    /**
     * Yeni bir mesajı listeye ekler ve RecyclerView'ı günceller.
     */
    private fun addMessage(chatMessage: ChatMessage) {
        pdfViewActivity.chatMessages.add(chatMessage)
        chatAdapter.notifyItemInserted(pdfViewActivity.chatMessages.size - 1)
        recyclerViewChat.scrollToPosition(pdfViewActivity.chatMessages.size - 1)
    }
}
