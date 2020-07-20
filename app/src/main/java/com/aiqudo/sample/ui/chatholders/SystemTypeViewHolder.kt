package com.aiqudo.sample.ui.chatholders

import androidx.recyclerview.widget.RecyclerView
import com.aiqudo.sample.databinding.SystemTypeItemBinding
import com.aiqudo.sample.ui.ChatAdapter
import com.aiqudo.sample.models.ConversationItem


class SystemTypeViewHolder(
    private val binding: SystemTypeItemBinding,
    private val interaction: ChatAdapter.Interaction?
) : RecyclerView.ViewHolder(binding.root) {

    fun bind(item: ConversationItem) = with(itemView) {
        itemView.setOnClickListener {
            item.clickFunction?.invoke()
            interaction?.onItemSelected(adapterPosition, item)
        }
        binding.item = item
        binding.executePendingBindings()
    }
}