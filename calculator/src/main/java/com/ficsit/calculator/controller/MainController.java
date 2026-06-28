package com.ficsit.calculator.controller;

import com.ficsit.calculator.model.FactoryEngine;
import com.ficsit.calculator.model.FactoryEngine.CalculationResult;
import com.ficsit.calculator.model.FactoryEngine.Edge;
import com.ficsit.calculator.model.FactoryEngine.NodeData;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class MainController {

    @FXML private TextArea demandsArea;
    @FXML private TextArea providedArea;
    @FXML private TextField surplusField;
    @FXML private Button generateBtn;
    @FXML private Label statusLabel;
    @FXML private ImageView graphImageView;

    private FactoryEngine engine;

    @FXML
    public void initialize() {
        // Ta metoda uruchamia się automatycznie po załadowaniu okna (FXML)
        engine = new FactoryEngine();
        engine.loadDatabase("recipes.json"); // Upewnij się, że plik leży w głównym folderze projektu
        statusLabel.setText("System FICSIT gotowy. Baza załadowana.");
    }

    @FXML
    private void handleGenerate() {
        try {
            statusLabel.setText("Przetwarzanie danych...");

            // 1. Parsowanie wejść użytkownika
            Map<String, Double> demands = parseArea(demandsArea.getText());
            Map<String, Double> provided = parseArea(providedArea.getText());
            Set<String> surplus = parseSurplus(surplusField.getText());

            if (demands.isEmpty()) {
                statusLabel.setText("BŁĄD: Podaj przynajmniej jeden produkt do wyprodukowania!");
                return;
            }

            // 2. Wywołanie Głównego Silnika z Etapu 2
            CalculationResult result = engine.calculateFactory(demands, provided, surplus);

            // 3. Budowanie kodu DOT dla Graphviza
            String dotString = buildDotString(result);

            // 4. Renderowanie grafu w pamięci i wyświetlenie w JavaFX
            MutableGraph g = new Parser().read(dotString);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Graphviz.fromGraph(g).render(Format.PNG).toOutputStream(os);
            
            ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
            Image fxImage = new Image(is);
            graphImageView.setImage(fxImage);

            statusLabel.setText("Schemat wygenerowany pomyślnie! MW: " + String.format(Locale.US, "%.1f", result.totalPowerMW));

        } catch (Exception e) {
            statusLabel.setText("Wystąpił błąd! Sprawdź składnię wprowadzonych danych.");
            e.printStackTrace();
        }
    }

    // --- METODY POMOCNICZE ---

    private Map<String, Double> parseArea(String text) {
        Map<String, Double> map = new HashMap<>();
        if (text == null || text.trim().isEmpty()) return map;
        
        String[] lines = text.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(":");
            if (parts.length == 2) {
                map.put(parts[0].trim(), Double.parseDouble(parts[1].trim()));
            }
        }
        return map;
    }

    private Set<String> parseSurplus(String text) {
        Set<String> set = new HashSet<>();
        if (text == null || text.trim().isEmpty()) return set;
        
        String[] items = text.split(",");
        for (String item : items) {
            set.add(item.trim());
        }
        return set;
    }

    private String buildDotString(CalculationResult result) {
        StringBuilder dot = new StringBuilder();
        dot.append("digraph G {\n");
        dot.append("rankdir=LR; splines=ortho; nodesep=0.6; ranksep=1.2;\n");
        dot.append("node [style=\"rounded,filled\", fontname=\"Helvetica\", fontsize=10];\n");
        dot.append("edge [fontname=\"Helvetica\", fontsize=9, color=\"#666666\"];\n");

        for (NodeData n : result.nodes.values()) {
            // Zamieniamy w locie znaki nowej linii na składnię Graphviza
            String safeLabel = n.label.replace("\n", "\\n").replace("\"", "\\\"");
            dot.append(String.format("\"%s\" [label=\"%s\", shape=\"%s\", fillcolor=\"%s\"];\n",
                    n.id, safeLabel, n.shape, n.color));
        }

        for (Edge e : result.edges) {
            // Jeśli liczba to np. 15.0, wyświetlamy "15" (bez zera)
            String amountStr = (e.amount % 1 == 0) ? String.format(Locale.US, "%.0f", e.amount) : String.format(Locale.US, "%s", e.amount);
            dot.append(String.format("\"%s\" -> \"%s\" [headlabel=\" %s \", labelfontsize=9, labelfontcolor=\"#111111\", labeldistance=1.5];\n",
                    e.from, e.to, amountStr));
        }

        dot.append("}\n");
        return dot.toString();
    }
}