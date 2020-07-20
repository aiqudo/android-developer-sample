package com.aiqudo.sample.ui

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import com.aiqudo.sample.databinding.SystemTypeItemBinding
import com.aiqudo.sample.databinding.UserTypeItemBinding
import com.aiqudo.sample.models.ConversationItem
import com.aiqudo.sample.ui.chatholders.SystemTypeViewHolder
import com.aiqudo.sample.ui.chatholders.UserTypeViewHolder

/**
 * Adapter class to hold our chat items
 */
class ChatAdapter(private val interaction: Interaction? = null) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val diffCallback =
        object : DiffUtil.ItemCallback<ConversationItem>() {
            override fun areItemsTheSame(
                oldItem: ConversationItem,
                newItem: ConversationItem
            ): Boolean {
                return oldItem == newItem
            }

            override fun areContentsTheSame(
                oldItem: ConversationItem,
                newItem: ConversationItem
            ): Boolean {
                return oldItem == newItem
            }
        }

    private val differ = AsyncListDiffer(this, diffCallback)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ConversationItem.TYPE_USER -> {
                UserTypeViewHolder(
                    UserTypeItemBinding.inflate(layoutInflater, parent, false),
                    interaction
                )
            }
            else -> {
                SystemTypeViewHolder(
                    SystemTypeItemBinding.inflate(layoutInflater, parent, false),
                    interaction
                )
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is UserTypeViewHolder -> {
                holder.bind(differ.currentList[position])
            }
            is SystemTypeViewHolder -> {
                holder.bind(differ.currentList[position])
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return differ.currentList[position].type
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    fun submitList(list: List<ConversationItem>) {
        differ.submitList(list)
    }

    interface Interaction {
        fun onItemSelected(position: Int, item: ConversationItem)
    }
}