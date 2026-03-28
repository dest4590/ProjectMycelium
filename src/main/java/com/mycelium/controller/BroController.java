package com.mycelium.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BroController {
    @GetMapping("/bro")
    public String bro() {
        return "hi bro";
    }
}
