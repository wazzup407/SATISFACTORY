package com.ficsit.calculator.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.*;


public class FactoryEngine {

    public Map<String, Recipe> recipesDB;
    public boolean exactMode = false;

    public static class Edge {
        public String from, to;
        public double amount;
        public Edge(String from, String to, double amount) { this.from = from; this.to = to; this.amount = amount; }
    }

    public static class NodeData {
        public String id;
        public String label;
        public String shape;
        public String color;
        public NodeData(String id, String label, String shape, String color) {
            this.id = id; this.label = label; this.shape = shape; this.color = color;
        }
    }

    public static class CalculationResult {
        public Map<String, NodeData> nodes = new HashMap<>();
        public List<Edge> edges = new ArrayList<>();
        public double totalPowerMW = 0.0;
        
        public int totalBuildings = 0;
        public Map<String, Double> ingredients = new HashMap<>(); 
        public Set<String> usedAltRecipes = new HashSet<>();
    }

    public void loadDatabase(String filePath) {
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Recipe>>(){}.getType();
            recipesDB = gson.fromJson(reader, type);
            
            for (Map.Entry<String, Recipe> entry : recipesDB.entrySet()) {
                entry.getValue().id = entry.getKey();
            }
        } catch (Exception e) {
            System.err.println("[Błąd] Nie udało się wczytać pliku recipes.json.");
            e.printStackTrace();
        }
    }

    public String getName(String key) {
        // Zmieniony Regex: Szuka "_alt" na końcu, opcjonalnie z cyframi po nim
        String baseKey = key.replaceAll("_alt\\d*$", "");
        String name = baseKey;
        
        switch (baseKey) {
            case "iron_ore": name = "Ruda Żelaza"; break;
            case "copper_ore": name = "Ruda Miedzi"; break;
            case "limestone": name = "Wapień"; break;
            case "coal": name = "Węgiel"; break;
            case "crude_oil": name = "Ropa Naftowa"; break;
            case "raw_quartz": name = "Surowy Kwarzec"; break;
            case "bauxite": name = "Boksyt"; break;
            case "water": name = "Woda"; break;
            case "iron_ingot": name = "Sztabka Żelaza"; break;
            case "copper_ingot": name = "Sztabka Miedzi"; break;
            case "steel_ingot": name = "Sztabka Stali"; break;
            case "iron_plate": name = "Żelazna Płyta"; break;
            case "iron_rod": name = "Żelazny Pręt"; break;
            case "wire": name = "Drut"; break;
            case "cable": name = "Przewód"; break;
            case "concrete": name = "Beton"; break;
            case "screw": name = "Śruba"; break;
            case "reinforced_plate": name = "Wzmocniona Płyta"; break;
            case "rotor": name = "Wirnik"; break;
            case "stator": name = "Stojan"; break;
            case "motor": name = "Silnik"; break;
            case "modular_frame": name = "Rama Modułowa"; break;
            case "smart_plating": name = "Inteligentna Powłoka"; break;
            case "copper_sheet": name = "Blacha Miedziana"; break;
            case "steel_beam": name = "Belka Stalowa"; break;
            case "steel_pipe": name = "Rura Stalowa"; break;
            case "encased_beam": name = "Wzmocniona Belka Przem."; break;
            case "plastic": name = "Plastik"; break;
            case "rubber": name = "Guma"; break;
            case "heavy_oil_residue": name = "Ciężki Osad Olejowy"; break;
            case "polymer_resin": name = "Żywica Polimerowa"; break;
            case "petroleum_coke": name = "Koks Naftowy"; break;
            case "fuel": name = "Paliwo"; break;
            case "circuit_board": name = "Płytka Drukowana"; break;
            case "computer": name = "Komputer"; break;
            case "heavy_frame": name = "Ciężka Rama Modułowa"; break;
            case "versatile_framework": name = "Wszechstronna Rama"; break;
            case "modular_engine": name = "Silnik Modułowy"; break;
            case "adaptive_unit": name = "Adaptacyjna Jednostka"; break;
            case "automated_wiring": name = "Automatyczne Okablowanie"; break;
            case "silica": name = "Krzemionka"; break;
            case "alumina_solution": name = "Roztwór Tlenku Glinu"; break;
            case "aluminum_scrap": name = "Złom Aluminiowy"; break;
            case "sulfuric_acid": name = "Kwas Siarkowy"; break;
        }

        // Dodanie formatowania dla wielu alternatyw
        if (key.matches(".*_alt\\d*$")) {
            String num = key.replace(baseKey + "_alt", "");
            return num.isEmpty() ? name + " (Alt)" : name + " (Alt " + num + ")";
        }
        return name;
    }

    private List<String> getTopologicalOrder() {
        List<String> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String item : recipesDB.keySet()) {
            dfs(item, visited, visiting, order);
        }
        Collections.reverse(order);
        return order;
    }

    private void dfs(String item, Set<String> visited, Set<String> visiting, List<String> order) {
        if (visited.contains(item)) return;
        if (visiting.contains(item)) return; // Zapobieganie wpadnięciu w pętlę nieskończoną
        visiting.add(item);
        
        Recipe r = recipesDB.get(item);
        if (r != null) {
            // Przetwarzanie wejść (normalne zależności)
            if (r.inputs != null) {
                for (String inp : r.inputs.keySet()) {
                    dfs(inp, visited, visiting, order);
                }
            }
            // NOWOŚĆ: Rejestracja produktów ubocznych
            // Wymusza to na silniku, by najpierw przetwarzał maszyny generujące byproducts
            // zanim zacznie stawiać oddzielne rafinerie na te same surowce!
            if (r.outputs != null) {
                for (String out : r.outputs.keySet()) {
                    if (!out.equals(item)) {
                        dfs(out, visited, visiting, order);
                    }
                }
            }
        }
        visiting.remove(item);
        visited.add(item);
        order.add(item);
    }

    // Pomocnicza metoda do tworzenia połączeń na grafie
    private void addEdge(List<Edge> edges, String from, String to, double amount) {
        if (amount <= 0.001) return;
        for (Edge e : edges) {
            if (e.from.equals(from) && e.to.equals(to)) {
                e.amount += amount;
                return;
            }
        }
        edges.add(new Edge(from, to, amount));
    }

    public CalculationResult calculateFactory(Map<String, Double> demands, Map<String, Double> providedInputs, Set<String> wantedSurplus) {
        CalculationResult result = new CalculationResult();
        
        List<String> topOrder = getTopologicalOrder();
        Map<String, Double> totalItems = new HashMap<>();
        Map<String, Double> usedExternal = new HashMap<>();
        Map<String, Double> globalSurplus = new HashMap<>();
        Map<String, Double> byproductsPool = new HashMap<>(); // Pula wytworzonych resztek
        Map<String, Double> availableInputs = new HashMap<>(providedInputs);

        for (Map.Entry<String, Double> entry : demands.entrySet()) {
            totalItems.put(entry.getKey(), totalItems.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
        }

        Map<String, Double> exactMachinesMap = new HashMap<>();

        for (String item : topOrder) {
            double exactAmount = totalItems.getOrDefault(item, 0.0);
            
            // 1. Sprawdzamy czy mamy ten surowiec wyprodukowany "przy okazji" przez inną maszynę
            double fromPool = Math.min(exactAmount, byproductsPool.getOrDefault(item, 0.0));
            if (fromPool > 0) {
                exactAmount -= fromPool;
                byproductsPool.put(item, byproductsPool.get(item) - fromPool);
            }
            
            if (exactAmount <= 0) continue;

            Recipe recipe = recipesDB.get(item);
            
            // 2. Dobieranie z zasilania z zewnątrz
            double extAvailable = availableInputs.getOrDefault(item, 0.0);
            double takeFromExt = Math.min(exactAmount, extAvailable);
            if (takeFromExt > 0) {
                availableInputs.put(item, extAvailable - takeFromExt);
                exactAmount -= takeFromExt;
                usedExternal.put(item, usedExternal.getOrDefault(item, 0.0) + takeFromExt);
                addEdge(result.edges, "ext_" + item, item, takeFromExt);
            }

            if (recipe == null || recipe.outputs == null || !recipe.outputs.containsKey(item) || exactAmount <= 0) continue;

            double outQty = recipe.outputs.get(item);
            double exactMachines = exactAmount / outQty;
            double full = Math.floor(exactMachines);
            double frac = exactMachines - full;
            double newMachines = exactMachines;

            boolean isFinal = demands.containsKey(item);
            boolean isWanted = wantedSurplus.contains(item);

            if (!exactMode) {
                if (isWanted) {
                    if (frac > 0.001) newMachines = full + 1;
                } else if (isFinal) {
                    if (frac > 0.25 && full >= 1.0) newMachines = full + 1;
                }
            }

            double actualAmount = newMachines * outQty;
            double surplus = actualAmount - exactAmount;
            
            if (surplus > 0.001) globalSurplus.put(item, surplus);
            exactMachinesMap.put(item, newMachines);

            // 3. Obliczanie wejść
            if (recipe.inputs != null) {
                double multiplier = actualAmount / outQty;
                for (Map.Entry<String, Double> inp : recipe.inputs.entrySet()) {
                    double needed = inp.getValue() * multiplier;
                    totalItems.put(inp.getKey(), totalItems.getOrDefault(inp.getKey(), 0.0) + needed);
                    addEdge(result.edges, inp.getKey(), item, needed);
                }
            }
            
            // 4. GENEROWANIE PRODUKTÓW UBOCZNYCH
            for (Map.Entry<String, Double> out : recipe.outputs.entrySet()) {
                if (!out.getKey().equals(item)) {
                    double byproductGenerated = newMachines * out.getValue();
                    if (byproductGenerated > 0.001) {
                        byproductsPool.put(out.getKey(), byproductsPool.getOrDefault(out.getKey(), 0.0) + byproductGenerated);
                        // Rysujemy linię od głównej maszyny do surowca ubocznego
                        addEdge(result.edges, item, out.getKey(), byproductGenerated);
                    }
                }
            }
        }

        // --- Cel końcowy (Wyjścia) ---
        for (Map.Entry<String, Double> entry : demands.entrySet()) {
            String id = "out_" + entry.getKey();
            result.nodes.put(id, new NodeData(id, "WYJŚCIE (CEL)\n" + getName(entry.getKey()) + "\n(" + entry.getValue() + "/min)", "cds", "#b3e5fc"));
            addEdge(result.edges, entry.getKey(), id, entry.getValue());
        }

        // --- Nadwyżki + Sinki na Zablokowanie Płynów ---
        Map<String, Double> finalSinks = new HashMap<>(globalSurplus);
        // Resztki z byproducts, które nie zostały wykorzystane też muszą pójść do Sinka/Magazynu
        for (Map.Entry<String, Double> b : byproductsPool.entrySet()) {
            finalSinks.put(b.getKey(), finalSinks.getOrDefault(b.getKey(), 0.0) + b.getValue());
        }
        
        for (Map.Entry<String, Double> entry : finalSinks.entrySet()) {
            if (entry.getValue() > 0.001) {
                String id = "sink_" + entry.getKey();
                result.nodes.put(id, new NodeData(id, "ZLEWNIA / NADWYŻKA\n" + getName(entry.getKey()) + "\n(" + String.format(Locale.US, "%.1f", entry.getValue()) + "/min)", "box3d", "#ffcdd2"));
                addEdge(result.edges, entry.getKey(), id, entry.getValue());
            }
        }

        // --- Węzły fabryk ---
        for (Map.Entry<String, Double> entry : totalItems.entrySet()) {
            String item = entry.getKey();
            Recipe recipe = recipesDB.get(item);

            if (exactMachinesMap.containsKey(item) && recipe != null) {
                double newMachines = exactMachinesMap.get(item);
                double full = Math.floor(newMachines);
                double frac = newMachines - full;
                
                String machineType = recipe.machine;
                double baseMw = recipe.power;
                
                List<String> lines = new ArrayList<>();
                lines.add("--- Linia: " + getName(item) + " ---");
                double linePower = 0.0;
                
                if (frac > 0.001 && frac <= 0.25 && full >= 1.0) {
                    double actualFull = full - 1.0;
                    double overclockDec = 1.0 + frac;
                    if (actualFull > 0) {
                        lines.add((int)actualFull + "x " + machineType + " @ 100%");
                        linePower += actualFull * baseMw;
                        result.totalBuildings += (int) actualFull;
                    }
                    lines.add(String.format(Locale.US, "1x %s @ %.2f%%", machineType, overclockDec * 100));
                    linePower += baseMw * Math.pow(overclockDec, 1.321928);
                    result.totalBuildings += 1;
                } else {
                    if (full > 0) {
                        lines.add((int)full + "x " + machineType + " @ 100%");
                        linePower += full * baseMw;
                        result.totalBuildings += (int) full;
                    }
                    if (frac > 0.001) {
                        lines.add(String.format(Locale.US, "1x %s @ %.2f%%", machineType, frac * 100));
                        linePower += baseMw * Math.pow(frac, 1.321928);
                        result.totalBuildings += 1;
                    }
                }
                
                result.totalPowerMW += linePower;
                if (linePower > 0) lines.add(String.format(Locale.US, "⚡ %.1f MW", linePower));
                
                double ext = usedExternal.getOrDefault(item, 0.0);
                if (ext > 0) lines.add("(+ Z zewnątrz: " + ext + "/min)");
                
                if (recipe.inputs == null || recipe.inputs.isEmpty()) {
                    double actualMined = newMachines * recipe.outputs.get(item);
                    result.ingredients.put(item, result.ingredients.getOrDefault(item, 0.0) + actualMined);
                    result.nodes.put(item, new NodeData(item, "KOPALNIA/POMPA\n" + String.join("\n", lines), "folder", "#e0e0e0"));
                } else {
                    result.nodes.put(item, new NodeData(item, String.join("\n", lines), "box", "#ffe0b2"));
                }

                // Śledzenie czy użyliśmy receptury alternatywnej (obsługuje _alt, _alt2, _alt3)
                if (recipe.id != null && recipe.id.matches(".*_alt\\d*$")) {
                    result.usedAltRecipes.add(recipe.id);
                }
            }
        }
        
        // --- Węzły zewnętrznego zasilania ---
        for (Map.Entry<String, Double> ext : usedExternal.entrySet()) {
            result.ingredients.put(ext.getKey(), result.ingredients.getOrDefault(ext.getKey(), 0.0) + ext.getValue());
            if (ext.getValue() > 0) {
                String id = "ext_" + ext.getKey();
                result.nodes.put(id, new NodeData(id, "DOCIĄGNIĘTO\n" + getName(ext.getKey()), "folder", "#c8e6c9"));
            }
        }
        
        // --- ZŁĄCZA (Byproducts) ---
        // Generujemy "węzły-przesiadki" w fioletowych kółkach dla przedmiotów, 
        // które pochodzą WYŁĄCZNIE z byproducts i nie potrzebowały własnych budynków.
        Set<String> neededNodes = new HashSet<>();
        for (Edge e : result.edges) {
            neededNodes.add(e.from);
            neededNodes.add(e.to);
        }
        for (String node : neededNodes) {
            if (!result.nodes.containsKey(node)) {
                if (node.startsWith("ext_") || node.startsWith("out_") || node.startsWith("sink_") || node.equals("power_summary")) continue;
                
                result.nodes.put(node, new NodeData(node, "PRODUKT UBOCZNY\n" + getName(node), "ellipse", "#e1bee7"));
            }
        }
        
        result.nodes.put("power_summary", new NodeData("power_summary", "ZASILANIE CAŁKOWITE\n⚡ " + String.format(Locale.US, "%.1f", result.totalPowerMW) + " MW", "note", "#fff59d"));

        return result;
    }
}