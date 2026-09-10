package com.dronzer.aisearch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.dronzer.aisearch.entity.Note;
import com.dronzer.aisearch.entity.User;

public interface NoteRepository extends JpaRepository<Note, Long> {

	List<Note> findByUserOrderByIdAsc(User user);

	Optional<Note> findByIdAndUser(Long id, User user);
}