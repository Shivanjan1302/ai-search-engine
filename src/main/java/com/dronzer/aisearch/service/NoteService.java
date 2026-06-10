package com.dronzer.aisearch.service;

import com.dronzer.aisearch.entity.Note;
import com.dronzer.aisearch.repository.NoteRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NoteService {

    private final NoteRepository noteRepository;

    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    public Note saveNote(String title) {

        Note note = new Note(title);

        return noteRepository.save(note);
    }

    public List<Note> getAllNotes() {

        return noteRepository.findAll();
    }
}