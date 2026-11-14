package com.example.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

/**
 * Formats an error for logging. Retained for API compatibility with the
 * original Firestore-backed repository; the auth-specific payload is gone
 * because the app no longer uses Firebase Auth.
 */
fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it !is CancellationException } ?: exception

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", JSONObject().apply {
            put("userId", null)
            put("mode", "local-device")
        })
    }

    val jsonString = errorInfoJson.toString()
    Log.e("GradePulseError", "Storage Error: $jsonString")
    return jsonString
}
