package com.juniverse.redis.example.infrastructure.adapter.in.web.dto;

import com.juniverse.redis.example.domain.model.Account;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
/**
 * Account data transfer object of the REST API (web adapter)
 *
 * @author Java Universe
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDTO implements java.io.Serializable{

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String accountNumber;
    private String accountHolder;
    private BigDecimal balance;
    private String accountType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * Builds the web representation of a domain object
     * @param domain domain object
     * @return the DTO, or {@code null} if the domain object is {@code null}
     */
    public static AccountDTO fromDomain(Account domain) {
        if (domain == null) {
            return null;
        }
        return AccountDTO.builder()
                .id(domain.getId())
                .accountNumber(domain.getAccountNumber())
                .accountHolder(domain.getAccountHolder())
                .balance(domain.getBalance())
                .accountType(domain.getAccountType())
                .createdAt(domain.getCreatedAt())
                .build();
    }

    /**
     * Converts this DTO into a domain object
     * @return the domain object
     */
    public Account toDomain() {
        return Account.builder()
                .id(id)
                .accountNumber(accountNumber)
                .accountHolder(accountHolder)
                .balance(balance)
                .accountType(accountType)
                .createdAt(createdAt)
                .build();
    }
}
