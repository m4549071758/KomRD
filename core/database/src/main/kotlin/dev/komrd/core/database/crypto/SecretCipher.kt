package dev.komrd.core.database.crypto

/**
 * 認証情報（秘匿バイト列）の暗号化/復号。実装はAndroid Keystore（[KeystoreSecretCipher]）だが、
 * インタフェースに切ることでマッパー層をJVM単体テストから検証できる。
 */
interface SecretCipher {
    fun encrypt(plaintext: ByteArray): EncryptedSecret

    fun decrypt(secret: EncryptedSecret): ByteArray
}

/** 暗号文とGCMのIV。永続時は[ciphertext]/[iv]をserversテーブルに保存する。 */
class EncryptedSecret(
    val ciphertext: ByteArray,
    val iv: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedSecret) return false
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int = 31 * ciphertext.contentHashCode() + iv.contentHashCode()
}
