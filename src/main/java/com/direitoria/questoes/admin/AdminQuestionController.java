package com.direitoria.questoes.admin;

import com.direitoria.questoes.admin.dto.AdminQuestionRequest;
import com.direitoria.questoes.admin.dto.AdminQuestionResponse;
import com.direitoria.questoes.admin.dto.EditResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin cadastro, edição and exclusão of questões. ADMIN-only because /api/admin/** is
 * hasRole("ADMIN") in SecurityConfig — the server is the real gate; this class adds no
 * check of its own. Writes are multipart: a `questao` JSON part (+ `imagens` parts).
 */
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionController {

    private final AdminQuestionService service;

    public AdminQuestionController(AdminQuestionService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public AdminQuestionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, UUID>> create(
            @RequestPart("questao") AdminQuestionRequest questao,
            @RequestPart(name = "imagens", required = false) List<MultipartFile> imagens) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", service.create(questao, imagens)));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EditResult edit(
            @PathVariable UUID id,
            @RequestPart("questao") AdminQuestionRequest questao,
            @RequestPart(name = "imagens", required = false) List<MultipartFile> imagens) {
        return service.edit(id, questao, imagens);
    }

    /** `expectedAttempts` = the blast radius the admin confirmed; the service re-checks it (docs/adr/0005). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @RequestParam long expectedAttempts) {
        if (expectedAttempts < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedAttempts inválido");
        }
        service.delete(id, expectedAttempts);
    }
}
