package com.dronzer.aisearch.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

import com.dronzer.aisearch.entity.Note;
import com.dronzer.aisearch.entity.User;
import com.dronzer.aisearch.exception.ResourceNotFoundException;
import com.dronzer.aisearch.repository.NoteRepository;
import com.dronzer.aisearch.repository.UserRepository;

class NoteServiceTest {

    private final NoteRepository noteRepository = mock(NoteRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    private NoteService noteService;
    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        noteService = new NoteService(noteRepository, userRepository);
        userA = user(1L, "user-a@example.test");
        userB = user(2L, "user-b@example.test");
        when(userRepository.findByEmail(userA.getEmail())).thenReturn(Optional.of(userA));
        when(userRepository.findByEmail(userB.getEmail())).thenReturn(Optional.of(userB));
    }

    @Test
    void createsNotesForTheAuthenticatedOwner() {
        when(noteRepository.save(any(Note.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Note noteA = noteService.saveNote("A note", userA.getEmail());
        Note noteB = noteService.saveNote("B note", userB.getEmail());

        assertThat(noteA.getUser()).isSameAs(userA);
        assertThat(noteB.getUser()).isSameAs(userB);
    }

    @Test
    void listsOnlyTheAuthenticatedUsersNotes() {
        Note noteA = note("A note", userA);
        Note noteB = note("B note", userB);
        when(noteRepository.findByUserOrderByIdAsc(userA)).thenReturn(List.of(noteA));
        when(noteRepository.findByUserOrderByIdAsc(userB)).thenReturn(List.of(noteB));

        assertThat(noteService.getAllNotes(userA.getEmail())).containsExactly(noteA);
        assertThat(noteService.getAllNotes(userB.getEmail())).containsExactly(noteB);

        verify(noteRepository).findByUserOrderByIdAsc(userA);
        verify(noteRepository).findByUserOrderByIdAsc(userB);
    }

    @Test
    void rejectsCrossUserReadUpdateAndDeleteById() {
        when(noteRepository.findByIdAndUser(10L, userA)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.getNote(10L, userA.getEmail()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> noteService.updateNote(10L, "changed", userA.getEmail()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> noteService.deleteNote(10L, userA.getEmail()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(noteRepository, times(3)).findByIdAndUser(10L, userA);
        verify(noteRepository, never()).save(any(Note.class));
        verify(noteRepository, never()).delete(any(Note.class));
    }

    @Test
    void updatesAndDeletesOnlyAnOwnedNote() {
        Note note = note("before", userA);
        when(noteRepository.findByIdAndUser(10L, userA)).thenReturn(Optional.of(note));
        when(noteRepository.save(note)).thenReturn(note);

        assertThat(noteService.updateNote(10L, "after", userA.getEmail()).getTitle())
                .isEqualTo("after");
        noteService.deleteNote(10L, userA.getEmail());

        verify(noteRepository).save(note);
        verify(noteRepository).delete(note);
    }

    private User user(Long id, String email) {
        User user = new User(email, "encoded-password", LocalDateTime.now());
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Note note(String title, User user) {
        Note note = new Note(title);
        note.setUser(user);
        return note;
    }
}
