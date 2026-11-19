package com.juniverse.redis.example.infrastructure.adapter.in.web.dto;

import com.juniverse.redis.example.domain.model.Product;
import java.io.Serial;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
/**
 * Product data transfer object of the REST API (web adapter)
 *
 * @author Java Universe
 */
@Getter
@Setter
@Builder
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

    /**
     * Builds the web representation of a domain object
     * @param domain domain object
     * @return the DTO, or {@code null} if the domain object is {@code null}
     */
    public static ProductDTO fromDomain(Product domain) {
        if (domain == null) {
            return null;
        }
        return ProductDTO.builder()
                .id(domain.getId())
                .name(domain.getName())
                .mount(domain.getMount())
                .date(domain.getDate())
                .build();
    }

    /**
     * Converts this DTO into a domain object
     * @return the domain object
     */
    public Product toDomain() {
        return Product.builder()
                .id(id)
                .name(name)
                .mount(mount)
                .date(date)
                .build();
    }
}
