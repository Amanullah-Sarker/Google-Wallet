package com.amanullah.googlewallet.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.amanullah.googlewallet.util.showLog
import com.amanullah.googlewallet.util.showToast
import com.amanullah.googlewallet.viewmodel.CheckoutViewModel
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayClient
import com.google.android.gms.samples.wallet.R
import com.google.android.gms.samples.wallet.databinding.ActivityCheckoutBinding
import com.google.android.gms.wallet.PaymentData
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * Checkout implementation for the app
 */
class CheckoutActivity : AppCompatActivity() {

    private val addToGoogleWalletRequestCode = 1000

    private val viewModel: CheckoutViewModel by viewModels()

    private lateinit var payClient: PayClient

    private lateinit var binding: ActivityCheckoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        payClient = Pay.getClient(this)

        // Setup buttons
        binding.googlePayButton.root.setOnClickListener { requestPayment() }

        binding.addToGoogleWalletButton.root.setOnClickListener {
            savePasses()
            /*requestSavePass()*/
        }

        // Check Google Pay availability
        viewModel.canUseGooglePay.observe(this, Observer(::setGooglePayAvailable))
        /*viewModel.canSavePasses.observe(this, Observer(::setAddToGoogleWalletAvailable))*/
    }

    /**
     * If isReadyToPay returned `true`, show the button and hide the "checking" text. Otherwise,
     * notify the user that Google Pay is not available. Please adjust to fit in with your current
     * user flow. You are not required to explicitly let the user know if isReadyToPay returns `false`.
     *
     * @param available isReadyToPay API response.
     */
    private fun setGooglePayAvailable(available: Boolean) {
        if (available) {
            binding.googlePayButton.root.visibility = View.VISIBLE
        } else {
            Toast.makeText(
                this,
                R.string.google_pay_status_unavailable,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * If the Google Wallet API is available, show the button to Add to Google Wallet. Please adjust to fit
     * in with your current user flow.
     *
     * @param available
     */
    private fun setAddToGoogleWalletAvailable(available: Boolean) {
        if (available) {
            binding.passContainer.visibility = View.VISIBLE
        } else {
            Toast.makeText(
                this,
                R.string.google_wallet_status_unavailable,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun requestPayment() {

        // Disables the button to prevent multiple clicks.
        binding.googlePayButton.root.isClickable = false

        // The price provided to the API should include taxes and shipping.
        // This price is not displayed to the user.
        val dummyPriceCents = 100L
        val shippingCostCents = 900L
        val task = viewModel.getLoadPaymentDataTask(dummyPriceCents + shippingCostCents)

        task.addOnCompleteListener { completedTask ->
            if (completedTask.isSuccessful) {
                completedTask.result.let(::handlePaymentSuccess)
            } else {
                when (val exception = completedTask.exception) {
                    is ResolvableApiException -> {
                        resolvePaymentForResult.launch(
                            IntentSenderRequest.Builder(exception.resolution).build()
                        )
                    }
                    is ApiException -> {
                        handleError(exception.statusCode, exception.message)
                    }
                    else -> {
                        handleError(
                            CommonStatusCodes.INTERNAL_ERROR, "Unexpected non API" +
                                    " exception when trying to deliver the task result to an activity!"
                        )
                    }
                }
            }

            // Re-enables the Google Pay payment button.
            binding.googlePayButton.root.isClickable = true
        }
    }

    // Handle potential conflict from calling loadPaymentData
    private val resolvePaymentForResult =
        registerForActivityResult(StartIntentSenderForResult()) { result: ActivityResult ->
            when (result.resultCode) {
                RESULT_OK ->
                    result.data?.let { intent ->
                        PaymentData.getFromIntent(intent)?.let(::handlePaymentSuccess)
                    }

                RESULT_CANCELED -> {
                    // The user cancelled the payment attempt
                }
            }
        }

    /**
     * PaymentData response object contains the payment information, as well as any additional
     * requested information, such as billing and shipping address.
     *
     * @param paymentData A response object returned by Google after a payer approves payment.
     * @see [Payment
     * Data](https://developers.google.com/pay/api/android/reference/object.PaymentData)
     */
    private fun handlePaymentSuccess(paymentData: PaymentData) {
        val paymentInformation = paymentData.toJson()

        try {
            // Token will be null if PaymentDataRequest was not constructed using fromJson(String).
            val paymentMethodData =
                JSONObject(paymentInformation).getJSONObject("paymentMethodData")
            val billingName = paymentMethodData.getJSONObject("info")
                .getJSONObject("billingAddress").getString("name")
            billingName.showLog()

            Toast.makeText(
                this,
                getString(R.string.payments_show_name, billingName),
                Toast.LENGTH_LONG
            ).show()

            // Logging token string.
            paymentMethodData
                .getJSONObject("tokenizationData")
                .getString("token").showLog()

        } catch (error: JSONException) {
            Log.e("handlePaymentSuccess", "Error: $error")
        }
    }

    /**
     * At this stage, the user has already seen a popup informing them an error occurred. Normally,
     * only logging is required.
     *
     * @param statusCode will hold the value of any constant from CommonStatusCode or one of the
     * WalletConstants.ERROR_CODE_* constants.
     * @see [
     * Wallet Constants Library](https://developers.google.com/android/reference/com/google/android/gms/wallet/WalletConstants.constant-summary)
     */
    private fun handleError(statusCode: Int, message: String?) {
        Log.e("Google Pay API error", "Error code: $statusCode, Message: $message")
    }

    private fun savePasses() {
        payClient.savePasses(newObjectJson, this, addToGoogleWalletRequestCode)
    }

    private fun requestSavePass() {

        // Disables the button to prevent multiple clicks.
        binding.addToGoogleWalletButton.root.isClickable = false

        viewModel.savePassesJwt(viewModel.genericObjectJwt, this, addToGoogleWalletRequestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == addToGoogleWalletRequestCode) {
            when (resultCode) {
                RESULT_OK -> getString(R.string.add_google_wallet_success).showToast(this)

                RESULT_CANCELED -> {
                    // Save canceled
                    getString(R.string.add_google_wallet_canceled).showToast(this)
                }

                PayClient.SavePassesResult.SAVE_ERROR -> data?.let { intentData ->
                    val apiErrorMessage =
                        intentData.getStringExtra(PayClient.EXTRA_API_ERROR_MESSAGE)
                    handleError(resultCode, apiErrorMessage)
                }

                else -> {
                    getString(R.string.something_went_wrong).showToast(this)

                    handleError(
                        CommonStatusCodes.INTERNAL_ERROR, "Unexpected non API" +
                                " exception when trying to deliver the task result to an activity!"
                    )
                }
            }

            // Re-enables the Google Pay payment button.
            binding.addToGoogleWalletButton.root.isClickable = true
        }
    }
    private val issuerEmail = "amanullahsarker.amiprobashi@gmail.com"
    private val issuerId = "3388000000022209204"

    private val passClass = "3388000000022209204.6abee19a-f801-4bd1-909d-ce461964820d"

    private val passId = UUID.randomUUID().toString()

    private val newObjectJson = """{
  "aud": "google",
  "origins": [],
  "iss": "$issuerEmail",
  "iat": ${Date().time / 1000L},
  "typ": "savetowallet",
  "payload": {
    "loyaltyObjects": [
      {
        "barcode": {
          "alternateText": "12345",
          "type": "qrCode",
          "value": "28343E3"
        },
        "linksModuleData": {
          "uris": [
            {
              "kind": "walletobjects#uri",
              "uri": "https://www.baconrista.com/myaccount?id=1234567890",
              "description": "My Baconrista Account"
            }
          ]
        },
        "infoModuleData": {
          "labelValueRows": [
            {
              "columns": [
                {
                  "value": "Jane Doe",
                  "label": "Member Name"
                },
                {
                  "value": "1234567890",
                  "label": "Membership #"
                }
              ]
            },
            {
              "columns": [
                {
                  "value": "2 coffees",
                  "label": "Next Reward in"
                },
                {
                  "value": "01/15/2013",
                  "label": "Member Since"
                }
              ]
            }
          ],
          "showLastUpdateTime": "true"
        },
        "id": "$issuerId.$passId",
        "loyaltyPoints": {
          "balance": {
            "string": "500"
          },
          "label": "Points"
        },
        "accountId": "1234567890",
        "classId": "$passClass",
        "accountName": "Jane Doe",
        "state": "active",
        "version": 1,
        "textModulesData": [
          {
            "body": "You are 5 coffees away from receiving a free bacon fat latte. ",
            "header": "Jane's Baconrista Rewards"
          }
        ]
      }
    ]
  }
}"""
}
