package com.nqutaoitfteikc.notehub.privacy

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM; key never leaves Android Keystore. Authentication failures never return plaintext. */
class LocalVault(private val alias: String = "notehub-photo-v1") {
    private val secret: SecretKey by lazy { synchronized(lock) {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    } }
    fun encrypted(bytes: ByteArray)=bytes.size>=32 && bytes.take(4).toByteArray().contentEquals(magic)
    fun seal(bytes: ByteArray, context: String): ByteArray {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,secret); cipher.updateAAD(context.toByteArray())
        return magic+cipher.iv+cipher.doFinal(bytes)
    }
    fun open(bytes: ByteArray, context: String): ByteArray {
        require(encrypted(bytes)) { "加密資料格式不正確" }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,secret,GCMParameterSpec(128,bytes.copyOfRange(4,16))); cipher.updateAAD(context.toByteArray())
        return cipher.doFinal(bytes,16,bytes.size-16)
    }
    companion object { private val lock=Any(); private val magic=byteArrayOf(78,72,86,49) }
}
