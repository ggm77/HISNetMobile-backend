package com.seohamin.hisnetmobile.global.exception.handler;

import com.seohamin.hisnetmobile.global.exception.CustomException;
import com.seohamin.hisnetmobile.global.exception.constants.ExceptionCode;
import com.seohamin.hisnetmobile.global.exception.response.ExceptionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Slf4j
public class CustomizedResponseEntityExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ExceptionResponse> handleCustomException(final CustomException ex) {

        if (ex.getCause() != null) {
            log.error("EXCEPTION CODE {}, message: {}", ex.getExceptionCode().name(), ex.getMessage(), ex.getCause());
        }

        final ExceptionResponse response = new ExceptionResponse(ex.getExceptionCode());

        return ResponseEntity.status(ex.getExceptionCode().getHttpStatus()).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ExceptionResponse> handleAllException(final Exception ex) {
        log.error("INTERNAL SERVER ERROR: {}", ex.getMessage(), ex);

        final ExceptionResponse response = new ExceptionResponse(ExceptionCode.INTERNAL_SERVER_ERROR);

        return ResponseEntity.internalServerError().body(response);
    }
}