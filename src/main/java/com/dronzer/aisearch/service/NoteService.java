package com.dronzer.aisearch.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dronzer.aisearch.entity.Note;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.exception.ResourceNotFoundException;
import com.dronzer.aisearch.repository.NoteRepository;
import com.dronzer.aisearch.repository.UserRepository;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;

    public NoteService(NoteRepository noteRepository, UserRepository userRepository) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Note saveNote(String title, String email) {
        User user = findUser(email);

        Note note = new Note(title);
        note.setUser(user);

        return noteRepository.save(note);
    }

    @Transactional(readOnly = true)
    public List<Note> getAllNotes(String email) {
        User user = findUser(email);
        return noteRepository.findByUserOrderByIdAsc(user);
    }

    @Transactional(readOnly = true)
    public Note getNote(Long id, String email) {
        return findOwnedNote(id, email);
    }

    @Transactional
    public Note updateNote(Long id, String title, String email) {
        Note note = findOwnedNote(id, email);
        note.setTitle(title);
        return noteRepository.save(note);
    }

    @Transactional
    public void deleteNote(Long id, String email) {
        Note note = findOwnedNote(id, email);
        noteRepository.delete(note);
    }

    private Note findOwnedNote(Long id, String email) {
        User user = findUser(email);
        return noteRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Note not found"));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}