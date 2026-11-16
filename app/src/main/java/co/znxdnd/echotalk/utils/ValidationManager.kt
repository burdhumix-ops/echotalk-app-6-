package co.znxdnd.echotalk.utils

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val reason: String) : ValidationResult()

    val isValid: Boolean get() = this is Valid
}

/**
 * ValidationManager: Centralized pre-validation utility for inputs, authentication,
 * permissions, connectivity, and message safety before actions execute.
 */
object ValidationManager {

    fun validateUsername(username: String): ValidationResult {
        val trimmed = username.trim()
        if (trimmed.length < 3) return ValidationResult.Invalid("Username must be at least 3 characters")
        if (trimmed.length > 20) return ValidationResult.Invalid("Username cannot exceed 20 characters")
        if (!Regex("^[a-zA-Z0-9_]+$").matches(trimmed)) {
            return ValidationResult.Invalid("Username can only contain letters, numbers, and underscores")
        }
        return ValidationResult.Valid
    }

    fun validateMessageText(text: String, maxChars: Int = 2000): ValidationResult {
        if (text.isBlank()) return ValidationResult.Invalid("Message cannot be empty")
        if (text.length > maxChars) return ValidationResult.Invalid("Message exceeds maximum length of $maxChars characters")
        return ValidationResult.Valid
    }

    fun validateBio(bio: String, maxChars: Int = 150): ValidationResult {
        if (bio.length > maxChars) return ValidationResult.Invalid("Bio cannot exceed $maxChars characters")
        return ValidationResult.Valid
    }

    fun validateAuth(): ValidationResult {
        val currentUser = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
        if (currentUser == null) {
            return ValidationResult.Invalid("User is not signed in to Firebase Authentication")
        }
        return ValidationResult.Valid
    }

    fun validatePermission(context: Context, permission: String): ValidationResult {
        val status = ContextCompat.checkSelfPermission(context, permission)
        return if (status == PackageManager.PERMISSION_GRANTED) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Required permission not granted: $permission")
        }
    }

    fun validateNetwork(context: Context): ValidationResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNet = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNet)
        val hasNet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

        return if (hasNet) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid("Network connection unavailable")
        }
    }

    fun sanitizeText(input: String): String {
        return input.trim().replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F]"), "")
    }
}
