package ryzlar.glass.network;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Salted PBKDF2 password hashes for GLASS accounts and channels. Passwords are never stored.
 */
public final class PasswordHasher
{
    public static final int ITERATIONS = 20_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public record Hash(String salt, String hash, int iterations) {}

    public static Hash hash(String password)
    {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return new Hash(Base64.getEncoder().encodeToString(salt), Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS)), ITERATIONS);
    }

    public static boolean verify(String password, String salt, String hash, int iterations)
    {
        byte[] expected = Base64.getDecoder().decode(hash);
        byte[] actual = derive(password, Base64.getDecoder().decode(salt), iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] derive(String password, byte[] salt, int iterations)
    {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try
        {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        }
        catch(GeneralSecurityException e)
        {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is not available", e);
        }
        finally
        {
            spec.clearPassword();
        }
    }
}
