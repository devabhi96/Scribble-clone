package com.scribble.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValiddationErrors(MethodArgumentNotValidException ex){
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage())
                .orElse("Invalid request. Please check your input and try again.");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error",message));
    }
}