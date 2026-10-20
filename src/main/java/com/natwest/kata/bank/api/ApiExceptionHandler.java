package com.natwest.kata.bank.api;

import com.natwest.kata.bank.domain.AccountNotFoundException;
import com.natwest.kata.bank.domain.InsufficientFundsException;
import com.natwest.kata.bank.domain.InvalidAmountException;
import com.natwest.kata.bank.domain.InvalidTransferException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Translates domain rule violations into RFC 9457 problem responses.
 * <ul>
 *   <li>400 - the request itself is wrong (bad amount, transfer to self, missing fields)</li>
 *   <li>404 - an account in the request does not exist</li>
 *   <li>422 - the request is well-formed but the account cannot afford it</li>
 * </ul>
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail handle(AccountNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Account not found", e);
    }

    @ExceptionHandler
    ProblemDetail handle(InsufficientFundsException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds", e);
    }

    @ExceptionHandler
    ProblemDetail handle(InvalidAmountException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid amount", e);
    }

    @ExceptionHandler
    ProblemDetail handle(InvalidTransferException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid transfer", e);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = e.getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle("Invalid request");
        return handleExceptionInternal(e, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, RuntimeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problem.setTitle(title);
        return problem;
    }
}
