package com.urlshortener.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.auth.AuthenticatedOwner;
import com.urlshortener.auth.AuthenticatedPrincipal;
import com.urlshortener.auth.IssuedSession;
import com.urlshortener.auth.OwnerSessionIssuer;
import com.urlshortener.dto.SessionInfoResponse;
import com.urlshortener.dto.SessionResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

/**
 * Explicit "continue as guest" login: creates a new anonymous owner and issues a
 * session for it. Unlike the previous API-key bootstrap, this is only ever called
 * when the user deliberately chooses guest mode on the frontend, not automatically.
 */
@RestController
@RequestMapping("/api/auth")
public class GuestAuthController {

    private final OwnerSessionIssuer sessionIssuer;

    public GuestAuthController(OwnerSessionIssuer sessionIssuer) {
        this.sessionIssuer = sessionIssuer;
    }

    @PostMapping("/guest")
    @SecurityRequirements
    @Operation(summary = "Start a guest session")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Guest session created")
    })
    public ResponseEntity<SessionResponse> continueAsGuest() {
        IssuedSession issued = sessionIssuer.issueForNewGuest();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SessionResponse(issued.token(), issued.provider()));
    }

    /**
     * Reports who the caller currently is.
     *
     * <p>The client uses this to decide which actions to offer - a guest is not shown a
     * Delete control, for instance. That is presentation only: the API enforces the same
     * rule independently, because anything the browser decides can be changed by whoever
     * is holding the browser.
     */
    @GetMapping("/session")
    @SecurityRequirement(name = com.urlshortener.config.OpenApiConfig.BEARER_SCHEME_NAME)
    @Operation(summary = "Get the current session identity")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session identity returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid session token",
                    content = @Content(schema = @Schema(implementation = com.urlshortener.exception.ErrorResponse.class)))
    })
    public SessionInfoResponse currentSession(@AuthenticatedOwner AuthenticatedPrincipal principal) {
        return new SessionInfoResponse(principal.provider(), principal.isGuest());
    }
}
