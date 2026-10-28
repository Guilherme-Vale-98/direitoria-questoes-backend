package com.direitoria.questoes.admin;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * PDF importações (Bloco 3C spec §4). ADMIN-only because /api/admin/** is hasRole("ADMIN")
 * in SecurityConfig; this class adds no check of its own.
 */
@RestController
@RequestMapping("/api/admin/imports")
public class ImportController {

    private final ImportService service;
    private final ImportCommitService commits;

    public ImportController(ImportService service, ImportCommitService commits) {
        this.service = service;
        this.commits = commits;
    }

    @GetMapping
    public List<ImportViews.Summary> list() {
        return service.list();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, UUID>> create(
            @RequestPart("pdf") MultipartFile pdf,
            @RequestPart("importacao") ImportJson.ParseResult importacao,
            @RequestPart(name = "figuras", required = false) List<MultipartFile> figuras) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", service.create(pdf, importacao, figuras)));
    }

    /** Fetched by the browser for pdf.js; `attachment` + `nosniff` so it is never rendered on our origin. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        ImportStore.PdfFile f = service.pdf(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                        ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.bytes());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discard(@PathVariable UUID id) {
        service.discard(id);
    }

    @PostMapping("/{id}/commit")
    public ResponseEntity<ImportViews.CommitResult> commit(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(new ImportViews.CommitResult(commits.commit(id)));
    }

    @GetMapping("/{id}")
    public ImportViews.Overview overview(@PathVariable UUID id) {
        return service.overview(id);
    }

    @PutMapping("/{id}")
    public ImportViews.Overview updateFields(@PathVariable UUID id, @RequestBody ImportJson.FieldsRequest fields) {
        return service.updateFields(id, fields);
    }

    @GetMapping("/{id}/questions/{number}")
    public ImportViews.QuestionView question(@PathVariable UUID id, @PathVariable int number) {
        return service.question(id, number);
    }

    @PutMapping(path = "/{id}/questions/{number}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportViews.QuestionView saveQuestion(
            @PathVariable UUID id,
            @PathVariable int number,
            @RequestPart("questao") ImportJson.DraftRequest questao,
            @RequestPart(name = "imagens", required = false) List<MultipartFile> imagens) {
        return service.saveQuestion(id, number, questao, imagens);
    }

    @PutMapping("/{id}/questions/{number}/included")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setIncluded(@PathVariable UUID id, @PathVariable int number, @RequestBody ImportJson.IncludedRequest body) {
        service.setIncluded(id, number, body.included());
    }

    /** A draft's image. `no-store`: after a save the same ordem may hold another image. */
    @GetMapping("/{id}/questions/{number}/images/{ordem}")
    public ResponseEntity<byte[]> image(@PathVariable UUID id, @PathVariable int number, @PathVariable int ordem) {
        ImportStore.StoredImage img = service.image(id, number, ordem);
        return ResponseEntity.ok()
                .header("Content-Type", img.contentType())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(img.bytes());
    }
}
