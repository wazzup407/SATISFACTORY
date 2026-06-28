package com.ficsit.calculator.model;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

public class Recipe {
    public String name;
    public String machine;
    
    // SerializedName mówi Gsonowi, że w JSON-ie klucz nazywa się "output_qty", 
    // ale my w Javie wolimy standardową nazwę z wielką literą
    @SerializedName("output_qty")
    public double outputQty;
    
    public Map<String, Double> inputs;
}