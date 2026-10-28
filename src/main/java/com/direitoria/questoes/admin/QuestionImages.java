package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.ImageRef;
import com.direitoria.questoes.repository.QuestionAssetRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * A questão's images (docs/adr/0008): checks an upload before anything is written,
 * then writes the final order to questao_asset with native SQL — never through the
 * Question.assets collection, whose join column is read-only.
 */
@Component
class QuestionImages {

    static final int MAX_IMAGES = 5;
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 12L * 1024 * 1024;

    record Upload(String contentType, byte[] bytes, String origin) {
    }

    /** One slot of the final order: an existing asset (`keep` = its current ordem) or an upload. */
    record Slot(Integer keep, Upload upload) {
    }

    private final QuestionAssetRepository assets;

    QuestionImages(QuestionAssetRepository assets) {
        this.assets = assets;
    }

    List<Integer> existingOrdens(String sourceId) {
        return assets.ordensOf(sourceId);
    }

    /**
     * Checks every file and every slot before anything is written: size (413), type by
     * magic bytes (415), then the order itself (400).
     *
     * @param existing the ordens the questão has today — empty on create
     */
    List<Slot> plan(List<ImageRef> refs, List<MultipartFile> files, Collection<Integer> existing) {
        List<MultipartFile> parts = files == null ? List.of() : files;
        List<Upload> uploads = new ArrayList<>();
        long total = 0;
        for (MultipartFile f : parts) {
            String name = nameOf(f);
            if (f.getSize() > MAX_IMAGE_BYTES) {
                throw AdminApiException.imageTooLarge("Imagem muito grande (máx. 5 MB): " + name + ".");
            }
            total += f.getSize();
            byte[] bytes = bytesOf(f);
            String type = ImageSniffer.detect(bytes).orElseThrow(() -> AdminApiException.unsupportedImage(name));
            uploads.add(new Upload(type, bytes, "upload:" + name));
        }
        if (total > MAX_TOTAL_BYTES) {
            throw AdminApiException.imageTooLarge("As imagens somam mais de 12 MB.");
        }

        List<ImageRef> order = refs == null ? List.of() : refs;
        if (order.size() > MAX_IMAGES) {
            throw invalid("No máximo 5 imagens por questão.");
        }
        Set<Integer> keptSeen = new HashSet<>();
        Set<Integer> uploadSeen = new HashSet<>();
        List<Slot> slots = new ArrayList<>();
        for (ImageRef ref : order) {
            if (ref == null || (ref.keep() == null) == (ref.upload() == null)) {
                throw invalid("Cada imagem deve ser mantida ou enviada.");
            }
            if (ref.keep() != null) {
                if (!existing.contains(ref.keep()) || !keptSeen.add(ref.keep())) {
                    throw invalid("Imagem existente inválida: " + ref.keep() + ".");
                }
                slots.add(new Slot(ref.keep(), null));
            } else {
                int i = ref.upload();
                if (i < 0 || i >= uploads.size() || !uploadSeen.add(i)) {
                    throw invalid("Arquivo de imagem inválido: " + i + ".");
                }
                slots.add(new Slot(null, uploads.get(i)));
            }
        }
        if (uploadSeen.size() != uploads.size()) {
            throw invalid("Há arquivos enviados sem lugar na ordem das imagens.");
        }
        return slots;
    }

    /**
     * An imported questão's images (Bloco 3C), all new, in order. Their type was sniffed when
     * the importação stored them; only the count is left to check.
     */
    List<Slot> planUploads(List<Upload> uploads) {
        if (uploads.size() > MAX_IMAGES) {
            throw invalid("No máximo 5 imagens por questão.");
        }
        return uploads.stream().map(u -> new Slot(null, u)).toList();
    }

    /** Writes the final order. Must run after the questão row is flushed (FK). */
    void apply(String sourceId, List<Slot> slots) {
        Set<Integer> kept = new HashSet<>();
        for (Slot s : slots) {
            if (s.keep() != null) {
                kept.add(s.keep());
            }
        }
        for (Integer ordem : assets.ordensOf(sourceId)) {
            if (!kept.contains(ordem)) {
                assets.deleteOne(sourceId, ordem);
            }
        }
        assets.park(sourceId);
        for (int pos = 0; pos < slots.size(); pos++) {
            Slot s = slots.get(pos);
            if (s.keep() != null) {
                assets.moveParked(sourceId, -1 - s.keep(), pos);
            } else {
                assets.insert(sourceId, pos, s.upload().contentType(), s.upload().bytes(),
                        s.upload().origin());
            }
        }
    }

    private static AdminApiException invalid(String message) {
        return AdminApiException.validation(Map.of("images", message));
    }

    private static String nameOf(MultipartFile f) {
        String n = f.getOriginalFilename();
        return n == null || n.isBlank() ? "imagem" : n;
    }

    private static byte[] bytesOf(MultipartFile f) {
        try {
            return f.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
