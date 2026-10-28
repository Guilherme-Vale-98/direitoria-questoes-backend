package com.direitoria.questoes.admin;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * An image's type from its magic bytes — never from the declared Content-Type or the
 * file name, both of which the uploader controls. Only three raster types pass: SVG
 * served from our own origin can execute script (the Slice A lesson behind
 * `baixarImagem`'s allowlist; docs/adr/0008).
 */
final class ImageSniffer {

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private ImageSniffer() {
    }

    static Optional<String> detect(byte[] b) {
        if (startsWith(b, PNG, 0)) {
            return Optional.of("image/png");
        }
        if (startsWith(b, JPEG, 0)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(b, RIFF, 0) && startsWith(b, WEBP, 8)) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] b, byte[] magic, int offset) {
        if (b.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (b[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
