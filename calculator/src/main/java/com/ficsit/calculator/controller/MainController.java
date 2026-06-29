package com.ficsit.calculator.controller;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import com.ficsit.calculator.model.FactoryEngine;
import com.ficsit.calculator.model.FactoryEngine.CalculationResult;
import com.ficsit.calculator.model.FactoryEngine.Edge;
import com.ficsit.calculator.model.FactoryEngine.NodeData;
import com.ficsit.calculator.model.Recipe;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

public class MainController {

    // Nowe elementy interfejsu (Listy i Przyciski wyboru)
    @FXML private ListView<String> demandsList, providedList, surplusList;
    @FXML private ComboBox<String> demandsCombo, providedCombo, surplusCombo;
    @FXML private TextField demandsQty, providedQty;
    
    @FXML private Label statusLabel;
    @FXML private ImageView graphImageView;

    private FactoryEngine engine;
    private byte[] currentGraphImageBytes = null; // Przechowuje gotowy obrazek do zapisu

    // Wewnętrzne struktury, w których trzymamy dodane pozycje
    private Map<String, Double> demandsMap = new HashMap<>();
    private Map<String, Double> providedMap = new HashMap<>();
    private Set<String> surplusSet = new HashSet<>();

    @FXML
    public void initialize() {
        engine = new FactoryEngine();
        engine.loadDatabase("recipes.json");

        // Tworzenie ładnej listy do rozwijanego menu: "Nazwa (klucz)"
        List<String> items = new ArrayList<>();
        for (String key : engine.recipesDB.keySet()) {
            items.add(engine.getName(key) + " (" + key + ")");
        }
        Collections.sort(items); // Sortowanie alfabetyczne dla wygody

        ObservableList<String> options = FXCollections.observableArrayList(items);
        demandsCombo.setItems(options);
        providedCombo.setItems(options);
        surplusCombo.setItems(options);

        statusLabel.setText("System FICSIT gotowy. Wybierz przedmioty z list.");
    }

    // --- FUNKCJE DODAWANIA DO LIST (Guziki "+") ---

    @FXML
    private void addDemand() {
        String selection = demandsCombo.getValue();
        // Zamiana przecinka na kropkę, żeby program się nie wywalił jak użytkownik wpisze "1,5" zamiast "1.5"
        String qtyStr = demandsQty.getText().replace(",", "."); 
        
        if (selection != null && !qtyStr.isEmpty()) {
            try {
                double qty = Double.parseDouble(qtyStr);
                String key = extractKey(selection);
                demandsMap.put(key, demandsMap.getOrDefault(key, 0.0) + qty);
                updateListView(demandsList, demandsMap);
                demandsQty.clear();
            } catch (NumberFormatException e) {
                statusLabel.setText("BŁĄD: Ilość musi być liczbą!");
            }
        }
    }

    @FXML
    private void addProvided() {
        String selection = providedCombo.getValue();
        String qtyStr = providedQty.getText().replace(",", ".");
        
        if (selection != null && !qtyStr.isEmpty()) {
            try {
                double qty = Double.parseDouble(qtyStr);
                String key = extractKey(selection);
                providedMap.put(key, providedMap.getOrDefault(key, 0.0) + qty);
                updateListView(providedList, providedMap);
                providedQty.clear();
            } catch (NumberFormatException e) {
                statusLabel.setText("BŁĄD: Ilość musi być liczbą!");
            }
        }
    }

    @FXML
    private void addSurplus() {
        String selection = surplusCombo.getValue();
        if (selection != null) {
            String key = extractKey(selection);
            surplusSet.add(key);
            
            ObservableList<String> list = FXCollections.observableArrayList();
            for (String k : surplusSet) {
                list.add(engine.getName(k));
            }
            surplusList.setItems(list);
        }
    }

    // --- CZYSZCZENIE LIST ---
    @FXML private void clearDemands() { demandsMap.clear(); demandsList.getItems().clear(); }
    @FXML private void clearProvided() { providedMap.clear(); providedList.getItems().clear(); }
    @FXML private void clearSurplus() { surplusSet.clear(); surplusList.getItems().clear(); }

    // --- NARZĘDZIA POMOCNICZE ---
    
    // Wydobywa klucz systemowy z ładnej nazwy, np. z "Rotor (rotor)" wyciąga "rotor"
    private String extractKey(String comboText) {
        return comboText.substring(comboText.lastIndexOf("(") + 1, comboText.length() - 1);
    }

