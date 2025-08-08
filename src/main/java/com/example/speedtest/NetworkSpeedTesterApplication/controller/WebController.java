package com.example.speedtest.NetworkSpeedTesterApplication.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

public class WebController {
	
	@GetMapping("/")
    public String index(Model model) {
        return "index";
    }

}