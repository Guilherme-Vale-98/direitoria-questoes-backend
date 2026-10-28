package com.direitoria.questoes.admin;

import com.direitoria.questoes.TestcontainersConfiguration;
import com.direitoria.questoes.auth.JwtService;
import com.direitoria.questoes.domain.Agency;
import com.direitoria.questoes.domain.ExamBoard;
import com.direitoria.questoes.domain.Subject;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression tests for the commit's row lock and its all-or-nothing rollback (Bloco 3C D6,
 * Review Focus 3), and for the save paths that take the same lock (final review Minor 1).
 *
 * <p>Every test in {@link AdminImportCommitTest} runs inside one class-level test
 * transaction, so a lost {@code @Transactional}, a {@code REQUIRES_NEW} somewhere in the
 * commit, or a missing {@code FOR UPDATE} would all pass there silently. This class is
 * deliberately NOT {@code @Transactional}: every request commits for real, several
 * connections run at once, and {@link #cleanUp()} deletes every row a test created.
 *
 * <p>The concurrency probes park their threads behind a separate connection that holds
 * {@code FOR UPDATE} on the importação, wait until Postgres reports them blocked on a lock,
 * then release it:
 * <ul>
 *   <li><b>Two commits</b> — with the lock, the first commit to get the row creates the N
 *       questões and deletes the importação; the second then finds no row and answers 404.
 *       Without the lock both run: the second's inserts collide with the first's and it
 *       fails with something other than 404.</li>
 *   <li><b>A commit and an include toggle</b> — with the save paths locking first, the
 *       toggle queues behind the commit and then finds the importação gone (404). Without
 *       it, the toggle updates its questão row, blocks on the importação row, and the
 *       commit's cascade delete then needs that questão row: a deadlock, which Postgres
 *       settles by aborting the commit.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminImportCommitLockTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;
    @Autowired ImportCommitService commits;
    @Autowired ImportService imports;
    @Autowired SubjectRepository subjects;
    @Autowired ExamBoardRepository boards;
    @Autowired AgencyRepository agencies;

    private final String tag = "Lock " + UUID.randomUUID().toString().substring(0, 8);
    private final String boardByName = "Banca " + tag;
    private final List<UUID> importIds = new ArrayList<>();
    private final List<Integer> subjectIds = new ArrayList<>();
    private final List<Integer> boardIds = new ArrayList<>();
    private final List<Integer> agencyIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        // questão first: it cascades to questao_asset / questao_disciplina and releases the canonicals
        jdbc.update("DELETE FROM questao WHERE enunciado LIKE ?", "Enunciado " + tag + "%");
        importIds.forEach(id -> jdbc.update("DELETE FROM importacao WHERE id = ?", id)); // cascades to its questões and images
        jdbc.update("DELETE FROM banca WHERE nome = ?", boardByName);
        boardIds.forEach(id -> jdbc.update("DELETE FROM banca WHERE id = ?", id));
        agencyIds.forEach(id -> jdbc.update("DELETE FROM orgao WHERE id = ?", id));
        subjectIds.forEach(id -> jdbc.update("DELETE FROM disciplina WHERE id = ?", id));
    }

    @Test
    void aFailureOnALaterQuestaoRollsBackEveryWriteTheCommitMade() throws Exception {
        Subject first = subject("Disciplina " + tag + " A");
        Subject gone = subject("Disciplina " + tag + " B");
        Map<String, Object> r = threeQuestoes();
        firstQuestao(r).put("figures", List.of(Map.of("part", 0, "page", 1)));
        UUID id = createImport(r, ImportFixtures.figure(AdminFixtures.png(64)));
        Map<String, Object> f = completeFields(List.of(range(1, 1, first.getId()), range(2, 3, gone.getId())));
        f.put("examBoard", Map.of("name", boardByName)); // created by questão 1's write, inside the commit
        putFields(id, f);
        jdbc.update("DELETE FROM disciplina WHERE id = ?", gone.getId()); // deleted after the review (plan Ruling 7)
        long questoes = count("SELECT count(*) FROM questao");
        long pdfRows = count("SELECT count(*) FROM questao WHERE source_id LIKE 'pdf:%'");
        long assets = count("SELECT count(*) FROM questao_asset");
        long bancas = count("SELECT count(*) FROM banca");

        // questão 1 (its banca, its figure) is written before questão 2's disciplina fails to resolve
        mvc.perform(post("/api/admin/imports/{id}/commit", id).header("Authorization", admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.fieldErrors.subjects").exists());

        assertThat(count("SELECT count(*) FROM questao")).isEqualTo(questoes);
        assertThat(count("SELECT count(*) FROM questao WHERE source_id LIKE 'pdf:%'")).isEqualTo(pdfRows);
        assertThat(count("SELECT count(*) FROM questao_asset")).isEqualTo(assets);
        assertThat(count("SELECT count(*) FROM banca")).isEqualTo(bancas);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM banca WHERE nome = ?", Long.class, boardByName)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM importacao WHERE id = ?", Long.class, id)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM importacao_questao WHERE importacao_id = ? AND included",
                Long.class, id)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM importacao_imagem WHERE importacao_id = ?", Long.class, id))
                .isEqualTo(1L);
    }

    @Test
    void twoConcurrentCommitsCreateTheQuestoesOnceAndTheSecondFindsTheImportGone() throws Exception {
        UUID id = readyImport();
        long pdfRows = count("SELECT count(*) FROM questao WHERE source_id LIKE 'pdf:%'");
        AtomicReference<Object> one = new AtomicReference<>();
        AtomicReference<Object> two = new AtomicReference<>();

        withTheImportLocked(id, () -> {
            Thread a = worker("commit-a", one, () -> commits.commit(id));
            Thread b = worker("commit-b", two, () -> commits.commit(id));
            a.start();
            b.start();
            awaitLockWaiters(2);
            return List.of(a, b);
        });

        List<Object> outcomes = List.of(one.get(), two.get());
        assertThat(outcomes).as("one commit created the 3 questões").filteredOn(o -> Integer.valueOf(3).equals(o)).hasSize(1);
        assertThat(outcomes).as("the other found the importação gone").filteredOn(AdminImportCommitLockTest::importNotFound)
                .hasSize(1);
        assertThat(count("SELECT count(*) FROM questao WHERE source_id LIKE 'pdf:%'")).isEqualTo(pdfRows + 3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questao WHERE enunciado LIKE ?", Long.class,
                "Enunciado " + tag + "%")).isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM importacao WHERE id = ?", Long.class, id)).isZero();
    }

    @Test
    void anIncludeToggleIssuedDuringACommitWaitsForItThenFindsTheImportGone() throws Exception {
        UUID id = readyImport();
        AtomicReference<Object> committed = new AtomicReference<>();
        AtomicReference<Object> toggled = new AtomicReference<>();

        withTheImportLocked(id, () -> {
            Thread c = worker("commit", committed, () -> commits.commit(id));
            c.start();
            awaitLockWaiters(1); // the commit is first in the queue
            Thread t = worker("toggle", toggled, () -> {
                imports.setIncluded(id, 2, false);
                return "toggled";
            });
            t.start();
            awaitLockWaiters(2);
            return List.of(c, t);
        });

        assertThat(committed.get()).as("the commit ran to the end (no deadlock victim)").isEqualTo(3);
        assertThat(toggled.get()).as("the toggle waited, then found the importação gone").matches(AdminImportCommitLockTest::importNotFound);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM questao WHERE enunciado LIKE ?", Long.class,
                "Enunciado " + tag + "%")).isEqualTo(3L);
    }

    // --- concurrency helpers ---

    /**
     * Holds FOR UPDATE on the importação from another connection while `start` launches the
     * workers (and waits until they are blocked), then releases it and joins them all.
     */
    private void withTheImportLocked(UUID id, Callable<List<Thread>> start) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Thread holder = new Thread(() -> tx.executeWithoutResult(s -> {
            jdbc.queryForList("SELECT id FROM importacao WHERE id = ? FOR UPDATE", id);
            locked.countDown();
            try {
                if (!release.await(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the lock holder timed out waiting for release");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }), "lock-holder");
        holder.start();
        List<Thread> workers = List.of();
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).as("the holder took the lock").isTrue();
            workers = start.call();
        } finally {
            release.countDown();
            holder.join(30_000);
            for (Thread w : workers) {
                w.join(30_000);
            }
        }
        assertThat(holder.isAlive()).as("the lock holder finished").isFalse();
        for (Thread w : workers) {
            assertThat(w.isAlive()).as(w.getName() + " finished").isFalse();
        }
    }

    /** Records what `work` returned, or what it threw. */
    private static Thread worker(String name, AtomicReference<Object> outcome, Callable<Object> work) {
        return new Thread(() -> {
            try {
                outcome.set(work.call());
            } catch (Throwable t) {
                outcome.set(t);
            }
        }, name);
    }

    /** Waits until `n` sessions of this database are blocked on a lock. */
    private void awaitLockWaiters(int n) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Long waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity"
                    + " WHERE datname = current_database() AND wait_event_type = 'Lock'", Long.class);
            if (waiting != null && waiting >= n) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("expected " + n + " sessions blocked on a lock");
    }

    private static boolean importNotFound(Object outcome) {
        return outcome instanceof AdminApiException e && e.getStatus() == HttpStatus.NOT_FOUND
                && "IMPORT_NOT_FOUND".equals(e.getBody().get("code"));
    }

    // --- fixtures ---

    private String admin() {
        return AdminFixtures.bearer(jwt, "ADMIN");
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private Subject subject(String name) {
        Subject s = subjects.save(new Subject(name)); // committed: this class has no test transaction
        subjectIds.add(s.getId());
        return s;
    }

    private Map<String, Object> threeQuestoes() {
        List<Map<String, Object>> qs = new ArrayList<>(List.of(ImportFixtures.mc(1, "Enunciado " + tag + " 1"),
                ImportFixtures.mc(2, "Enunciado " + tag + " 2"), ImportFixtures.mc(3, "Enunciado " + tag + " 3")));
        Map<String, Object> r = ImportFixtures.result("MULTIPLA_ESCOLHA", qs);
        r.put("gabarito", ImportFixtures.gabarito(Map.of("1", "C", "2", "A", "3", "B")));
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstQuestao(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("questions")).get(0);
    }

    private static Map<String, Object> range(int from, int to, int subjectId) {
        return Map.of("from", from, "to", to, "subject", Map.of("id", subjectId), "hint", "X");
    }

    /** Banca/órgão by id (created here), 2024, cargo, section 0 and the given disciplina ranges. */
    private Map<String, Object> completeFields(List<Map<String, Object>> ranges) {
        ExamBoard board = boards.save(new ExamBoard("Banca " + tag + " por id"));
        boardIds.add(board.getId());
        Agency agency = agencies.save(new Agency("Órgão " + tag));
        agencyIds.add(agency.getId());
        Map<String, Object> f = new HashMap<>();
        f.put("examBoard", Map.of("id", board.getId()));
        f.put("agency", Map.of("id", agency.getId()));
        f.put("year", 2024);
        f.put("role", "Analista");
        f.put("gabaritoSections", List.of(0));
        f.put("subjectRanges", ranges);
        return f;
    }

    /** Three valid questões, every field set: a commit creates all three. */
    private UUID readyImport() throws Exception {
        UUID id = createImport(threeQuestoes());
        putFields(id, completeFields(List.of(range(1, 3, subject("Disciplina " + tag).getId()))));
        return id;
    }

    private UUID createImport(Map<String, Object> result, MockMultipartFile... figures) throws Exception {
        MockMultipartHttpServletRequestBuilder req = multipart("/api/admin/imports")
                .file(ImportFixtures.pdfPart(ImportFixtures.pdf()))
                .file(ImportFixtures.json(json, result));
        for (MockMultipartFile f : figures) {
            req.file(f);
        }
        String body = mvc.perform(req.header("Authorization", admin())).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(body).get("id").asText());
        importIds.add(id);
        return id;
    }

    private void putFields(UUID id, Map<String, Object> fields) throws Exception {
        mvc.perform(put("/api/admin/imports/{id}", id).header("Authorization", admin())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fields))).andExpect(status().isOk());
    }
}
