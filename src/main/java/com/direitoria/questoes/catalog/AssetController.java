package com.direitoria.questoes.catalog;

import com.direitoria.questoes.repository.QuestionAssetRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves self-hosted question images. Public, like the rest of the catalog, and
 * keyed on the PUBLIC uuid — source_id is never exposed by the API.
 */
@RestController
@RequestMapping("/api/questions")
public class AssetController {

    private final QuestionAssetRepository assets;

    public AssetController(QuestionAssetRepository assets) {
        this.assets = assets;
    }

    @GetMapping("/{id}/assets/{ordem}")
    public ResponseEntity<byte[]> asset(@PathVariable UUID id, @PathVariable short ordem) {
        byte[] bytes = assets.findBytes(id, ordem)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada"));
        String contentType = assets.findContentType(id, ordem).orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        return ResponseEntity.ok().header("Content-Type", contentType).body(bytes);
    }
}
