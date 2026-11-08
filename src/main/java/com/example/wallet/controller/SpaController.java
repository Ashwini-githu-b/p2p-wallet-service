package com.example.wallet.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/accounts", "/accounts/**", "/lab"})
    public String index() {
        return "forward:/index.html";
    }
}

