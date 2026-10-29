package cn.toside.music.compose.js

import android.util.Base64
import java.nio.charset.Charset
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 音源脚本沙箱所需的加密 / 编码原生实现。
 *
 * 契约来自两个 preload 脚本：
 *  - user-api-preload.js：str2b64 / b642buf / str2md5 / aes_encrypt / rsa_encrypt
 *    （Node 风格算法名 aes-128-cbc / aes-128-ecb，RSA NoPadding）
 *  - builtin-sdk-preload.js：b64md5 / str2md5 / aes_encrypt / aes_decrypt /
 *    rsa_encrypt / decode_charset / encode_charset / sha1
 *
 * 全部方法都在 [JsEngineThread] 上被调用（JS 同步回调），必须快速返回。
 */
object Crypto {

    private const val AES_CBC = "AES/CBC/PKCS7Padding"
    private const val AES_ECB = "AES/ECB/NoPadding"
    private const val RSA_NO_PADDING = "RSA/ECB/NoPadding"

    fun b64Encode(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP)

    fun b64Decode(b64: String): ByteArray =
        Base64.decode(b64, Base64.NO_WRAP)

    fun md5Hex(input: String): String =
        MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun md5B64Hex(b64: String): String =
        md5Hex(String(b64Decode(b64), Charsets.UTF_8))

    fun sha1Hex(input: String): String =
        MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /**
     * aes-128-cbc / AES/CBC/PKCS7Padding → AES/CBC/PKCS7Padding；
     * aes-128-ecb / AES → AES（NoPadding）。
     * data/key/iv 均为 base64，返回 base64。
     */
    fun aesEncrypt(mode: String, dataB64: String, keyB64: String, ivB64: String): String {
        val useCbc = mode.contains("CBC", ignoreCase = true)
        val cipher = Cipher.getInstance(if (useCbc) AES_CBC else AES_ECB)
        val key = SecretKeySpec(b64Decode(keyB64), "AES")
        if (useCbc) cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(b64Decode(ivB64)))
        else cipher.init(Cipher.ENCRYPT_MODE, key)
        return b64Encode(cipher.doFinal(b64Decode(dataB64)))
    }

    fun aesDecrypt(mode: String, dataB64: String, keyB64: String, ivB64: String): String {
        val useCbc = mode.contains("CBC", ignoreCase = true)
        val cipher = Cipher.getInstance(if (useCbc) AES_CBC else AES_ECB)
        val key = SecretKeySpec(b64Decode(keyB64), "AES")
        if (useCbc) cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(b64Decode(ivB64)))
        else cipher.init(Cipher.DECRYPT_MODE, key)
        return b64Encode(cipher.doFinal(b64Decode(dataB64)))
    }

    /** key 为无 PEM 头尾的 base64 公钥；padding 为 3 或 "RSA/ECB/NoPadding" 即 NoPadding */
    fun rsaEncrypt(dataB64: String, keyB64: String, padding: String): String {
        val keySpec = java.security.spec.X509EncodedKeySpec(b64Decode(keyB64))
        val publicKey = java.security.KeyFactory.getInstance("RSA").generatePublic(keySpec)
        val noPadding = padding == "3" || padding.contains("NoPadding", ignoreCase = true)
        val cipher = Cipher.getInstance(if (noPadding) RSA_NO_PADDING else "RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return b64Encode(cipher.doFinal(b64Decode(dataB64)))
    }

    fun decodeCharset(b64: String, charsetName: String): String =
        String(b64Decode(b64), Charset.forName(charsetName))

    fun encodeCharset(str: String, charsetName: String): String =
        b64Encode(str.toByteArray(Charset.forName(charsetName)))
}
