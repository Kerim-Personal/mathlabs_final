// kerim-personal/mathlabs_final/mathlabs_final-fc4db8886de96aa10a2d2cc3b8c6c3634d9f4003/app/src/main/java/com/codenzi/mathlabs/ChatAiDialogFragment.kt
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatAiDialogFragment : BottomSheetDialogFragment() {

    private lateinit var recyclerViewChat: RecyclerView
    private lateinit var editTextQuestion: EditText
    private lateinit var buttonSend: View
    private lateinit var thinkingIndicator: LinearLayout
    private lateinit var suggestionScrollView: View
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var pdfViewActivity: PdfViewActivity

    // --- KALDIRILAN SATIRLAR: Bu değişkenler artık PdfViewActivity'de tutuluyor. ---
    // private val chatMessages = mutableListOf<ChatMessage>()
    // private val conversationHistory = mutableListOf<String>()
    // --- KALDIRILAN SATIRLARIN SONU ---

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_ai_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        pdfViewActivity = activity as PdfViewActivity

        initializeViews(view)
        setupRecyclerView()
        setupClickListeners()

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
        // --- DEĞİŞTİRİLEN SATIR: Artık PdfViewActivity'deki listeyi kullanıyor. ---
        chatAdapter = ChatAdapter(pdfViewActivity.chatMessages)
        recyclerViewChat.adapter = chatAdapter
        val layoutManager = LinearLayoutManager(context)
        recyclerViewChat.layoutManager = layoutManager
        // Sohbetin en sonuna kaydır
        recyclerViewChat.scrollToPosition(pdfViewActivity.chatMessages.size - 1)
    }

    private fun setupClickListeners() {
        buttonSend.setOnClickListener {
            val question = editTextQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                sendQuery(question)
                editTextQuestion.text.clear()
            }
        }

        view?.findViewById<Button>(R.id.btnSuggestion1)?.setOnClickListener {
            sendQuery((it as Button).text.toString())
        }
        view?.findViewById<Button>(R.id.btnSuggestion2)?.setOnClickListener {
            sendQuery((it as Button).text.toString())
        }
        view?.findViewById<Button>(R.id.btnSuggestion3)?.setOnClickListener {
            sendQuery((it as Button).text.toString())
        }

        view?.findViewById<ImageButton>(R.id.buttonClearChat)?.setOnClickListener {
            // --- DEĞİŞTİRİLEN SATIRLAR: Artık PdfViewActivity'deki listeleri temizliyor. ---
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

    private fun disableInputWithQuotaMessage() {
        editTextQuestion.isEnabled = false
        buttonSend.isEnabled = false
        suggestionScrollView.visibility = View.GONE

        if (pdfViewActivity.chatMessages.none { it.message.contains(getString(R.string.ai_quota_exceeded,"")) }) {
            val quotaMessage = AiQueryManager.getQuotaExceededMessage(requireContext())
            addMessage(ChatMessage(quotaMessage, false))
        }
    }

    private fun enableInput() {
        editTextQuestion.isEnabled = true
        buttonSend.isEnabled = true
        suggestionScrollView.visibility = View.VISIBLE
    }

    private fun sendQuery(question: String) {
        if (!AiQueryManager.canPerformQuery(requireContext())) {
            dismiss() // Dialog'u kapat
            pdfViewActivity.showWatchAdDialog()
            return
        }

        addMessage(ChatMessage(question, true))
        suggestionScrollView.visibility = View.GONE
        thinkingIndicator.visibility = View.VISIBLE
        editTextQuestion.isEnabled = false
        buttonSend.isEnabled = false

        lifecycleScope.launch {
            var aiResponse = ""
            try {
                // --- DEĞİŞTİRİLEN SATIR: PdfViewActivity'deki listeye ekliyor. ---
                pdfViewActivity.conversationHistory.add("Kullanıcı: $question")
                val relevantContext = pdfViewActivity.extractTextForAI()
                if (relevantContext.isBlank()) {
                    aiResponse = getString(R.string.ai_info_not_found)
                } else {
                    val prompt = createPrompt(question, relevantContext)
                    val responseFlow = pdfViewActivity.generativeModel.generateContentStream(prompt)
                        .catch { e ->
                            aiResponse = getString(R.string.ai_chat_error_with_details, e.localizedMessage ?: "Unknown error")
                        }

                    val stringBuilder = StringBuilder()
                    responseFlow.collect { chunk ->
                        stringBuilder.append(chunk.text)
                    }
                    aiResponse = stringBuilder.toString()
                }
            } catch (e: Exception) {
                aiResponse = getString(R.string.ai_chat_error_with_details, e.localizedMessage ?: "Unknown error")
            } finally {
                if (aiResponse.isNotBlank()) {
                    // --- DEĞİŞTİRİLEN SATIR: PdfViewActivity'deki listeye ekliyor. ---
                    pdfViewActivity.conversationHistory.add("Asistan: $aiResponse")
                }
                while (pdfViewActivity.conversationHistory.size > 8) {
                    pdfViewActivity.conversationHistory.removeAt(0)
                    pdfViewActivity.conversationHistory.removeAt(0)
                }

                withContext(Dispatchers.Main) {
                    thinkingIndicator.visibility = View.GONE
                    addMessage(ChatMessage(aiResponse, false))

                    if (AiQueryManager.canPerformQuery(requireContext())) {
                        enableInput()
                    } else {
                        disableInputWithQuotaMessage()
                    }
                }
                AiQueryManager.incrementQueryCount(requireContext())
            }
        }
    }

    private fun createPrompt(question: String, context: String): String {
        // --- DEĞİŞTİRİLEN SATIR: PdfViewActivity'deki listeyi kullanıyor. ---
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
        // --- DEĞİŞTİRİLEN SATIRLAR: PdfViewActivity'deki listeye ekliyor. ---
        pdfViewActivity.chatMessages.add(chatMessage)
        chatAdapter.notifyItemInserted(pdfViewActivity.chatMessages.size - 1)
        recyclerViewChat.scrollToPosition(pdfViewActivity.chatMessages.size - 1)
    }
}