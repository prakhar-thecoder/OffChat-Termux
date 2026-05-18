package com.appholaworld.offchat.activities

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.appholaworld.offchat.OffChatApp
import com.termux.R
import com.termux.databinding.ActivityAuthBinding
import com.appholaworld.offchat.viewmodels.AuthState
import com.appholaworld.offchat.viewmodels.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthActivity : BaseActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var preferenceManager: com.appholaworld.offchat.utils.PreferenceManager
    private val viewModel: AuthViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = (application as OffChatApp).onlineChatRepository
                return AuthViewModel(repository, applicationContext) as T
            }
        }
    }

    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                viewModel.setAuthLoading(false)
                // Log the exact API exception status code to pinpoint the failure
                Toast.makeText(this@AuthActivity, "Auth failed. Code: ${e.statusCode}", Toast.LENGTH_LONG).show()
                android.util.Log.e("AuthActivity", "Google Sign-In failed", e)
            }
        } else {
            viewModel.setAuthLoading(false)
            // Warn when the intent gets canceled or fails before returning OK
            Toast.makeText(this@AuthActivity, "Canceled/Failed. ResultCode: ${result.resultCode}", Toast.LENGTH_LONG).show()
            android.util.Log.w("AuthActivity", "Result not OK. Code: ${result.resultCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = com.appholaworld.offchat.utils.PreferenceManager(this)
        setupTermsText()
        setupListeners()
        observeViewModel()
    }

    private fun setupTermsText() {
        val fullText = "I agree to the Terms & Conditions"
        val termsText = "Terms & Conditions"
        val spannable = SpannableString(fullText)

        val startIndex = fullText.indexOf(termsText)
        val endIndex = startIndex + termsText.length

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                showTermsDialog()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = getColor(R.color.primary)
                ds.isUnderlineText = false
                ds.isFakeBoldText = true
            }
        }

        spannable.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.tvTerms.text = spannable
        binding.tvTerms.movementMethod = LinkMovementMethod.getInstance()
        binding.tvTerms.highlightColor = Color.TRANSPARENT
    }

    private fun setupListeners() {
        binding.cbTerms.setOnCheckedChangeListener { _, isChecked ->
            binding.btnGoogleSignIn.isEnabled = isChecked
            if (isChecked) {
                showTermsDialog()
            }
        }

        binding.btnGoogleSignIn.setOnClickListener {
            startGoogleSignIn()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: AuthState) {
        binding.authProgressBar.visibility = if (state is AuthState.Loading) View.VISIBLE else View.GONE
        binding.btnGoogleSignIn.isEnabled = state !is AuthState.Loading && binding.cbTerms.isChecked

        when (state) {
            is AuthState.NeedsUsername -> showUsernameDialog()
            is AuthState.Authenticated -> {
                // Save profile locally to avoid being redirected to profile activity by MainActivity
                val localUser = com.appholaworld.offchat.models.User(
                    id = state.uid,
                    name = state.username,
                    phoneNumber = "", // Not needed for online mode currently
                    deviceName = android.os.Build.MODEL,
                    isProfileCompleted = true
                )
                preferenceManager.saveUser(localUser)
                preferenceManager.setTermsAccepted(true)

                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            is AuthState.Error -> {
                Toast.makeText(this@AuthActivity, state.message, Toast.LENGTH_LONG).show()
            }
            else -> {}
        }
    }

    private fun startGoogleSignIn() {
        viewModel.setAuthLoading(true)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        googleSignInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        lifecycleScope.launch {
            try {
                FirebaseAuth.getInstance().signInWithCredential(credential).await()
                viewModel.onSignInSuccess()
            } catch (e: Exception) {
                viewModel.setAuthLoading(false)
                Toast.makeText(this@AuthActivity, "Firebase auth failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTermsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_terms, null)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnClose)
        val tvTermsContent = dialogView.findViewById<TextView>(R.id.tvTermsContent)

        tvTermsContent.text = androidx.core.text.HtmlCompat.fromHtml(
            getString(R.string.offchat_terms_html),
            androidx.core.text.HtmlCompat.FROM_HTML_MODE_COMPACT
        )

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()

        dialog.window?.let { window ->
            val displayMetrics = resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.95).toInt()
            val height = (displayMetrics.heightPixels * 0.90).toInt()
            window.setLayout(width, height)
        }
    }

    private fun showUsernameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom_input, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvDialogDescription)
        val tilInput = dialogView.findViewById<TextInputLayout>(R.id.tilInput)
        val etInput = dialogView.findViewById<TextInputEditText>(R.id.etInput)
        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSubmit = dialogView.findViewById<MaterialButton>(R.id.btnSubmit)

        tvTitle.text = "Set Username"
        tvDescription.text = "Choose a unique username to be discovered by others."
        tilInput.hint = "Username"
        btnCancel.visibility = View.GONE

        val dialog = AlertDialog.Builder(this, R.style.Theme_OffChat_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnSubmit.setOnClickListener {
            val username = etInput.text.toString().trim()
            if (username.length >= 3) {
                viewModel.submitUsername(username)
                dialog.dismiss()
            } else {
                etInput.error = "Minimum 3 characters"
            }
        }

        dialog.show()
    }
}
