package dev.komrd.core.database.mapper

import dev.komrd.core.database.crypto.EncryptedSecret
import dev.komrd.core.database.crypto.SecretCipher
import dev.komrd.core.database.entity.ServerEntity
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Server

private const val AUTH_API_KEY = "API_KEY"
private const val AUTH_BASIC = "BASIC"

/** ドメイン[Server] → 永続[ServerEntity]。秘匿値を[cipher]で暗号化して格納する。 */
fun Server.toEntity(
    cipher: SecretCipher,
    createdAt: Long,
): ServerEntity {
    val (authType, username, secret) =
        when (val method = auth) {
            is AuthMethod.ApiKey -> Triple(AUTH_API_KEY, null, method.key)
            is AuthMethod.Basic -> Triple(AUTH_BASIC, method.username, method.password)
        }
    val encrypted = cipher.encrypt(secret.encodeToByteArray())
    return ServerEntity(
        id = id,
        name = name,
        baseUrl = baseUrl,
        authType = authType,
        username = username,
        secretCiphertext = encrypted.ciphertext,
        secretIv = encrypted.iv,
        createdAt = createdAt,
    )
}

/** 永続[ServerEntity] → ドメイン[Server]。秘匿値を[cipher]で復号する。 */
fun ServerEntity.toDomain(cipher: SecretCipher): Server {
    val secret = cipher.decrypt(EncryptedSecret(secretCiphertext, secretIv)).decodeToString()
    val auth =
        when (authType) {
            AUTH_API_KEY -> AuthMethod.ApiKey(secret)
            AUTH_BASIC ->
                AuthMethod.Basic(
                    username = requireNotNull(username) { "BASIC認証のserver($id)にusernameがない" },
                    password = secret,
                )
            else -> error("未知のauthType: $authType (server=$id)")
        }
    return Server(id = id, name = name, baseUrl = baseUrl, auth = auth)
}
