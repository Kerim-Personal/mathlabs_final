package com.codenzi.mathlabs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val chatMessages: MutableList<ChatMessage>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_TYPE_USER = 1
    private val VIEW_TYPE_AI = 2

    inner class UserMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val userMessageTextView: TextView = view.findViewById(R.id.textViewUserMessage)
    }

    inner class AiMessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val aiMessageTextView: TextView = view.findViewById(R.id.textViewAiMessage)
    }

    override fun getItemViewType(position: Int): Int {
        return if (chatMessages[position].isFromUser) VIEW_TYPE_USER else VIEW_TYPE_AI
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_USER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_user, parent, false)
            UserMessageViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_ai, parent, false)
            AiMessageViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = chatMessages[position]
        if (holder is UserMessageViewHolder) {
            holder.userMessageTextView.text = message.message
        } else if (holder is AiMessageViewHolder) {
            holder.aiMessageTextView.text = message.message
        }
    }

    override fun getItemCount(): Int = chatMessages.size
}