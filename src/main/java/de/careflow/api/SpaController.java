package de.careflow.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(value = {"/", "/ward", "/lab", "/interop"})
    public String index() {
        return "forward:/index.html";
    }
}
