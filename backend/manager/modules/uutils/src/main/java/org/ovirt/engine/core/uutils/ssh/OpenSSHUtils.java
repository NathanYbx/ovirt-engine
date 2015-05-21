package org.ovirt.engine.core.uutils.ssh;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenSSHUtils {
    // The log:
    private static final Logger log = LoggerFactory.getLogger(OpenSSHUtils.class);

    // Names of supported algorithms:
    private static final String SSH_RSA = "ssh-rsa";

    private OpenSSHUtils () {
        // No instances allowed.
    }

    /**
     * Convert a public key to the SSH format.
     *
     * Note that only RSA keys are supported at the moment.
     *
     * @param key the public key to convert
     * @return an array of bytes that can with the representation of
     *   the public key
     */
    public static byte[] getKeyBytes(final PublicKey key) {
        // We only support RSA at the moment:
        if (!(key instanceof RSAPublicKey)) {
            log.error("The key algorithm '{}' is not supported, will return null.", key.getAlgorithm());
            return null;
        }

        // Extract the bytes of the exponent and the modulus
        // of the key:
        final RSAPublicKey rsaKey = (RSAPublicKey) key;
        final byte[] exponentBytes = rsaKey.getPublicExponent().toByteArray();
        final byte[] modulusBytes = rsaKey.getModulus().toByteArray();
        if (log.isDebugEnabled()) {
            log.debug("Exponent is {} ({}).", rsaKey.getPublicExponent(), Hex.encodeHexString(exponentBytes));
            log.debug("Modulus is {} ({}).", rsaKey.getModulus(), Hex.encodeHexString(exponentBytes));
        }

        try {
            // Prepare the stream to write the binary SSH key:
            final ByteArrayOutputStream binaryOut = new ByteArrayOutputStream();
            final DataOutputStream dataOut = new DataOutputStream(binaryOut);

            // Write the SSH header (4 bytes for the length of the algorithm
            // name and then the algorithm name):
            dataOut.writeInt(SSH_RSA.length());
            dataOut.writeBytes(SSH_RSA);

            // Write the exponent and modulus bytes (note that it is not
            // necessary to check if the most significative bit is one, as
            // that will never happen with byte arrays created from big
            // integers, unless they are negative, which is not the case
            // for RSA modulus or exponents):
            dataOut.writeInt(exponentBytes.length);
            dataOut.write(exponentBytes);
            dataOut.writeInt(modulusBytes.length);
            dataOut.write(modulusBytes);

            // Done, extract the bytes:
            binaryOut.close();
            final byte[] keyBytes = binaryOut.toByteArray();
            if (log.isDebugEnabled()) {
                log.debug("Key bytes are {}.", Hex.encodeHexString(keyBytes));
            }

            return keyBytes;
        }
        catch (IOException exception) {
            log.error("Error while serializing public key, will return null.", exception);
            return null;
        }
    }

    /**
     * Convert a public key to the SSH format used in the
     * <code>authorized_keys</code> files.
     *
     * Note that only RSA keys are supported at the moment.
     *
     * @param key the public key to convert
     * @param alias the alias to be appended at the end of the line, if
     *   it is <code>null</code> nothing will be appended
     * @return an string that can be directly written to the
     *   <code>authorized_keys</code> file or <code>null</code> if the
     *   conversion can't be performed for whatever the reason
     */
    public static String getKeyString(final PublicKey key, String alias) {
        // Get the serialized version of the key:
        final byte[] keyBytes = getKeyBytes(key);
        if (keyBytes == null) {
            log.error("Can't get key bytes, will return null.");
            return null;
        }

        // Encode it using BASE64:
        final Base64 encoder = new Base64(0);
        final String encoding = encoder.encodeToString(keyBytes);
        if (log.isDebugEnabled()) {
            log.debug("Key encoding is '{}'.", encoding);
        }

        // Return the generated SSH public key:
        final StringBuilder buffer = new StringBuilder(SSH_RSA.length() + 1 + encoding.length() + (alias != null? 1 + alias.length(): 0) + 1);
        buffer.append(SSH_RSA);
        buffer.append(" ");
        buffer.append(encoding);
        if (alias != null) {
            buffer.append(" ");
            buffer.append(alias);
        }
        buffer.append('\n');
        final String keyString = buffer.toString();
        if (log.isDebugEnabled()) {
            log.debug("Key string is '{}'.", keyString);
        }

        return keyString;
    }

    public static final String getKeyFingerprint(final PublicKey key, String digest) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(getKeyBytes(key));

            String fingerprint;
            if ("MD5".equals(digest)) {
                StringBuilder s = new StringBuilder();
                for (byte b : md.digest()) {
                    if (s.length() > 0) {
                        s.append(':');
                    }
                    s.append(String.format("%02x", b));
                }
                fingerprint = s.toString();
            } else {
                fingerprint = String.format(
                    "%s:%s",
                    digest.toUpperCase().replace("-", ""),
                    new Base64(0).encodeToString(md.digest())
                );
            }

            if (log.isDebugEnabled()) {
                log.debug("Fingerprint: {}", fingerprint);
            }

            return fingerprint;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean verifyByteArray(DataInputStream dataInputStream, byte[] expected) throws IOException {
        int length = dataInputStream.readInt();
        byte[] contents = new byte[length];
        int numBytes = dataInputStream.read(contents, 0, length);

        if (numBytes != length) {
            return false;
        }

        if (expected != null) {
            return Arrays.equals(contents, expected);
        }

        return true;
    }

    public static boolean isPublicKeyValid(String publicKey) {
        String[] words = publicKey.split("\\s+", 3);

        if (!words[0].equals(SSH_RSA)) {
            return false;
        }

        try {
            byte[] decodedBytes = Base64.decodeBase64(words[1]);

            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(decodedBytes);
                 DataInputStream dataInputStream = new DataInputStream(inputStream)) {

                verifyByteArray(dataInputStream, SSH_RSA.getBytes(Charset.forName("UTF-8")));
                verifyByteArray(dataInputStream, null);
                verifyByteArray(dataInputStream, null);

                return true;
            }
        } catch (IOException e) {
            return false;
        }
    }
}
