package id.tukangkilat.app.data.local

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM encrypted wrapper around the portable database snapshot. */
object SecureBackup {
    private const val MAGIC = "TKBK1"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val MAGIC_BYTES = 5

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        require(passphrase.length >= 8) { "Kata sandi minimal 8 karakter" }
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    /** Write an encrypted snapshot to [out]. Never closes [out], the caller owns the stream. */
    fun encrypt(context: Context, passphrase: String, out: OutputStream) {
        val plain = ByteArrayOutputStream()
        DatabaseTransfer.export(context, plain)
        val salt = SecureRandom().generateSeed(SALT_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt))
        val iv = cipher.iv
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(salt)
        out.write(iv)
        out.write(cipher.doFinal(plain.toByteArray()))
        out.flush()
    }

    /** Decrypt and restore a snapshot. Throws on wrong passphrase or corrupt data. */
    fun decrypt(context: Context, passphrase: String, input: InputStream) {
        val bytes = input.readBytes()
        require(bytes.size > MAGIC_BYTES + SALT_BYTES + IV_BYTES) { "Berkas cadangan tidak valid" }
        require(bytes.copyOf(MAGIC_BYTES).toString(Charsets.US_ASCII) == MAGIC) { "Format cadangan tidak dikenal" }
        val salt = bytes.copyOfRange(MAGIC_BYTES, MAGIC_BYTES + SALT_BYTES)
        val iv = bytes.copyOfRange(MAGIC_BYTES + SALT_BYTES, MAGIC_BYTES + SALT_BYTES + IV_BYTES)
        val payload = bytes.copyOfRange(MAGIC_BYTES + SALT_BYTES + IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        val plain = cipher.doFinal(payload)
        DatabaseTransfer.import(context, ByteArrayInputStream(plain))
    }
}
