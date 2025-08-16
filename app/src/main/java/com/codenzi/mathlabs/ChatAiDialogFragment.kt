package com.codenzi.mathlabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatAiDialogFragment : BottomSheetDialogFragment() {

    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var editTextQuestion: EditText
    private lateinit var buttonSend: View
    private lateinit var thinkingIndicator: LinearLayout
    private lateinit var suggestionScrollView: View
    private lateinit var buttonGetMoreQueries: Button
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var pdfViewActivity: PdfViewActivity

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // XML layout dosyasını inflate et
        return inflater.inflate(R.layout.dialog_ai_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // PdfViewActivity referansını al
        pdfViewActivity = activity as PdfViewActivity

        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()

        // Kullanıcı verisini ve sorgu hakkını gerçek zamanlı olarak dinle.
        // Bu yapı, arayüzün genel durumunu (butonların aktif/pasif olması) yönetir.
        viewLifecycleOwner.lifecycleScope.launch {
            UserRepository.userDataState.collect { userData ->
                val canQuery = AiQueryManager.canPerformQuery()
                if (canQuery) {
                    if (!editTextQuestion.isEnabled) {
                        enableInput()
                    }
                } else {
                    if (editTextQuestion.isEnabled) {
                        disableInput()
                    }
                }
            }
        }
    }

    private fun initializeViews(view: View) {
        recyclerViewChat = view.findViewById(R.id.recyclerViewChat)
        editTextQuestion = view.findViewById(R.id.editTextQuestion)
        buttonSend = view.findViewById(R.id.buttonSend)
        thinkingIndicator = view.findViewById(R.id.thinkingIndicator)
        suggestionScrollView = view.findViewById(R.id.suggestionScrollView)
        buttonGetMoreQueries = view.findViewById(R.id.buttonGetMoreQueries)
    }

    private fun setupRecyclerView() {
        // Sohbet geçmişi boşsa, bir karşılama mesajı ekle.
        if (pdfViewActivity.chatMessages.isEmpty()) {
            val welcomeMessage = getString(R.string.ai_chat_welcome_message)
            pdfViewActivity.chatMessages.add(ChatMessage(welcomeMessage, false))
        }

        chatAdapter = ChatAdapter(pdfViewActivity.chatMessages)
        recyclerViewChat.adapter = chatAdapter
        recyclerViewChat.layoutManager = LinearLayoutManager(context)
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

        buttonGetMoreQueries.setOnClickListener {
            // "Sorgu Hakkı Kazan" butonuna basıldığında reklam izleme diyaloğunu göster.
            pdfViewActivity.showWatchAdDialog()
        }

        view?.findViewById<Button>(R.id.btnSuggestion1)?.setOnClickListener { sendQueryToServer((it as Button).text.toString()) }
        view?.findViewById<Button>(R.id.btnSuggestion2)?.setOnClickListener { sendQueryToServer((it as Button).text.toString()) }
        view?.findViewById<Button>(R.id.btnSuggestion3)?.setOnClickListener { sendQueryToServer((it as Button).text.toString()) }

        view?.findViewById<ImageButton>(R.id.buttonClearChat)?.setOnClickListener {
            clearChat()
        }
    }

    private fun clearChat() {
        lifecycleScope.launch {
            pdfViewActivity.chatMessages.clear()
            pdfViewActivity.conversationHistory.clear()
            chatAdapter.notifyDataSetChanged()

            if (AiQueryManager.canPerformQuery()) {
                enableInput()
                val welcomeMessage = getString(R.string.ai_chat_welcome_message)
                addMessage(ChatMessage(welcomeMessage, false))
            } else {
                disableInput()
            }
        }
    }

    /**
     * Kullanıcının sorgu hakkı bittiğinde UI'ı günceller.
     * Giriş alanlarını pasif hale getirir ve "Sorgu Hakkı Kazan" butonunu gösterir.
     */
    private fun disableInput() {
        editTextQuestion.isEnabled = false
        buttonSend.isEnabled = false
        suggestionScrollView.visibility = View.GONE
        buttonGetMoreQueries.visibility = View.VISIBLE

        val quotaMessage = getString(R.string.ai_quota_exceeded, "") // string.xml dosyanızdaki ilgili metin
        if (pdfViewActivity.chatMessages.lastOrNull()?.message != quotaMessage) {
            addMessage(ChatMessage(quotaMessage, false))
        }
    }

    /**
     * Kullanıcı giriş alanlarını aktif hale getirir.
     */
    private fun enableInput() {
        editTextQuestion.isEnabled = true
        buttonSend.isEnabled = true
        suggestionScrollView.visibility = View.VISIBLE
        buttonGetMoreQueries.visibility = View.GONE

        // İyileştirme: Eğer son mesaj "kota aşıldı" mesajı ise, onu kaldır.
        val quotaMessage = getString(R.string.ai_quota_exceeded, "")
        if (pdfViewActivity.chatMessages.lastOrNull()?.message == quotaMessage) {
            val lastIndex = pdfViewActivity.chatMessages.size - 1
            if (lastIndex >= 0) {
                pdfViewActivity.chatMessages.removeAt(lastIndex)
                chatAdapter.notifyItemRemoved(lastIndex)
            }
        }
    }

    /**
     * Kullanıcının sorusunu alır, asenkron olarak sorgu hakkını kontrol eder ve sunucuya gönderir.
     */
    private fun sendQueryToServer(question: String) {
        lifecycleScope.launch {
            // 1. Sorgu göndermeden önce hakkı tekrar kontrol et
            if (!AiQueryManager.canPerformQuery()) {
                disableInput()
                pdfViewActivity.showWatchAdDialog()
                return@launch
            }

            // 2. UI güncellemeleri
            addMessage(ChatMessage(question, true))
            suggestionScrollView.visibility = View.GONE
            thinkingIndicator.visibility = View.VISIBLE
            editTextQuestion.isEnabled = false
            buttonSend.isEnabled = false

            // 3. Sorgu sayısını artır
            AiQueryManager.incrementQueryCount()

            // 4. PDF'ten ilgili metni al
            val relevantContext = withContext(Dispatchers.IO) {
                pdfViewActivity.extractTextForAI()
            }
            val prompt = createPrompt(question, relevantContext)

            // 5. Sunucudan cevabı al
            AiQueryManager.getResponseFromServer(prompt) { result ->
                // UI'ı ana thread'de güncelle
                requireActivity().runOnUiThread {
                    thinkingIndicator.visibility = View.GONE

                    result.onSuccess { aiResponse ->
                        addMessage(ChatMessage(aiResponse, false))
                    }.onFailure { error ->
                        val errorMessage = getString(R.string.ai_chat_error_with_details, error.localizedMessage)
                        addMessage(ChatMessage(errorMessage, false))
                    }

                    // 6. Cevap geldikten sonra sorgu hakkını tekrar kontrol et ve UI'ı ayarla
                    lifecycleScope.launch {
                        if (!AiQueryManager.canPerformQuery()) {
                            // Haklar bu sorgu ile bittiyse, reklam izleme diyaloğunu göster.
                            // Arayüzün pasif hale gelmesini ana 'collect' bloğu zaten sağlayacak.
                            pdfViewActivity.showWatchAdDialog()
                        } else {
                            // Hala hak varsa, bir sonraki soru için giriş alanlarını tekrar aktif et.
                            enableInput()
                        }
                    }
                }
            }
        }
    }

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

    private fun addMessage(chatMessage: ChatMessage) {
        pdfViewActivity.chatMessages.add(chatMessage)
        chatAdapter.notifyItemInserted(pdfViewActivity.chatMessages.size - 1)
        recyclerViewChat.scrollToPosition(pdfViewActivity.chatMessages.size - 1)
    }
}