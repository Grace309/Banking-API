package com.example.banking_api.exception;

import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.example.banking_api.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BankingException.class)
	public ResponseEntity<ErrorResponse> handleBankingException(BankingException exception) {
		HttpStatus status = switch (exception.reason()) {
			case ACCOUNT_NOT_FOUND -> HttpStatus.NOT_FOUND;
			case SAME_ACCOUNT -> HttpStatus.BAD_REQUEST;
			case INSUFFICIENT_FUNDS, BALANCE_LIMIT_EXCEEDED, ACCOUNT_INACTIVE -> HttpStatus.CONFLICT;
		};
		return ResponseEntity.status(status)
				.body(new ErrorResponse(exception.reason().name(), exception.getMessage(), Map.of()));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> fieldErrors = new TreeMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fieldErrors.merge(error.getField(), error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
						(first, second) -> first + "; " + second));
		return new ResponseEntity<>(new ErrorResponse("VALIDATION_ERROR", "Request validation failed", fieldErrors),
				headers, status);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return new ResponseEntity<>(new ErrorResponse("INVALID_REQUEST",
				"Request body is missing, malformed, or contains an invalid value", Map.of()), headers, status);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String field = exception instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : "parameter";
		return new ResponseEntity<>(new ErrorResponse("INVALID_REQUEST", "Invalid request parameter",
				Map.of(field, "A valid UUID is required")), headers, status);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		HttpStatus httpStatus = HttpStatus.resolve(status.value());
		String code = httpStatus == null ? "REQUEST_FAILED" : httpStatus.name();
		String message = httpStatus == null ? "Request failed" : httpStatus.getReasonPhrase();
		return new ResponseEntity<>(new ErrorResponse(code, message, Map.of()), headers, status);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
		LOG.error("Unexpected request failure", exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred", Map.of()));
	}
}
