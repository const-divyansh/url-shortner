package com.urlshortener.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.urlshortener.auth.IssuedSession;
import com.urlshortener.auth.OwnerSessionIssuer;
import com.urlshortener.dto.SessionResponse;

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
    public ResponseEntity<SessionResponse> continueAsGuest() {
        IssuedSession issued = sessionIssuer.issueForNewGuest();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new SessionResponse(issued.token(), issued.provider()));
    }
}