    // Odświeża wizualną listę w oknie
    private void updateListView(ListView<String> listView, Map<String, Double> map) {
        ObservableList<String> list = FXCollections.observableArrayList();
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            list.add(engine.getName(entry.getKey()) + " : " + entry.getValue() + " /min");
        }
        listView.setItems(list);
    }

    // --- GŁÓWNE PRZETWARZANIE ---

    @FXML
    private void handleGenerate() {
        if (demandsMap.isEmpty()) {
            statusLabel.setText("BŁĄD: Wybierz i dodaj (+) przynajmniej jeden produkt!");
            return;
        }

        try {
            statusLabel.setText("Optymalizacja łańcucha dostaw...");
            
            // NOWOŚĆ: Skanujemy tylko to, co jest faktycznie potrzebne!
            resolveRelevantAlternatives(demandsMap.keySet());
            
            statusLabel.setText("Analiza topologiczna w toku...");
            
            CalculationResult result = engine.calculateFactory(demandsMap, providedMap, surplusSet);
            String dotString = buildDotString(result);

            MutableGraph g = new Parser().read(dotString);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Graphviz.fromGraph(g).render(Format.PNG).toOutputStream(os);

            currentGraphImageBytes = os.toByteArray(); 
            
            ByteArrayInputStream is = new ByteArrayInputStream(currentGraphImageBytes);
            graphImageView.setImage(new Image(is));

            statusLabel.setText("Sukces! Skonstruowano. Zasilanie: " + String.format(Locale.US, "%.1f", result.totalPowerMW) + " MW");

        } catch (Exception e) {
            statusLabel.setText("Błąd krytyczny podczas renderowania!");
            e.printStackTrace();
        }
    }

    @FXML
    private void saveGraph() {
        if (currentGraphImageBytes == null) {
            statusLabel.setText("Brak grafu! Najpierw wygeneruj schemat.");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Zapisz schemat fabryki FICSIT");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Obraz PNG", "*.png"));
        fileChooser.setInitialFileName("Schemat_Fabryki.png");
        
        // Wywołujemy okienko Windowsa. Używamy getWindow() żeby okienko zapisu zablokowało aplikację pod spodem
        File file = fileChooser.showSaveDialog(demandsList.getScene().getWindow());
        
        if (file != null) {
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(currentGraphImageBytes);
                statusLabel.setText("Zapisano pomyślnie jako: " + file.getName());
            } catch (Exception e) {
                statusLabel.setText("Błąd zapisu pliku na dysku!");
                e.printStackTrace();
            }
        }
    }

    private String buildDotString(CalculationResult result) {
        StringBuilder dot = new StringBuilder();
        dot.append("digraph G {\n");
        dot.append("rankdir=LR; splines=ortho; nodesep=0.6; ranksep=1.2;\n");
        dot.append("node [style=\"rounded,filled\", fontname=\"Helvetica\", fontsize=10];\n");
        dot.append("edge [fontname=\"Helvetica\", fontsize=9, color=\"#666666\"];\n");

        for (NodeData n : result.nodes.values()) {
            String safeLabel = n.label.replace("\n", "\\n").replace("\"", "\\\"");
            dot.append(String.format("\"%s\" [label=\"%s\", shape=\"%s\", fillcolor=\"%s\"];\n",
                    n.id, safeLabel, n.shape, n.color));
        }

        for (Edge e : result.edges) {
            DecimalFormat df = new DecimalFormat("#.###");
            df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));

            String amountStr = df.format(e.amount);
            dot.append(String.format("\"%s\" -> \"%s\" [headlabel=\" %s \", labelfontsize=9, labelfontcolor=\"#111111\", labeldistance=1.5];\n",
                    e.from, e.to, amountStr));
        }

        dot.append("}\n");
        return dot.toString();
    }

    private void resolveRelevantAlternatives(Set<String> startingItems) {
        // Kolejka elementów do sprawdzenia (na start wrzucamy to, co chcemy wyprodukować)
        Queue<String> queue = new LinkedList<>(startingItems);
        Set<String> processed = new HashSet<>();

        while (!queue.isEmpty()) {
            String current = queue.poll();
            
            // Jeśli już to sprawdzaliśmy, pomijamy, żeby nie zapętlić programu
            if (processed.contains(current)) continue;
            processed.add(current);

            String altKey = current + "_alt";
            
            // Jeśli w bazie istnieje alternatywa dla aktualnie analizowanego przedmiotu
            if (engine.recipesDB.containsKey(altKey)) {
                Recipe baseRecipe = engine.recipesDB.get(current);
                Recipe altRecipe = engine.recipesDB.get(altKey);

                String baseName = engine.getName(current);
                String altName = engine.getName(altKey);

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("FICSIT - Wykryto alternatywę");
                alert.setHeaderText("Ścieżka produkcyjna wymaga: " + baseName);
                
                String baseCost = formatInputs(baseRecipe.inputs);
                String altCost = formatInputs(altRecipe.inputs);

                alert.setContentText(
                    "1. Standardowa: " + baseRecipe.machine + "\n" +
                    "   Koszt: " + baseCost + "\n\n" +
                    "2. Alternatywa (" + altName + "): " + altRecipe.machine + "\n" +
                    "   Koszt: " + altCost + "\n\n" +
                    "Wybierz technologię:"
                );

                ButtonType btnBase = new ButtonType("1. Standard");
                ButtonType btnAlt = new ButtonType("2. Alternatywa");
                alert.getButtonTypes().setAll(btnBase, btnAlt);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == btnAlt) {
                    engine.recipesDB.put(current, altRecipe); // Zastępujemy główną recepturę
                }
                
                // Usuwamy wariant alt z bazy, aby przy kolejnych generacjach już nie pytać
                engine.recipesDB.remove(altKey);
            }

            // Dodajemy do kolejki surowce potrzebne do stworzenia tego przedmiotu
            Recipe activeRecipe = engine.recipesDB.get(current);
            if (activeRecipe != null && activeRecipe.inputs != null) {
                queue.addAll(activeRecipe.inputs.keySet());
            }
        }
    }

    private String formatInputs(Map<String, Double> inputs) {
        if (inputs == null || inputs.isEmpty()) return "Brak (Surowiec wejściowy)";
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Double> entry : inputs.entrySet()) {
            // Skracamy ewentualne ".0" dla czytelności (np. 15.0x -> 15x)
            double val = entry.getValue();
            String valStr = (val % 1 == 0) ? String.format(Locale.US, "%.0f", val) : String.valueOf(val);
            parts.add(valStr + "x " + engine.getName(entry.getKey()));
        }
        return String.join(", ", parts);
    }
}