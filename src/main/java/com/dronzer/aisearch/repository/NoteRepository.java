package com.dronzer.aisearch.repository;

import com.dronzer.aisearch.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteRepository extends JpaRepository<Note, Long> {

}