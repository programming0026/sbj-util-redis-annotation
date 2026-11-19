package com.juniverse.redis.example.domain.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Product of the domain.
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
public class Product implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Double mount;
    private LocalDate date;
}
