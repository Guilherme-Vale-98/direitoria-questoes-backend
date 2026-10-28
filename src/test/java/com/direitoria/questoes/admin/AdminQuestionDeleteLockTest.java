package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the row lock in {@link AdminQuestionService#delete}
 * (docs/adr/0005; docs/superpowers/specs/2026-09-25-exclusao-questoes-design.md).
 *
 * <p>{@code delete} is {@code @Transactional} — one connection per call — so a
 * single-threaded test cannot tell a real {@code SELECT ... FOR UPDATE} lock apart from a
 * delete that never locks at all: every assertion in {@link AdminQuestionDeleteTest} stays
 * green either way, because nothing else ever touches the row inside that one connection.
 * This class is deliberately NOT {@code @Transactional} and forces two genuinely
 * concurrent connections instead:
 *
 * <ul>
 *   <li>Thread B opens its own transaction (a {@link TransactionTemplate} against the
 *       real {@link PlatformTransactionManager} — there is no ambient test transaction to
 *       borrow) and inserts one tentativa for the questão. That insert's foreign-key check
 *       needs a key-share lock on the questão row, and Postgres holds it until B's
 *       transaction ends. B then counts down the "inserted" latch and blocks on "release",
 *       so the insert stays uncommitted — and the lock held — for as long as the test
 *       wants.</li>
 *   <li>Thread C calls {@code adminQuestionService.delete(publicId, 0)} once "inserted"
 *       fires.</li>
 * </ul>
 *
 * <p><b>With the lock</b> (the shipped order: {@code lockForDelete} before
 * {@code countsFor}): C's {@code FOR UPDATE} blocks behind B's uncommitted insert. The main
 * thread waits ~500ms — long enough for C to be parked on the lock — then releases B, which
 * commits its tentativa. C's {@code FOR UPDATE} then returns and its count is 1, which does
 * not match the {@code expectedAttempts=0} it was called with, so C throws a 409 with
 * {@code attemptCount=1} and deletes nothing. The questão and B's tentativa both survive.
 *
 * <p><b>Without the lock</b> (counting before locking, or skipping the lock call): C's
 * count runs immediately against B's still-uncommitted insert and sees 0 — matching
 * {@code expectedAttempts=0} — so C proceeds straight to {@code DELETE FROM questao}. That
 * delete needs the same row lock B is holding, so it waits; once B commits, C's delete goes
 * through and, via {@code ON DELETE CASCADE}, silently erases the tentativa B just
 * committed. C would then return normally instead of throwing, and the tentativa would be
 * gone — failing the assertions below. This is exactly the failure the fix wave's M3 asked
 * to demonstrate: temporarily swapping the order (or dropping {@code lockForDelete}) in
 * {@link AdminQuestionService#delete} makes this test fail; the shipped order makes it
 * pass.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AdminQuestionDeleteLockTest {

    @Autowired AdminQuestionService adminQuestionService;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    private String sourceId;
    private UUID studentId;

    @AfterEach
    void cleanUp() {
        if (sourceId != null) {
            jdbc.update("DELETE FROM questao WHERE source_id = ?", sourceId); // cascades to question_attempt
        }
        if (studentId != null) {
            jdbc.update("DELETE FROM users WHERE id = ?", studentId);
        }
    }

    @Test
    void aConcurrentTentativaIsCountedBeforeTheDeleteCanProceed() throws Exception {
        String tag = UUID.randomUUID().toString();
        sourceId = "lock_" + tag;
        UUID publicId = AdminFixtures.seedMc(
                jdbc, sourceId, "Enunciado lock " + tag, "A", "a", "b", "c", "d", null); // committed: no test transaction here
        studentId = AdminFixtures.student(jdbc); // committed too

        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        Thread threadB = new Thread(() -> tx.executeWithoutResult(status -> {
            AdminFixtures.attempt(jdbc, studentId, sourceId, "A", true);
            inserted.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("thread B timed out waiting for release");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }), "thread-B-tentativa");

        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread threadC = new Thread(() -> {
            try {
                if (!inserted.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("thread C timed out waiting for thread B's insert");
                }
                adminQuestionService.delete(publicId, 0);
            } catch (Throwable t) {
                outcome.set(t);
            }
        }, "thread-C-delete");

        threadB.start();
        threadC.start();
        Thread.sleep(500); // give C time to be parked on the FOR UPDATE behind B's uncommitted insert
        release.countDown();
        threadB.join(10_000);
        threadC.join(10_000);
        assertThat(threadB.isAlive()).as("thread B finished").isFalse();
        assertThat(threadC.isAlive()).as("thread C finished").isFalse();

        Throwable thrown = outcome.get();
        assertThat(thrown).as("delete threw instead of succeeding").isInstanceOf(AdminApiException.class);
        AdminApiException ex = (AdminApiException) thrown;
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ex.getBody().get("attemptCount")).isEqualTo(1L);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM questao WHERE source_id = ?", Long.class, sourceId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM question_attempt WHERE question_source_id = ?", Long.class, sourceId))
                .isEqualTo(1L);
    }
}
