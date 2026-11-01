package com.juniverse.redis.example.controller;

import com.juniverse.redis.example.dto.AccountDTO;
import com.juniverse.redis.example.service.AccountService;
import java.util.List;
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
 * Controlador de cuentas que muestra el uso de CacheOperationBuilder
 *
 * @author linux
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/v1/accounts")
@Slf4j
public class AccountController {

    private final AccountService accountService;

    /**
     * Constructor para la inyección de dependencias
     * @param accountService servicio de cuentas
     */
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Obtiene todas las cuentas
     * @return ResponseEntity con la lista de cuentas
     */
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<AccountDTO>> getAccounts() {
        log.info("GET /api/v1/accounts - Fetching all accounts");
        List<AccountDTO> response = accountService.getAccounts();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Obtiene una cuenta por su identificador
     * @param id identificador de la cuenta
     * @param forceRefresh fuerza la actualización de la caché
     * @return ResponseEntity con la cuenta o NOT_FOUND
     */
    @GetMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<AccountDTO> getAccount(
            @PathVariable("id") Long id,
            @RequestParam(value = "forceRefresh", defaultValue = "false") boolean forceRefresh) {
        log.info("GET /api/v1/accounts/{} - forceRefresh: {}", id, forceRefresh);
        AccountDTO response = accountService.getAccountById(id, forceRefresh);
        if (response == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Limpia toda la caché de cuentas
     * @return ResponseEntity con estado OK
     */
    @GetMapping(path = "/refresh", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> refreshAll() {
        log.info("GET /api/v1/accounts/refresh - Clearing entire accounts cache");
        accountService.clearCache();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Elimina una cuenta por su identificador
     * @param id identificador de la cuenta
     * @return ResponseEntity con NO_CONTENT o NOT_FOUND
     */
    @DeleteMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        log.info("DELETE /api/v1/accounts/{}", id);
        boolean deleted = accountService.deleteAccount(id);
        if (!deleted) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Crea o actualiza una cuenta
     * @param account datos de la cuenta
     * @return ResponseEntity con la cuenta creada o actualizada
     */
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<AccountDTO> save(@RequestBody AccountDTO account) {
        log.info("POST /api/v1/accounts - Creating/updating account: {}",
                account.getAccountNumber());
        AccountDTO response = accountService.saveOrUpdateAccount(account);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
