package com.kire.remotecontroller.atv

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

object CertGenerator {
    private const val ALIAS = "atvremote"

    data class CertMaterial(val certPem: String, val keyPem: String, val cert: Certificate)

    fun loadOrCreate(context: Context, host: String): CertMaterial {
        val dir = File(context.filesDir, "certs")
        dir.mkdirs()
        val safeHost = host.replace('.', '_')
        val certFile = File(dir, "$safeHost.crt.pem")
        val keyFile = File(dir, "$safeHost.key.pem")
        if (certFile.exists() && keyFile.exists()) {
            return runCatching {
                val certPem = certFile.readText()
                val keyPem = keyFile.readText()
                CertMaterial(certPem, keyPem, readCertificate(certPem))
            }.getOrElse {
                certFile.delete()
                keyFile.delete()
                generateAndSave(certFile, keyFile, "Philips Remote")
            }
        }
        return generateAndSave(certFile, keyFile, "Philips Remote")
    }

    private fun generateAndSave(certFile: File, keyFile: File, commonName: String): CertMaterial {
        val material = generate(commonName)
        certFile.writeText(material.certPem)
        keyFile.writeText(material.keyPem)
        return material
    }

    private fun generate(commonName: String): CertMaterial {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(2048, SecureRandom())
        }.generateKeyPair()

        val now = System.currentTimeMillis()
        val holder = JcaX509v3CertificateBuilder(
            X500Name("CN=$commonName"),
            BigInteger.valueOf(now),
            Date(now - 86_400_000),
            Date(now + 365L * 86_400_000 * 10),
            X500Name("CN=$commonName"),
            keyPair.public,
        ).build(
            JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private),
        )

        val cert = java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(holder.encoded.inputStream())

        val certPem = buildString {
            append("-----BEGIN CERTIFICATE-----\n")
            append(android.util.Base64.encodeToString(holder.encoded, android.util.Base64.NO_WRAP))
            append("\n-----END CERTIFICATE-----\n")
        }
        val keyPem = buildString {
            append("-----BEGIN PRIVATE KEY-----\n")
            append(android.util.Base64.encodeToString(keyPair.private.encoded, android.util.Base64.NO_WRAP))
            append("\n-----END PRIVATE KEY-----\n")
        }
        return CertMaterial(certPem, keyPem, cert)
    }

    private fun readCertificate(pem: String): Certificate {
        val body = pem
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace("\n", "")
            .trim()
        val bytes = android.util.Base64.decode(body, android.util.Base64.DEFAULT)
        return java.security.cert.CertificateFactory.getInstance("X.509")
            .generateCertificate(bytes.inputStream())
    }

    fun createKeyStore(certPem: String, keyPem: String, password: CharArray): KeyStore {
        val cert = readCertificate(certPem)
        val keyBytes = keyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\n", "")
            .trim()
        val keySpec = java.security.spec.PKCS8EncodedKeySpec(
            android.util.Base64.decode(keyBytes, android.util.Base64.DEFAULT),
        )
        val privateKey = java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec)
        return KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry(ALIAS, privateKey, password, arrayOf(cert))
        }
    }
}
