package com.direitoria.questoes.catalog;

import com.direitoria.questoes.dto.LookupResponse;
import com.direitoria.questoes.repository.AgencyRepository;
import com.direitoria.questoes.repository.ExamBoardRepository;
import com.direitoria.questoes.repository.SubjectRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LookupController {

    private final SubjectRepository subjects;
    private final ExamBoardRepository examBoards;
    private final AgencyRepository agencies;

    public LookupController(SubjectRepository subjects, ExamBoardRepository examBoards, AgencyRepository agencies) {
        this.subjects = subjects;
        this.examBoards = examBoards;
        this.agencies = agencies;
    }

    @GetMapping("/subjects")
    public List<LookupResponse> subjects() {
        return subjects.findAllByOrderByNomeAsc().stream().map(LookupMapper::toLookup).toList();
    }

    @GetMapping("/exam-boards")
    public List<LookupResponse> examBoards() {
        return examBoards.findAllByOrderByNomeAsc().stream().map(LookupMapper::toLookup).toList();
    }

    @GetMapping("/agencies")
    public List<LookupResponse> agencies() {
        return agencies.findAllByOrderByNomeAsc().stream().map(LookupMapper::toLookup).toList();
    }
}
