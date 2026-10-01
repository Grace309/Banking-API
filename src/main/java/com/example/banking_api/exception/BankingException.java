package com.example.banking_api.exception;

public class BankingException extends RuntimeException {

	private final Reason reason;

	public BankingException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	public enum Reason {
		ACCOUNT_NOT_FOUND,
		ACCOUNT_INACTIVE,
		INSUFFICIENT_FUNDS,
		SAME_ACCOUNT,
		BALANCE_LIMIT_EXCEEDED
	}
}
