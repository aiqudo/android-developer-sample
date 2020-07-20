package com.aiqudo.sample.ui.chatholders

import androidx.recyclerview.widget.RecyclerView
import com.aiqudo.sample.databinding.UserTypeItemBinding
import com.aiqudo.sample.ui.ChatAdapter
import com.aiqudo.sample.models.ConversationItem

class UserTypeViewHolder(
    private val binding: UserTypeItemBinding,
    private val interaction: ChatAdapter.Interaction?
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: ConversationItem) = with(itemView) {
        itemView.setOnClickListener {
            interaction?.onItemSelected(adapterPosition, item)
        }
        binding.item = item
        binding.executePendingBindings()
    }
}