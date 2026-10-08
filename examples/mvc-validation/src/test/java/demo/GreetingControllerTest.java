package demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GreetingController.class)
class GreetingControllerTest {
    @Autowired MockMvc mvc;

    @Test void greetingRemainsStable() throws Exception {
        mvc.perform(get("/greeting")).andExpect(status().isOk()).andExpect(content().string("Hello, visitor"));
    }

    @Test void blankInputIsRejected() throws Exception {
        mvc.perform(post("/greeting").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test void surroundingWhitespaceIsTrimmed() throws Exception {
        mvc.perform(post("/greeting").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Ada \"}"))
            .andExpect(status().isOk()).andExpect(content().string("Hello, Ada"));
    }
}
