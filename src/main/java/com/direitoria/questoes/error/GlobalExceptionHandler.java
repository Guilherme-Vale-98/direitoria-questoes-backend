package com.direitoria.questoes.error;

import com.direitoria.questoes.admin.AdminApiException;
import com.direitoria.questoes.domain.QuestionLockedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AdminApiException.class)
    public ResponseEntity<Map<String, Object>> handleAdmin(AdminApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ex.getBody());
    }

    /** The domain's own lock guard (docs/adr/0009). The validator normally reports these first. */
    @ExceptionHandler(QuestionLockedException.class)
    public ResponseEntity<Map<String, Object>> handleLocked(QuestionLockedException ex) {
        return handleAdmin(AdminApiException.validation(ex.getFieldErrors()));
    }

    /**
     * Tomcat rejects an oversize upload before any controller runs. An importação's PDF is
     * the only file allowed past 5 MB, so the copy depends on the path.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                                    HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/admin/imports")) {
            return handleAdmin(AdminApiException.pdfTooLarge());
        }
        return handleAdmin(AdminApiException.imageTooLarge(
                "Imagem muito grande (máx. 5 MB por imagem e 12 MB no total)."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null ? ex.getMessage() : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleBadParam(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Parâmetro inválido: " + ex.getName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", message));
    }
}
