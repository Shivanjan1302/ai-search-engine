package com.dronzer.aisearch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.dronzer.aisearch.entity.Note;
import com.dronzer.aisearch.entity.User;

public interface NoteRepository extends Repository<Note, Long> {

	<S extends Note> S save(S note);

	void delete(Note note);

	List<Note> findByUserOrderByIdAsc(User user);

	Optional<Note> findByIdAndUser(Long id, User user);
}