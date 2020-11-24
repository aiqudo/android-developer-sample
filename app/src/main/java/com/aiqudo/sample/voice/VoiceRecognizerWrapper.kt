package com.aiqudo.sample.voice

import com.aiqudo.actionkit.ActionKitSDK
import com.aiqudo.actionkit.voice.RecognitionResult
import com.aiqudo.actionkit.voice.VoiceErrorType
import timber.log.Timber

/**
 * Wrapper class around voice recognizer
 * Takes in 3 lambdas to handle start/stop listening and error case.
 */
class VoiceRecognizerWrapper(
    private val startListening: () -> Unit,
    private val stopListening: () -> Unit,
    private val onError: (error: VoiceErrorType) -> Unit
) {
    private val voiceRecognizer = ActionKitSDK.getVoiceRecognizer()

    fun listenForResult(onDone: (RecognitionResult) -> Unit) {
        startListening()
        voiceRecognizer.startVoiceRecognition({
            Timber.d("success $it")
            stopListening()
            onDone(it)
        })
        {
            Timber.d("failure ${it.name()}")
            stopListening()
            onError(it)
        }
    }

    fun isVoiceRecognizing() = voiceRecognizer.isVoiceRecognising

    fun cancel() = voiceRecognizer.cancel()
}