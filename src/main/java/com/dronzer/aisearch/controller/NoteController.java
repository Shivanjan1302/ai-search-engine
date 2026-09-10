package com.dronzer.aisearch.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dronzer.aisearch.dto.CreateNoteRequest;
import com.dronzer.aisearch.dto.NoteResponse;
import com.dronzer.aisearch.dto.UpdateNoteRequest;
import com.dronzer.aisearch.service.NoteService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    public NoteResponse createNote(
            @Valid @RequestBody CreateNoteRequest request,
            @AuthenticationPrincipal String email) {

        return toResponse(noteService.saveNote(request.getTitle(), email));
    }

    @GetMapping
    public List<NoteResponse> getAllNotes(
            @AuthenticationPrincipal String email) {

        return noteService.getAllNotes(email).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public NoteResponse getNote(
            @PathVariable Long id,
            @AuthenticationPrincipal String email) {

        return toResponse(noteService.getNote(id, email));
    }

    @PutMapping("/{id}")
    public NoteResponse updateNote(
            @PathVariable Long id,
            @Valid @RequestBody UpdateNoteRequest request,
            @AuthenticationPrincipal String email) {

        return toResponse(noteService.updateNote(id, request.getTitle(), email));
    }

    @DeleteMapping("/{id}")
    public void deleteNote(
            @PathVariable Long id,
            @AuthenticationPrincipal String email) {

        noteService.deleteNote(id, email);
    }

    private NoteResponse toResponse(com.dronzer.aisearch.entity.Note note) {
        return new NoteResponse(note.getId(), note.getTitle());
    }
}