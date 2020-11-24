package com.aiqudo.sample.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.aiqudo.actionkit.models.Action
import com.aiqudo.actionkit.models.Option
import com.aiqudo.sample.models.ConversationItem
import kotlin.math.min

class MainChatViewModel : ViewModel() {
    private var _conversationLiveData = MutableLiveData<MutableList<ConversationItem>>()
    val conversationLiveData: LiveData<MutableList<ConversationItem>> = _conversationLiveData

    private var _listeningLiveData = MutableLiveData(false)
    val listeningLiveData: LiveData<Boolean> = _listeningLiveData

    init {
        _conversationLiveData.value = mutableListOf()
    }

    fun addSystemItem(text: String) {
        addConversationItem(ConversationItem.createSystemTypeItem(text))
    }

    fun addSystemItemWithClick(text: String, onClick: (() -> Unit)?) {
        addConversationItem(
            ConversationItem.createSystemTypeItem(text).apply { clickFunction = onClick }
        )
    }

    fun addUserItem(text: String) {
        addConversationItem(ConversationItem.createUserTypeItem(text))
    }

    fun setListening() {
        _listeningLiveData.value = true
    }

    fun setNotListening() {
        _listeningLiveData.value = false
    }

    /**
     * Method to display parameter options to the user
     */
    fun displayOptions(options: List<Option>) {
        val displaySb = StringBuilder("We found the following options:\n")
        for (option in options) {
            displaySb.append("\n${option.getTitle()}: ${option.value}")
        }
        addSystemItem(displaySb.toString())
    }

    /**
     * Method to display action choices to the user
     */
    fun displayActionChoices(actions: List<Action>) {
        val displaySb = StringBuilder("We found actions from the following apps:\n")
        for (x in 0..min(actions.lastIndex, 2)) {
            val action = actions[x]
            displaySb.append("\n${action.appName}: ${action.displayLabel}")
        }
        addSystemItem(displaySb.toString())
    }

    fun clearConversation() {
        _conversationLiveData.value?.clear()
        _conversationLiveData.notifyObserver()
    }

    private fun addConversationItem(item: ConversationItem) {
        _conversationLiveData.value?.add(0, item)
        _conversationLiveData.notifyObserver()
    }

    private fun <T> MutableLiveData<T>.notifyObserver() {
        this.value = this.value
    }
}