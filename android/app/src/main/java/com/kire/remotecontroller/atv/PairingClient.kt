package com.kire.remotecontroller.atv

import com.google.polo.wire.protobuf.PoloProto
import java.io.InputStream
import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocket

class PairingClient(
    private val socket: SSLSocket,
    private val clientName: String,
    private val clientCert: java.security.cert.Certificate,
) {
    private val input: InputStream = socket.inputStream
    private val output = socket.outputStream

    fun startPairing() {
        send(createBase().apply {
            pairingRequest = PoloProto.PairingRequest.newBuilder()
                .setServiceName("atvremote")
                .setClientName(clientName)
                .build()
        })
        waitForConfigurationAck()
    }

    fun finishPairing(pinHex: String) {
        require(pinHex.length == 6) { "PIN must be 6 hex characters" }
        pinHex.toULongOrNull(16) ?: throw IllegalArgumentException("PIN must be hexadecimal")

        val serverCert = socket.session.peerCertificates.first() as X509Certificate
        val hash = pairingHash(clientCert, serverCert, pinHex)
        if (hash[0].toInt() and 0xFF != pinHex.substring(0, 2).toInt(16)) {
            throw IllegalStateException("Invalid pairing PIN")
        }
        send(createBase().apply {
            secret = PoloProto.Secret.newBuilder()
                .setSecret(com.google.protobuf.ByteString.copyFrom(hash))
                .build()
        })
        val ackRaw = ProtobufFraming.readMessage(input) ?: throw IllegalStateException("No pairing ack")
        val ack = PoloProto.OuterMessage.parseFrom(ackRaw)
        if (ack.status != PoloProto.OuterMessage.Status.STATUS_OK || !ack.hasSecretAck()) {
            throw IllegalStateException("Pairing not acknowledged")
        }
    }

    private fun waitForConfigurationAck() {
        while (true) {
            val raw = ProtobufFraming.readMessage(input) ?: throw IllegalStateException("Pairing connection closed")
            val msg = PoloProto.OuterMessage.parseFrom(raw)
            if (msg.status != PoloProto.OuterMessage.Status.STATUS_OK) {
                throw IllegalStateException("Pairing failed: ${msg.status}")
            }
            when {
                msg.hasPairingRequestAck() -> {
                    send(createBase().apply {
                        options = PoloProto.Options.newBuilder()
                            .setPreferredRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT)
                            .addInputEncodings(
                                PoloProto.Options.Encoding.newBuilder()
                                    .setType(PoloProto.Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
                                    .setSymbolLength(6)
                                    .build(),
                            )
                            .build()
                    })
                }
                msg.hasOptions() -> {
                    send(createBase().apply {
                        configuration = PoloProto.Configuration.newBuilder()
                            .setClientRole(PoloProto.Options.RoleType.ROLE_TYPE_INPUT)
                            .setEncoding(
                                PoloProto.Options.Encoding.newBuilder()
                                    .setType(PoloProto.Options.Encoding.EncodingType.ENCODING_TYPE_HEXADECIMAL)
                                    .setSymbolLength(6)
                                    .build(),
                            )
                            .build()
                    })
                }
                msg.hasConfigurationAck() -> return
                else -> throw IllegalStateException("Unexpected pairing message")
            }
        }
    }

    private fun pairingHash(
        clientCert: java.security.cert.Certificate,
        serverCert: X509Certificate,
        pin: String,
    ): ByteArray {
        val clientRsa = clientCert.publicKey as java.security.interfaces.RSAPublicKey
        val serverRsa = serverCert.publicKey as java.security.interfaces.RSAPublicKey
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(clientRsa.modulus.toHexBytes())
        digest.update("0${clientRsa.publicExponent.toString(16)}".toHexBytes())
        digest.update(serverRsa.modulus.toHexBytes())
        digest.update("0${serverRsa.publicExponent.toString(16)}".toHexBytes())
        digest.update(pin.substring(2).toHexBytes())
        return digest.digest()
    }

    private fun String.toHexBytes(): ByteArray =
        ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }

    private fun java.math.BigInteger.toHexBytes(): ByteArray = toString(16).toHexBytes()

    private fun createBase(): PoloProto.OuterMessage.Builder =
        PoloProto.OuterMessage.newBuilder()
            .setProtocolVersion(2)
            .setStatus(PoloProto.OuterMessage.Status.STATUS_OK)

    private fun send(builder: PoloProto.OuterMessage.Builder) {
        ProtobufFraming.writeMessage(output, builder.build())
    }

    fun close() {
        runCatching { socket.close() }
    }
}
