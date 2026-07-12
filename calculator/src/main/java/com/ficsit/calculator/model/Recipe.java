package com.ficsit.calculator.model;

import java.util.Map;

public class Recipe {
    public String id; // Dodane pole do śledzenia użytej receptury
    public String machine;
    public double power;
    
    // Surowce wchodzące (np. "iron_ore": 30.0)
    public Map<String, Double> inputs;
    
    // Surowce wychodzące (np. "iron_ingot": 30.0)
    public Map<String, Double> outputs;
}