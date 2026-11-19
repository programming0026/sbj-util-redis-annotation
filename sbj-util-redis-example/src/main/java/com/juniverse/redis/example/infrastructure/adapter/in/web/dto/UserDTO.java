package com.juniverse.redis.example.infrastructure.adapter.in.web.dto;

import com.juniverse.redis.example.domain.model.User;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * User data transfer object of the REST API (web adapter)
 *
 * @author Java Universe
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO implements java.io.Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Builds the web representation of a domain object
     * @param domain domain object
     * @return the DTO, or {@code null} if the domain object is {@code null}
     */
    public static UserDTO fromDomain(User domain) {
        if (domain == null) {
            return null;
        }
        return UserDTO.builder()
                .id(domain.getId())
                .username(domain.getUsername())
                .email(domain.getEmail())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    /**
     * Converts this DTO into a domain object
     * @return the domain object
     */
    public User toDomain() {
        return User.builder()
                .id(id)
                .username(username)
                .email(email)
                .createdAt(createdAt)
                .build();
    }
}
