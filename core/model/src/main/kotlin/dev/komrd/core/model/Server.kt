package dev.komrd.core.model

/**
 * 登録された1つのKomgaインスタンス。認証情報・キャッシュ・進捗を分離する単位（CONTEXT: Server）。
 * [id] はサーバ別名前空間（キャッシュ/進捗）の安定キーとして使う不変のserverId。
 */
data class Server(
    val id: String,
    val name: String,
    val baseUrl: String,
    val auth: AuthMethod,
)

sealed interface AuthMethod {
    /** APIキー: `X-API-Key` を毎リクエスト付与（ステートレス）。 */
    data class ApiKey(
        val key: String,
    ) : AuthMethod {
        override fun toString(): String = "ApiKey(key=••••)"
    }

    /** Basic: 初回認証で `X-Auth-Token` セッションを取得し再利用。 */
    data class Basic(
        val username: String,
        val password: String,
    ) : AuthMethod {
        override fun toString(): String = "Basic(username=$username, password=••••)"
    }
}
