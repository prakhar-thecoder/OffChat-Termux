package com.appholaworld.offchat.activities

import android.content.Intent
import android.os.Bundle
import com.termux.databinding.ActivityTermsBinding
import com.appholaworld.offchat.utils.PreferenceManager

class TermsActivity : BaseActivity() {

    private lateinit var binding: ActivityTermsBinding
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTermsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferenceManager = PreferenceManager(this)
        setStatusBarColor(com.termux.R.color.background)

        binding.btnAccept.setOnClickListener {
            preferenceManager.setTermsAccepted(true)
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
