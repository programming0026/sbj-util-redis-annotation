package com.juniverse.redis.example.domain.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bank account of the domain.
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
public class Account implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String accountNumber;
    private String accountHolder;
    private BigDecimal balance;
    private String accountType;
    private LocalDateTime createdAt;
}
