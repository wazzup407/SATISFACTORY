package com.ficsit.calculator.model;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileReader;
import java.lang.reflect.Type;
import java.util.*;

public class FactoryEngine {

    public Map<String, Recipe> recipesDB;

    // --- KLASY ZWRACANE (WYNIKOWE) ---
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
    }

    // --- ŁADOWANIE BAZY ---
    public void loadDatabase(String filePath) {
        try (FileReader reader = new FileReader(filePath)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Recipe>>(){}.getType();
            recipesDB = gson.fromJson(reader, type);
            System.out.println("[System] Załadowano bazę z JSON.");
        } catch (Exception e) {
            System.err.println("[Błąd] Nie udało się wczytać pliku recipes.json. Upewnij się, że leży w głównym folderze projektu.");
            e.printStackTrace();
        }
    }

    public String getName(String key) {
        switch (key) {
            case "iron_ore": return "Ruda Żelaza";
            case "copper_ore": return "Ruda Miedzi";
            case "limestone": return "Wapień";
            case "coal": return "Węgiel";
            case "crude_oil": return "Ropa Naftowa";
            case "sam_ore": return "Ruda DMO";
            case "raw_quartz": return "Surowy Kwarzec";
            case "iron_ingot": return "Sztabka Żelaza";
            case "copper_ingot": return "Sztabka Miedzi";
            case "steel_ingot": return "Sztabka Stali";
            case "iron_plate": return "Żelazna Płyta";
            case "iron_rod": return "Żelazny Pręt";
            case "wire": return "Drut";
            case "cable": return "Przewód";
            case "concrete": return "Beton";
            case "screw": return "Śruba";
            case "reinforced_plate": return "Wzmocniona Płyta";
            case "rotor": return "Wirnik";
            case "stator": return "Stojan";
            case "motor": return "Silnik";
            case "modular_frame": return "Rama Modułowa";
            case "smart_plating": return "Inteligentna Powłoka";
            case "copper_sheet": return "Blacha Miedziana";
            case "steel_beam": return "Belka Stalowa";
            case "steel_pipe": return "Rura Stalowa";
            case "encased_beam": return "Wzmocniona Belka Przemysłowa";
            case "plastic": return "Plastik";
            case "rubber": return "Guma";
            case "fuel": return "Paliwo";
            case "circuit_board": return "Płytka Drukowana";
            case "computer": return "Komputer";
            case "heavy_frame": return "Ciężka Rama Modułowa";
            case "versatile_framework": return "Wszechstronna Rama";
            case "modular_engine": return "Silnik Modułowy";
            case "adaptive_unit": return "Adaptacyjna Jednostka Sterująca";
            case "automated_wiring": return "Automatyczne Okablowanie";
            case "quartz_crystal": return "Kryształy Krzemu";
            case "reanimated_sam": return "Ożywione DMO";
            case "crystal_oscillator": return "Oscylatory";
            case "sam_fluctuator": return "Fluktuator DMO";
            default: return key;
        }
    }

    // --- SORTOWANIE TOPOLOGICZNE ---
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
        visiting.add(item);
        Recipe r = recipesDB.get(item);
        if (r != null && r.inputs != null) {
            for (String inp : r.inputs.keySet()) {
                dfs(inp, visited, visiting, order);
            }
        }
        visiting.remove(item);
        visited.add(item);
        order.add(item);
    }

    // --- GŁÓWNY SILNIK OBLICZENIOWY ---
    public CalculationResult calculateFactory(Map<String, Double> demands, Map<String, Double> providedInputs, Set<String> wantedSurplus) {
        CalculationResult result = new CalculationResult();
        
        List<String> topOrder = getTopologicalOrder();
        Map<String, Double> totalItems = new HashMap<>();
        Map<String, Double> usedExternal = new HashMap<>();
        Map<String, Double> globalSurplus = new HashMap<>();
        
        Map<String, Double> availableInputs = new HashMap<>(providedInputs);

        for (Map.Entry<String, Double> entry : demands.entrySet()) {
            totalItems.put(entry.getKey(), totalItems.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
        }

        Map<String, Double> exactMachinesMap = new HashMap<>();

        // Faza 1: Propagacja potrzeb i maszyn w dół drzewa
        for (String item : topOrder) {
            double exactAmount = totalItems.getOrDefault(item, 0.0);
            if (exactAmount <= 0) continue;

            Recipe recipe = recipesDB.get(item);
            
            // Pobieranie z zewnątrz
            double extAvailable = availableInputs.getOrDefault(item, 0.0);
            double takeFromExt = Math.min(exactAmount, extAvailable);
            if (takeFromExt > 0) {
                availableInputs.put(item, extAvailable - takeFromExt);
                exactAmount -= takeFromExt;
                usedExternal.put(item, takeFromExt);
                result.edges.add(new Edge("ext_" + item, item, takeFromExt));
            }

            // Zabezpieczenie przed brakami w bazie
            if (recipe == null || recipe.outputs == null || !recipe.outputs.containsKey(item)) continue;
            if (exactAmount <= 0) continue; 

            // Wyciąganie wartości z nowej struktury słownika
            double outQty = recipe.outputs.get(item);
            
            double exactMachines = exactAmount / outQty;
            double full = Math.floor(exactMachines);
            double frac = exactMachines - full;
            double newMachines = exactMachines;

            boolean isFinal = demands.containsKey(item);
            boolean isWanted = wantedSurplus.contains(item);

            // LOGIKA MAGAZYNU I NADWYŻEK
            if (isWanted) {
                if (frac > 0.001) newMachines = full + 1;
            } else if (isFinal) {
                if (frac > 0.25 && full >= 1.0) newMachines = full + 1;
            }

            double actualAmount = newMachines * outQty;
            double surplus = actualAmount - exactAmount;
            
            if (surplus > 0.001) globalSurplus.put(item, surplus);
            
            exactMachinesMap.put(item, newMachines);

            // Zgłaszanie zapotrzebowania niżej (do półproduktów)
            if (recipe.inputs != null) {
                double multiplier = actualAmount / outQty;
                for (Map.Entry<String, Double> inp : recipe.inputs.entrySet()) {
                    double needed = inp.getValue() * multiplier;
                    totalItems.put(inp.getKey(), totalItems.getOrDefault(inp.getKey(), 0.0) + needed);
                    
                    // Kumulujemy połączenia, aby się nie dublowały
                    boolean edgeExists = false;
                    for (Edge e : result.edges) {
                        if (e.from.equals(inp.getKey()) && e.to.equals(item)) {
                            e.amount += needed;
                            edgeExists = true;
                            break;
                        }
                    }
                    if (!edgeExists) result.edges.add(new Edge(inp.getKey(), item, needed));
                }
            }
        }

        // Faza 2: Generowanie Węzłów dla GUI i wyliczanie MW
        for (Map.Entry<String, Double> entry : demands.entrySet()) {
            String id = "out_" + entry.getKey();
            result.nodes.put(id, new NodeData(id, "WYJŚCIE (CEL)\n" + getName(entry.getKey()) + "\n(" + entry.getValue() + "/min)", "cds", "#b3e5fc"));
            result.edges.add(new Edge(entry.getKey(), id, entry.getValue()));
        }

        for (Map.Entry<String, Double> entry : globalSurplus.entrySet()) {
            String id = "sink_" + entry.getKey();
            result.nodes.put(id, new NodeData(id, "MAGAZYN / SINK\n" + getName(entry.getKey()) + " Nadwyżka\n(" + String.format(Locale.US, "%.1f", entry.getValue()) + "/min)", "box3d", "#ffcdd2"));
            result.edges.add(new Edge(entry.getKey(), id, entry.getValue()));
        }

        for (Map.Entry<String, Double> entry : totalItems.entrySet()) {
            String item = entry.getKey();
            double amount = entry.getValue();
            if (amount <= 0) continue;
            Recipe recipe = recipesDB.get(item);

            if (exactMachinesMap.containsKey(item) && recipe != null) {
                double newMachines = exactMachinesMap.get(item);
                double full = Math.floor(newMachines);
                double frac = newMachines - full;
                
                String machineType = recipe.machine;
                
                // Moc wczytywana bezpośrednio z JSON!
                double baseMw = recipe.power;
                
                List<String> lines = new ArrayList<>();
                lines.add("--- Linia: " + getName(item) + " ---");
                double linePower = 0.0;
                
                // Reguła Ogonków FICSIT (<25% nadmiaru wizualnego)
                if (frac > 0.001 && frac <= 0.25 && full >= 1.0) {
                    double actualFull = full - 1.0;
                    double overclockDec = 1.0 + frac;
                    if (actualFull > 0) {
                        lines.add((int)actualFull + "x " + machineType + " @ 100%");
                        linePower += actualFull * baseMw;
                    }
                    lines.add(String.format(Locale.US, "1x %s @ %.2f%%", machineType, overclockDec * 100));
                    linePower += baseMw * Math.pow(overclockDec, 1.321928);
                } else {
                    if (full > 0) {
                        lines.add((int)full + "x " + machineType + " @ 100%");
                        linePower += full * baseMw;
                    }
                    if (frac > 0.001) {
                        lines.add(String.format(Locale.US, "1x %s @ %.2f%%", machineType, frac * 100));
                        linePower += baseMw * Math.pow(frac, 1.321928);
                    }
                }
                
                result.totalPowerMW += linePower;
                if (linePower > 0) lines.add(String.format(Locale.US, "⚡ %.1f MW", linePower));
                
                double ext = usedExternal.getOrDefault(item, 0.0);
                if (ext > 0) lines.add("(+ Z zewnątrz: " + ext + "/min)");
                
                // Rozróżnienie wizualne kopalni od fabryk, ale z zachowaniem wyliczeń MW
                if (recipe.inputs == null || recipe.inputs.isEmpty()) {
                    result.nodes.put(item, new NodeData(item, "KOPALNIA/POMPA\n" + String.join("\n", lines), "folder", "#e0e0e0"));
                } else {
                    result.nodes.put(item, new NodeData(item, String.join("\n", lines), "box", "#ffe0b2"));
                }
            }
        }
        
        for (Map.Entry<String, Double> ext : usedExternal.entrySet()) {
            if (ext.getValue() > 0) {
                String id = "ext_" + ext.getKey();
                result.nodes.put(id, new NodeData(id, "DOCIĄGNIĘTO\n" + getName(ext.getKey()), "folder", "#c8e6c9"));
            }
        }
        
        // Globalne podsumowanie MW
        result.nodes.put("power_summary", new NodeData("power_summary", "ZASILANIE CAŁKOWITE\n⚡ " + String.format(Locale.US, "%.1f", result.totalPowerMW) + " MW", "note", "#fff59d"));

        return result;
    }
}