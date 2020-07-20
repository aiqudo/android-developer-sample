package com.aiqudo.sample.models

data class ConversationItem(
    val type: Int,
    val text: String
) {
    var clickFunction: (() -> Unit)? = null

    companion object {
        const val TYPE_USER = 0
        const val TYPE_SYSTEM = 1

        fun createSystemTypeItem(text: String): ConversationItem {
            return ConversationItem(
                TYPE_SYSTEM,
                text
            )
        }

        fun createUserTypeItem(text: String): ConversationItem {
            return ConversationItem(
                TYPE_USER,
                text
            )
        }
    }
}