package demo;

import javax.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {
    @GetMapping("/greeting")
    public String greeting() {
        return "Hello, visitor";
    }

    @PostMapping("/greeting")
    public String greet(@Valid @RequestBody GreetingRequest request) {
        return "Hello, " + request.name().trim();
    }
}
