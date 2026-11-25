package com.roommatrix.backend.entity;

import com.roommatrix.backend.entity.enums.Gender;
import com.roommatrix.backend.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true, length = 10)
    private String mobileNumber;

    @Column(nullable = false, length = 50)
    private String firstName;

    @Column(length = 50)
    private String middleName;

    @Column(nullable = false, length = 50)
    private String lastName;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder.Default
    private boolean isActive = true;

    // ─── Native JSON Column mapping Role -> referenceId ─────────────
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    @Builder.Default
    private Map<Role, Long> roleReferences = new EnumMap<>(Role.class);

    // ─── Helper Methods ─────────────────────────────────────────────

    public String getFullName() {
        StringBuilder sb = new StringBuilder(firstName != null ? firstName : "");
        if (middleName != null && !middleName.isBlank()) {
            sb.append(" ").append(middleName);
        }
        if (lastName != null && !lastName.isBlank()) {
            sb.append(" ").append(lastName);
        }
        return sb.toString().trim();
    }

    public Set<Role> getRoles() {
        return roleReferences != null ? roleReferences.keySet() : Set.of();
    }

    public Long getReferenceId(Role role) {
        return roleReferences != null ? roleReferences.get(role) : null;
    }

    public void addRole(Role role, Long referenceId) {
        if (this.roleReferences == null) {
            this.roleReferences = new EnumMap<>(Role.class);
        }
        this.roleReferences.put(role, referenceId);
    }
}
