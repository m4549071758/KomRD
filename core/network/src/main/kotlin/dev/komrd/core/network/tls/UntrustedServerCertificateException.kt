package dev.komrd.core.network.tls

import dev.komrd.core.common.error.CertificateInfo
import java.security.cert.CertificateException

class UntrustedServerCertificateException(
    val certificateInfo: CertificateInfo?,
    message: String,
    cause: Throwable?,
) : CertificateException(message, cause)
