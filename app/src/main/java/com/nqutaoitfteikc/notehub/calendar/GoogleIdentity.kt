package com.nqutaoitfteikc.notehub.calendar

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.*
import com.google.android.libraries.identity.googleid.*
import com.google.android.gms.auth.api.identity.*
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val CALENDAR_READ_SCOPES = listOf("https://www.googleapis.com/auth/calendar.calendarlist.readonly", "https://www.googleapis.com/auth/calendar.events.readonly")
class GoogleIdentity(private val context: Context, val clientId: String) {
    private val credentials = CredentialManager.create(context)
    private val authorization = Identity.getAuthorizationClient(context)
    suspend fun signIn(activity: Activity): String {
        check(clientId.isNotBlank()) { "尚未設定 Google Web OAuth client ID，請依 GOOGLE_SETUP.md 完成設定後重新建置" }
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val response = credentials.getCredential(activity, GetCredentialRequest.Builder().addCredentialOption(option).build())
        val credential = response.credential
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Google 登入資料類型不符，請重試" }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        // ID token is deliberately not retained or sent anywhere; GIS binds authorization to this account.
        return google.id.trim().lowercase(java.util.Locale.ROOT)
    }
    suspend fun authorize(email: String): AuthorizationResult = authorization.authorize(
        AuthorizationRequest.builder().setAccount(Account(email, "com.google"))
            .setRequestedScopes(CALENDAR_READ_SCOPES.map(::Scope)).build()).awaitResult()
    fun resolve(data: Intent): AuthorizationResult = authorization.getAuthorizationResultFromIntent(data)
    fun token(result: AuthorizationResult): String {
        check(result.grantedScopes.containsAll(CALENDAR_READ_SCOPES)) { "尚未授予所有行事曆唯讀權限" }
        return result.accessToken?.takeIf { it.isNotBlank() } ?: error("未取得 Google 存取授權，請重新授權")
    }
    suspend fun clearExpiredToken(token: String) { authorization.clearToken(ClearTokenRequest.builder().setToken(token).build()).awaitResult() }
    suspend fun signOut() { credentials.clearCredentialState(ClearCredentialStateRequest()) }
}
private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
