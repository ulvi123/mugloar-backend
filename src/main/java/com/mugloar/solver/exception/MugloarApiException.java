package com.mugloar.solver.exception;

public class MugloarApiException extends RuntimeException {
	public MugloarApiException(String message, Throwable cause){
		super(message,cause);
	}

	public MugloarApiException(String message){
		super(message);
	}
}