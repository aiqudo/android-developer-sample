package com.aiqudo.sample.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import com.aiqudo.actionkit.ActionKitSDK
import com.aiqudo.actionkit.assist.IExecutionContext
import com.aiqudo.actionkit.assist.IExecutionController
import com.aiqudo.actionkit.assist.IExecutionListener
import com.aiqudo.actionkit.assist.IResultListener
import com.aiqudo.actionkit.internal.assist.GsonHelper
import com.aiqudo.actionkit.models.*
import com.aiqudo.actionkit.tts.ITextToSpeech
import com.aiqudo.actionkit.tts.TtsStatusListener
import com.aiqudo.actionkit.voice.VoiceErrorType
import com.aiqudo.sample.R
import com.aiqudo.sample.databinding.ActivityMainBinding
import com.aiqudo.sample.viewmodels.MainChatViewModel
import com.aiqudo.sample.voice.VoiceRecognizerWrapper
import com.google.gson.*
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*

/**
 * Sample Activity for an implementation of Aiqudo's SDK.
 *
 * Prerequisite:
 *  Implementer needs to provide:
 *      UI to show user options for actions and dialog prompting.
 *      General way to show UI for handing speech recognition
 *
 * General flow of events:
 * 1. Open voice recognition and get user command
 * 2. Use utterance from 1 and pass to Aiqudo search
 * 3. Aiqudo search may return more than one actionable result per search.
 *     Need to further disambiguate intent by displaying action choices to user
 *     and have them select one.
 * 4. Execute the action
 * 5. Handle prompting that may occur when engaging in dialog with a user.
 *
 * Other notes:
 *  -NOT handling permissions to keep the code simpler as a sample. Make sure all permissions
 *  are turned on.
 *  -No persistence of the chat items, also to keep code simpler.
 *  -Architecturally, some things in MainActivity should be moved into the view-model, but placing
 *  it all here makes it a bit easier to follow the steps.
 */
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainChatViewModel
    private val chatAdapter = ChatAdapter()
    private val voiceRecognizer: VoiceRecognizerWrapper =
        VoiceRecognizerWrapper(
            { viewModel.setListening() }, //start listening callback
            { viewModel.setNotListening() }, //end listening callback
            { voiceErrorType -> //listening error callback

                //An error occurred, check the VoiceErrorType object for what went wrong.
                //Handle the error as needed, re-listen, etc
                //Here we just show some error UI and end the session.
                val errorMsg = when (voiceErrorType) {
                    VoiceErrorType.CANCELLED -> null
                    VoiceErrorType.SPEECH_TIMEOUT -> "You didn't say anything, please try again."
                    VoiceErrorType.NO_MATCH -> "We couldn't get that, please try again."
                    VoiceErrorType.INSUFFICIENT_PERMISSIONS -> "Audio Permission Required"
                    else -> "Something Unexpected Happened $voiceErrorType"
                }
                if (errorMsg != null) {
                    viewModel.addSystemItem(errorMsg)
                }
            }
        )
    private val textToSpeech: ITextToSpeech = ActionKitSDK.getTextToSpeech()
    private var controller: IExecutionController? = null

    /**
     * Status listener for action execution, to be used to reacting to action execution updates.
     * Used for step 4, to update any UI you may have to reflect action execution statuses.
     */
    private var actionExecutionStatusListener: IExecutionListener<out QResult> =
        object : IExecutionListener<QResult> {
            //action requires dialog with user. handle prompting here using the controller.
            override fun onPrompt(controller: IPromptController) {
                handlePrompt(controller)
            }

            //action began executing.
            override fun onStarted(exeContext: IExecutionContext<QResult>) {
                with(exeContext.executingAction) {
                    Timber.d("$displayLabel has started executing")
                    viewModel.addSystemItemWithClick(
                        "Executing \"${displayLabel}\" on ${appName}." +
                                " Tap here to re-execute it."
                    ) { executeResult(exeContext.executingAction) }
                }
            }

            //action is done executing.
            override fun onCompleted(exeContext: IExecutionContext<QResult>) {
                Timber.d("${exeContext.executingAction.displayLabel} has completed with status: ${exeContext.readableStatus}")
                handleOnCompleted(exeContext)
            }

            //action has completed a step.
            override fun onProgress(
                exeContext: IExecutionContext<QResult>,
                step: Int,
                maxStep: Int
            ) {
                Timber.d("${exeContext.executingAction.displayLabel} is on step $step of $maxStep")
            }
        }

    /**
     *  Status listener that acts on an answer status after sending an answer.
     *  Used for parameter prompting in step 5.
     *  This listener handles accepting or denying answers from users.
     *
     *  An example of input verification that the status listener handles is if a user selects
     *  a non-existent option, or an unusable input.
     */
    private val parameterPromptStatusListener = IResultListener { answerStatus: AnswerStatus ->
        when (answerStatus.type) {
            AnswerStatus.ANSWER_REJECTED_USER_CANCELLED -> {
                // Doing nothing because user has cancelled the action, this matches
                // a list of words and phrases that mean cancel.
                // delete this block if cancelling not desired.
                answerStatus.doNothing()
            }
            AnswerStatus.ANSWER_ACCEPTED -> {
                // the users answer is valid, continue execution of the action
                // (next parameter or action launch)
                answerStatus.continueExecution()
            }
            else -> {
                //else the users command was invalid. we can choose to abort with doNothing, or retry the prompt
                // Doing nothing with the below commented out line
                // answerStatus.doNothing();
                // OR retry the prompt
                viewModel.addSystemItem("We didn't get that.")
                Handler(Looper.getMainLooper()).postDelayed({ answerStatus.retryPrompt() }, 10)
            }
        }
    }

    /**
     * onCreate, setting up our ui components/observers
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(this)[MainChatViewModel::class.java]
        binding = DataBindingUtil.setContentView(
            this,
            R.layout.activity_main
        )
        binding.apply {
            lifecycleOwner = this@MainActivity
            mic.setOnClickListener {
                if (voiceRecognizer.isVoiceRecognizing()) {
                    resetMic()
                } else {
                    viewModel.clearConversation()
                    searchAndExecute()
                }
            }
            chatRecyclerView.apply {
                adapter = chatAdapter
                layoutManager = LinearLayoutManager(this@MainActivity).apply {
                    stackFromEnd = true
                    reverseLayout = true
                }
            }
            mainModel = viewModel
        }

        //updating adapter and scrolling to bottom
        var textChangedJob: Job? = null
        viewModel.conversationLiveData.observe(this,
            Observer {
                chatAdapter.submitList(ArrayList(it))
                binding.chatRecyclerView.smoothScrollToPosition(0)
                textChangedJob?.cancel()
                textChangedJob = GlobalScope.launch {
                    delay(100)
                    binding.chatRecyclerView.smoothScrollToPosition(0)
                }
            })
    }

    /**
     * Step 1.
     * Begin voice recognition and then begin disambiguation, if necessary in selectResult().
     */
    private fun searchAndExecute() {
        voiceRecognizer.listenForResult { recognitionResult ->
            //List of utterances returned here.
            //Use utterances as needed here, update UI, forward utterances to our action search API.
            val searchRequest = SearchRequest(recognitionResult, ExecutionSource.SDK)
            viewModel.addUserItem(recognitionResult.utterances[0])
            search(searchRequest)
                .doOnSuccess { selectResult(it) }
                .subscribe()
        }
    }

    /**
     * Step 2.
     * Retrieve a list of actions that match a given user command
     * Searching using a search request made by the users query.
     */
    private fun search(request: SearchRequest): Single<SearchResult> {
        return Single.fromCallable {
            ActionKitSDK.getActionKitApi().getActionsForCommand(request)
        }.map { response -> response.result }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
    }

    /**
     * Step 3.
     * Disambiguate multiple Actions that match a given command.
     * Using the results from step 2, dialog with user to determine which action to launch
     * using executeResult().
     */
    private fun selectResult(result: SearchResult) {
        //Search result has many types of results, here we are just checking actions
        //for simplicity.
        val actions = result.actions
        if (!actions.isNullOrEmpty()) {
            if (actions.size == 1) {
                executeResult(actions[0])
            } else {
                userSelectAction(actions)
            }
        } else {
            //update ui, no results.
            viewModel.addSystemItem("No results found.")
        }
    }

    /**
     * Method to display options to the user and prompt for a choice, then executes the action.
     */
    private fun userSelectAction(actions: List<Action>) {
        //Display the options to the user
        viewModel.displayActionChoices(actions)

        //Speak and then match user utterances over the action options, and execute if found.
        textToSpeech.speak("Which app or action?", Locale.US, object : TtsStatusListener {
            override fun onSpeechEnded() {
                binding.container.post {
                    voiceRecognizer.listenForResult { recognitionResult ->
                        //Updating UI
                        viewModel.addUserItem(recognitionResult.utterances[0])
                        //match and execute action
                        matchAndExecute(recognitionResult.utterances, actions)
                    }
                }
            }
        })
    }

    /**
     * Uses Aiqudo's match api and executes the action, if found.
     */
    private fun matchAndExecute(speechResults: List<String>, actions: List<Action>) {
        //Using Aiqudo select action API to match action to utterances
        val action = ActionKitSDK.getActionKitApi()
            .selectAction(speechResults, actions)

        //Execute if found, else handle error.
        if (action != null) {
            executeResult(action)
        } else {
            viewModel.addSystemItem("We couldn't get that.")
        }
    }

    /**
     * Step 4.
     * Execute the action selected from step 3.
     * Status updates and execution progress will be handled inside actionExecutionStatusListener.
     */
    private fun executeResult(action: QResult) {
        controller =
            ActionKitSDK.getActionKitApi().execute(action, actionExecutionStatusListener)
    }

    /**
     * Step 5.
     * Prompts
     * Handling the dialog prompting with the user using Aiqudo's controller APIs.
     */
    private fun handlePrompt(promptController: IPromptController) {

        // get the answer options if exists from the controller, boolean for has options
        val answerOptions: IAnswerOptions = promptController.answerOptions
        val hasOptions = answerOptions.options.isNotEmpty()
        if (hasOptions) { // this parameter has options, show to the user what they are.
            viewModel.displayOptions(answerOptions.options)
        }

        /*
         * Reading the prompt out to the user,
         * update the UI with the prompt,
         * then beginning voice recognition and forward to our prompt controller.
         */
        val prompt = promptController.prompt
        viewModel.addSystemItem(prompt)
        textToSpeech.speak(prompt, Locale.US, object : TtsStatusListener {
            override fun onSpeechEnded() {
                binding.container.post {
                    voiceRecognizer.listenForResult { recognitionResult ->
                        //Updating UI
                        viewModel.addUserItem(recognitionResult.utterances[0])
                        //Send the results to the prompt controller
                        promptController.sendAnswer(recognitionResult.utterances, parameterPromptStatusListener)
                    }
                }
            }
        })
    }


    /**
     * Handling action execution completed by updating the UI.
     */
    private fun handleOnCompleted(exeContext: IExecutionContext<QResult>) {
        viewModel.addSystemItemWithClick(getCompletedMessage(exeContext), getOnClick(exeContext))
    }

    private fun getOnClick(exeContext: IExecutionContext<QResult>): (() -> Unit)? {
        return when (exeContext.status) {
            IExecutionContext.STATUS_ACCESSIBILITY_SERVICE_NOT_RUNNING -> {
                { showAccessibilitySettings() }
            }
            IExecutionContext.STATUS_PERMISSION_NOT_GRANTED -> {
                when {
                    exeContext.getMissingPermissions(this).contains("akit:accessibility") -> {
                        { showAccessibilitySettings() }
                    }
                    exeContext.getMissingPermissions(this).contains("akit:draw_over_apps") -> {
                        { showDrawOverSettings() }
                    }
                    else -> {
                        { showSettings() }
                    }
                }
            }
            else -> null
        }
    }

    private fun getCompletedMessage(exeContext: IExecutionContext<QResult>): String {
        return when (exeContext.status) {
            IExecutionContext.STATUS_SUCCESS ->
                "Successfully executed."
            IExecutionContext.STATUS_ACCESSIBILITY_SERVICE_NOT_RUNNING ->
                "Accessibility service is not running. " +
                        "Please tap here and turn on the accessibility permission."
            IExecutionContext.STATUS_PERMISSION_NOT_GRANTED ->
                "Permissions missing. Please tap here and enable:" +
                        " ${exeContext.getMissingPermissions(this@MainActivity)} permission(s)" +
                        " in the permissions screen."
            IExecutionContext.STATUS_CANCELLED ->
                "Action cancelled."
            IExecutionContext.STATUS_EXECUTIONER_NOT_READY ->
                "The execution engine is not ready yet. could be issue related to permissions."
            else ->
                "Execution failed. reason code - ${exeContext.status}"
        }
    }

    private fun showAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun showDrawOverSettings() {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    private fun showSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    /**
     * Method to stop execution, if needed.
     */
    fun stopExecution() {
        controller?.cancel()
    }

    /**
     * Resets the microphone
     */
    private fun resetMic() {
        voiceRecognizer.cancel()
        viewModel.setNotListening()
    }
}