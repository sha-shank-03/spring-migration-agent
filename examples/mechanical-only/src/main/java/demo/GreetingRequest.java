package demo;

import javax.validation.constraints.NotBlank;

public record GreetingRequest(@NotBlank String name) {}
