package com.dronzer.aisearch.controller;

import com.dronzer.aisearch.dto.CreateNoteRequest;
import com.dronzer.aisearch.entity.Note;
import com.dronzer.aisearch.service.NoteService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    public Note createNote(@RequestBody CreateNoteRequest request) {

        return noteService.saveNote(request.getTitle());
    }

    @GetMapping
    public List<Note> getAllNotes() {

        return noteService.getAllNotes();
    }
}