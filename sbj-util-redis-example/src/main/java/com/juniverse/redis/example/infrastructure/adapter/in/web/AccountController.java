package com.juniverse.redis.example.infrastructure.adapter.in.web;

import com.juniverse.redis.example.infrastructure.adapter.in.web.dto.AccountDTO;
import com.juniverse.redis.example.domain.port.in.AccountUseCase;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accounts controller showing the use of CacheOperationBuilder
 *
 * @author Java Universe
 */
@RestController
@Tag(name = "Accounts", description = "Accounts cached with the CacheOperationBuilder pattern")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/v1/accounts")
@Slf4j
public class AccountController {

    private final AccountUseCase accountUseCase;

    /**
     * Constructor for dependency injection
     * @param accountUseCase account use cases (inbound port)
     */
    public AccountController(AccountUseCase accountUseCase) {
        this.accountUseCase = accountUseCase;
    }

    /**
     * Gets all accounts
     * @return ResponseEntity with the list of accounts
     */
    @Operation(summary = "Get all accounts")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of accounts")
    })
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<AccountDTO>> getAccounts() {
        log.info("GET /api/v1/accounts - Fetching all accounts");
        List<AccountDTO> response = accountUseCase.getAccounts().stream().map(AccountDTO::fromDomain).toList();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Gets an account by its id
     * @param id account id
     * @param forceRefresh forces a cache refresh
     * @return ResponseEntity with the account or NOT_FOUND
     */
    @Operation(summary = "Get an account by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<AccountDTO> getAccount(
            @Parameter(description = "Resource id") @PathVariable("id") Long id,
            @Parameter(description = "Forces a cache refresh") @RequestParam(value = "forceRefresh", defaultValue = "false") boolean forceRefresh) {
        log.info("GET /api/v1/accounts/{} - forceRefresh: {}", id, forceRefresh);
        AccountDTO response = AccountDTO.fromDomain(accountUseCase.getAccountById(id, forceRefresh).orElse(null));
        if (response == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Clears the entire accounts cache
     * @return ResponseEntity with OK status
     */
    @Operation(summary = "Clear the accounts cache")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cache cleared")
    })
    @GetMapping(path = "/refresh", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> refreshAll() {
        log.info("GET /api/v1/accounts/refresh - Clearing entire accounts cache");
        accountUseCase.clearCache();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Deletes an account by its id
     * @param id account id
     * @return ResponseEntity with NO_CONTENT or NOT_FOUND
     */
    @Operation(summary = "Delete an account by id")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account deleted"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @DeleteMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> delete(@Parameter(description = "Resource id") @PathVariable("id") Long id) {
        log.info("DELETE /api/v1/accounts/{}", id);
        boolean deleted = accountUseCase.deleteAccount(id);
        if (!deleted) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Creates or updates an account
     * @param account account data
     * @return ResponseEntity with the created or updated account
     */
    @Operation(summary = "Create or update an account")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created or updated")
    })
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<AccountDTO> save(@RequestBody AccountDTO account) {
        log.info("POST /api/v1/accounts - Creating/updating account: {}",
                account.getAccountNumber());
        AccountDTO response = AccountDTO.fromDomain(accountUseCase.saveOrUpdateAccount(account.toDomain()));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
