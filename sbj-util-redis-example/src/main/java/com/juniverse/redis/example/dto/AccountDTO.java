package com.juniverse.redis.example.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
/**
 * Account Data Transfer Object
 *
 * @author linux
 */
@Getter
@Setter
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

}
