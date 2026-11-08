package com.seasonplan.lifecycle.api;

import com.seasonplan.lifecycle.application.shared.PollenTreeUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PollenTreeUnavailableAdviceTest {
    @RestController
    static class FailingController {
        @GetMapping("/failure")
        void fail() {
            throw new PollenTreeUnavailableException("API key=secret; provider private response", new RuntimeException("secret"));
        }
    }

    @Test
    void upstreamFailureProducesOnlyGeneric503WithoutProviderOrCredentialDetails() throws Exception {
        MockMvcBuilders.standaloneSetup(new FailingController()).setControllerAdvice(new LifecycleExceptionHandler()).build()
                .perform(get("/failure")).andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"message\":\"Pollen Tree is unavailable\"}", true));
    }
}
