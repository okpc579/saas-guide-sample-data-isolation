package com.example.dataisolation.api;
public class ResourceNotFoundException extends RuntimeException { public ResourceNotFoundException() { super("Service request was not found"); } }
