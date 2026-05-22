package com.example.zejioscafese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.zejioscafese.databinding.ActivityLoginBinding
import com.example.zejioscafese.ui.showErrorDialog
import com.example.zejioscafese.ui.showWarningDialog
import java.security.MessageDigest
import java.util.Locale

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (isRememberedSession()) {
            openMainActivity()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            attemptLogin()
        }

        binding.etLoginPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }
    }

    private fun attemptLogin() {
        val username = binding.etLoginUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etLoginPassword.text?.toString().orEmpty()

        binding.usernameInputLayout.error = null
        binding.passwordInputLayout.error = null

        if (username.isBlank() || password.isBlank()) {
            if (username.isBlank()) {
                binding.usernameInputLayout.error = getString(R.string.login_username_required)
            }
            if (password.isBlank()) {
                binding.passwordInputLayout.error = getString(R.string.login_password_required)
            }
            showWarningDialog(this, getString(R.string.login_empty_fields))
            return
        }

        if (!isCredentialConfigPresent()) {
            binding.passwordInputLayout.error = getString(R.string.login_credentials_not_configured)
            showErrorDialog(this, getString(R.string.login_credentials_not_configured))
            return
        }

        if (isValidCredential(username, password)) {
            setRememberedSession(binding.checkboxRemember.isChecked)
            openMainActivity()
        } else {
            binding.passwordInputLayout.error = getString(R.string.login_invalid_credentials)
            binding.etLoginPassword.text?.clear()
            showErrorDialog(this, getString(R.string.login_invalid_credentials))
        }
    }

    private fun isCredentialConfigPresent(): Boolean {
        return BuildConfig.LOGIN_USERNAME.isNotBlank() && BuildConfig.LOGIN_PASSWORD_SHA256.isNotBlank()
    }

    private fun isValidCredential(username: String, password: String): Boolean {
        val configuredUsername = BuildConfig.LOGIN_USERNAME.trim()
        val configuredPasswordHash = BuildConfig.LOGIN_PASSWORD_SHA256.trim().lowercase(Locale.US)
        val enteredPasswordHash = sha256(password)
        return username == configuredUsername &&
            MessageDigest.isEqual(
                enteredPasswordHash.toByteArray(),
                configuredPasswordHash.toByteArray()
            )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun openMainActivity() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun isRememberedSession(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMEMBER_SESSION, false)
    }

    private fun setRememberedSession(remember: Boolean) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REMEMBER_SESSION, remember)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "zejios_login"
        private const val KEY_REMEMBER_SESSION = "remember_session"

        fun clearRememberedSession(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_REMEMBER_SESSION)
                .apply()
        }
    }
}
