package com.direitoria.questoes.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Every error the admin endpoints answer with: an HTTP status plus the JSON body the
 * form reads (`code` + Portuguese `message` + details). Thrown inside the service's
 * transaction, so it also rolls back anything already written.
 */
public class AdminApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, Object> body;

    private AdminApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("code", code);
        b.put("message", message);
        b.putAll(details);
        this.body = b;
    }

    public static AdminApiException validation(Map<String, String> fieldErrors) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("fieldErrors", new LinkedHashMap<>(fieldErrors));
        return new AdminApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Corrija os campos destacados.", d);
    }

    /** `existingId` is null only when the duplicate was caught by the unique index (a race). */
    public static AdminApiException duplicate(UUID existingId) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("existingId", existingId);
        return new AdminApiException(HttpStatus.CONFLICT, "DUPLICATE", "Essa questão já existe no acervo.", d);
    }

    public static AdminApiException rejudgeRequired(long attemptCount, long studentCount) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("attemptCount", attemptCount);
        d.put("studentCount", studentCount);
        String message = "Isso vai recalcular " + attemptCount + (attemptCount == 1 ? " tentativa" : " tentativas")
                + " de " + studentCount + (studentCount == 1 ? " aluno" : " alunos") + ". Continuar?";
        return new AdminApiException(HttpStatus.CONFLICT, "REJUDGE_CONFIRMATION_REQUIRED", message, d);
    }

    /** The admin confirmed a blast radius that is no longer the real one (a student answered meanwhile). */
    public static AdminApiException deleteConfirmationRequired(long attemptCount, long studentCount) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("attemptCount", attemptCount);
        d.put("studentCount", studentCount);
        String message = "Esta questão tem " + attemptCount + (attemptCount == 1 ? " tentativa" : " tentativas")
                + " de " + studentCount + (studentCount == 1 ? " aluno" : " alunos") + ". Confirme de novo para excluir.";
        return new AdminApiException(HttpStatus.CONFLICT, "DELETE_CONFIRMATION_REQUIRED", message, d);
    }

    public static AdminApiException imageTooLarge(String message) {
        return new AdminApiException(HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE", message, Map.of());
    }

    public static AdminApiException unsupportedImage(String fileName) {
        return new AdminApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE",
                "Formato não aceito (use PNG, JPEG ou WebP): " + fileName + ".", Map.of());
    }

    public static AdminApiException notFound() {
        return new AdminApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Questão não encontrada", Map.of());
    }

    public static AdminApiException importNotFound() {
        return new AdminApiException(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "Importação não encontrada.", Map.of());
    }

    public static AdminApiException notAPdf() {
        return new AdminApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_PDF",
                "O arquivo enviado não é um PDF.", Map.of());
    }

    public static AdminApiException pdfTooLarge() {
        return new AdminApiException(HttpStatus.CONTENT_TOO_LARGE, "PDF_TOO_LARGE",
                "PDF muito grande (máx. 20 MB).", Map.of());
    }

    /** Nothing was written: every included questão's problem, and the importação's own. */
    public static AdminApiException commitBlocked(List<?> problems) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("problems", problems);
        return new AdminApiException(HttpStatus.CONFLICT, "COMMIT_BLOCKED",
                "Há pendências: nada foi adicionado ao acervo.", d);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getBody() {
        return body;
    }
}
