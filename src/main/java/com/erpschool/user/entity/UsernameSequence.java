package com.erpschool.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "username_sequences")
public class UsernameSequence {

    @EmbeddedId
    private UsernameSequenceId id;

    @Column(name = "next_value", nullable = false)
    private int nextValue = 1;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public int consume() {
        int current = nextValue;
        nextValue = current + 1;
        updatedAt = Instant.now();
        return current;
    }
}
