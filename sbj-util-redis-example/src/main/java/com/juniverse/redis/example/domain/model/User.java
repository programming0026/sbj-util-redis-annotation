package com.juniverse.redis.example.domain.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User of the domain.
 * Plain Java object with no framework dependencies; it is {@link Serializable}
 * because the cache adapters store it in Redis.
 *
 * @author Java Universe
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private LocalDateTime createdAt;
}
