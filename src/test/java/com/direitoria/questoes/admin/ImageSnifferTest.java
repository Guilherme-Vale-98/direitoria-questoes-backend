package com.direitoria.questoes.admin;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageSnifferTest {

    @Test
    void recognisesPngJpegAndWebpByTheirBytes() {
        assertThat(ImageSniffer.detect(AdminFixtures.png(32))).contains("image/png");
        assertThat(ImageSniffer.detect(AdminFixtures.jpeg(32))).contains("image/jpeg");
        assertThat(ImageSniffer.detect(AdminFixtures.webp(32))).contains("image/webp");
    }

    @Test
    void rejectsSvgGifAndGarbage() {
        assertThat(ImageSniffer.detect(AdminFixtures.svg())).isEqualTo(Optional.empty());
        assertThat(ImageSniffer.detect("GIF89a-------".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ImageSniffer.detect(new byte[0])).isEmpty();
        assertThat(ImageSniffer.detect(new byte[] {(byte) 0x89, 0x50})).isEmpty();
    }

    @Test
    void aRiffThatIsNotWebpIsRejected() {
        assertThat(ImageSniffer.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.US_ASCII))).isEmpty();
    }
}
