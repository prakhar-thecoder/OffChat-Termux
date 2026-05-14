package com.appholaworld.offchat.fragments

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.termux.R
import com.appholaworld.offchat.activities.AuthActivity
import com.termux.databinding.FragmentAccountBinding
import com.termux.databinding.DialogEditProfileBinding
import com.appholaworld.offchat.models.User
import com.appholaworld.offchat.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth

class AccountFragment : Fragment() {

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager(requireContext())
        setupUI()
    }

    private fun setupUI() {
        refreshUserInfo()
        
        binding.tvDeviceModel.text = "Version 1.0.0 (${android.os.Build.MODEL})"

        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }
        
        binding.btnMeshStats.setOnClickListener {
            Toast.makeText(context, "Mesh Stats feature coming soon!", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnPrivacy.setOnClickListener {
            Toast.makeText(context, "Privacy settings are locked for security", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnAbout.setOnClickListener {
            Toast.makeText(context, "OffChat - Peer-to-Peer Offline Messenger", Toast.LENGTH_SHORT).show()
        }

        binding.btnLogout.setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext(), R.style.Theme_OffChat_Dialog)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                FirebaseAuth.getInstance().signOut()
                preferenceManager.clear()
                val intent = Intent(requireActivity(), AuthActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshUserInfo() {
        val user = preferenceManager.getUser()
        if (user != null) {
            binding.tvUserNameDisplay.text = user.name
            binding.tvUserPhoneDisplay.text = user.phoneNumber
        } else {
            binding.tvUserNameDisplay.text = "Guest User"
            binding.tvUserPhoneDisplay.text = "Tap Edit to set up profile"
        }
    }

    private fun showEditProfileDialog() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogEditProfileBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val currentUser = preferenceManager.getUser()
        if (currentUser != null) {
            dialogBinding.etName.setText(currentUser.name)
            dialogBinding.etPhone.setText(currentUser.phoneNumber)
        }

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSave.setOnClickListener {
            val newName = dialogBinding.etName.text.toString().trim()
            val newPhone = dialogBinding.etPhone.text.toString().trim()

            if (newName.isNotEmpty() && newPhone.isNotEmpty()) {
                val updatedUser = User(
                    id = currentUser?.id ?: java.util.UUID.randomUUID().toString(),
                    name = newName,
                    phoneNumber = newPhone,
                    deviceName = android.os.Build.MODEL,
                    isProfileCompleted = true
                )
                preferenceManager.saveUser(updatedUser)
                refreshUserInfo()
                Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Fields cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
