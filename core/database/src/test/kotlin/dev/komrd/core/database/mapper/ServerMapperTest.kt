package dev.komrd.core.database.mapper

import dev.komrd.core.database.crypto.EncryptedSecret
import dev.komrd.core.database.crypto.SecretCipher
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Server
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 可逆な単純変換でKeystoreを使わずマッパーの暗号化/復号オーケストレーションを検証する。 */
private class ShiftCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray) = EncryptedSecret(shift(plaintext, 1), byteArrayOf(0x7F))

    override fun decrypt(secret: EncryptedSecret) = shift(secret.ciphertext, -1)

    private fun shift(
        bytes: ByteArray,
        by: Int,
    ) = ByteArray(bytes.size) { (bytes[it] + by).toByte() }
}

class ServerMapperTest {
    private val cipher = ShiftCipher()

    @Test
    fun apiKey_server_roundtrips() {
        val server = Server("s1", "Home", "https://komga.example", AuthMethod.ApiKey("secret-key"))
        val restored = server.toEntity(cipher, createdAt = 1L).toDomain(cipher)
        assertEquals(server, restored)
    }

    @Test
    fun basic_server_roundtrips() {
        val server = Server("s2", "Work", "https://k.example", AuthMethod.Basic("alice", "pw123"))
        val restored = server.toEntity(cipher, createdAt = 2L).toDomain(cipher)
        assertEquals(server, restored)
    }

    @Test
    fun secret_isStoredEncrypted_notPlaintext() {
        val server = Server("s3", "X", "https://x", AuthMethod.ApiKey("plainkey"))
        val entity = server.toEntity(cipher, createdAt = 3L)
        assertFalse(entity.secretCiphertext.contentEquals("plainkey".encodeToByteArray()))
    }
}
