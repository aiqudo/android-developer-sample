package com.aiqudo.sample

import android.app.Application
import com.aiqudo.actionkit.ActionKitSDK
import com.aiqudo.actionkit.IActionKitCreator
import com.aiqudo.actionkit.models.*
import timber.log.Timber

class AiqudoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        ActionKitSDK.initSDK(actionKitCreator)
    }

    private val actionKitCreator = object : IActionKitCreator {
        override fun getActionKitAuthenticator(): ActionKitAuthenticator {
            return object : CredentialAuthenticator {
                /* TODO
                 * Put your id/key here.
                 */
                override fun getPartnerId() = ""
                override fun getPartnerSecret() = ""
            }
        }

        override fun getFloaterConfig() = FloaterConfig(R.drawable.fab_blue_mic)

        override fun getContext() = this@AiqudoApplication

        override fun getOverlayConfig() = OverlayConfig()

        override fun getActionKitConfig(): ActionKitConfig {
            return ActionKitConfig().apply {
                features.apply {
                    /*
                     * Add these flags if you want dialog actions to return from search.
                     * If turned on, you will need to handle prompting in your code.
                     */
                    areGeneralContactPromptsEnabled = true
                    arePromptsEnabled = true
                    areConfirmationsEnabled = true
                    enableDialogActions = true
                }
            }
        }
    }
}