package com.juniverse.redis.example.dto;

import java.io.Serial;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
/**
 * Objeto de transferencia de producto
 *
 * @author Java Universe
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO implements java.io.Serializable{

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Double mount;
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

}
