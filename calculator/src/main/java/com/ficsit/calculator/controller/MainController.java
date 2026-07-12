package com.ficsit.calculator.controller;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;
import javafx.beans.property.SimpleStringProperty;
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
import java.util.stream.Collectors;

public class MainController {

    @FXML private ListView<String> demandsList, providedList, surplusList;
    @FXML private ComboBox<String> demandsCombo, providedCombo, surplusCombo;
    @FXML private TextField demandsQty, providedQty;
    @FXML private Label statusLabel;
    @FXML private ImageView graphImageView;

    // Elementy tabeli wyników
    // Zmienione typy generyczne z String na Double, Integer i VariantOption
    @FXML private TableView<VariantOption> variantsTable;
    @FXML private TableColumn<VariantOption, String> colVariant;
    @FXML private TableColumn<VariantOption, Double> colPower;
    @FXML private TableColumn<VariantOption, Integer> colBuildings;
    @FXML private TableColumn<VariantOption, VariantOption> colInputs;
    @FXML private TableColumn<VariantOption, Void> colAction;

    private FactoryEngine engine;
    private byte[] currentGraphImageBytes = null;

    private Map<String, Double> demandsMap = new HashMap<>();
    private Map<String, Double> providedMap = new HashMap<>();
    private Set<String> surplusSet = new HashSet<>();

    // Klasa reprezentująca zbiór danych dla wybranego wariantu technologicznego
    public static class VariantOption {
        public String variantName;
        public double power;
        public int buildings;
        public String inputsStr;
        public double totalInputsSum; // NOWE: Suma całkowita do sortowania
        public Map<String, Recipe> db;
        public CalculationResult result;

        public VariantOption(String variantName, double power, int buildings, String inputsStr, double totalInputsSum, Map<String, Recipe> db, CalculationResult result) {
            this.variantName = variantName;
            this.power = power;
            this.buildings = buildings;
            this.inputsStr = inputsStr;
            this.totalInputsSum = totalInputsSum; // Przypisanie
            this.db = db;
            this.result = result;
        }
    }
    @FXML
    public void initialize() {
        engine = new FactoryEngine();
        engine.loadDatabase("recipes.json");

        List<String> items = new ArrayList<>();
        for (String key : engine.recipesDB.keySet()) {
            if (!key.endsWith("_alt")) { // Do list wybieramy tylko główne nazwy
                items.add(engine.getName(key) + " (" + key + ")");
            }
        }
        Collections.sort(items);

        ObservableList<String> options = FXCollections.observableArrayList(items);
        demandsCombo.setItems(options);
        providedCombo.setItems(options);
        surplusCombo.setItems(options);
        
        setupTable();

        statusLabel.setText("System FICSIT gotowy. Wybierz przedmioty z list.");
    }
    
    private void setupTable() {
        // Nazwa
        colVariant.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().variantName));
        
        // Moc - Zwracamy Double, a formatujemy jako String w wizualnej komórce
        colPower.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().power));
        colPower.setCellFactory(param -> new TableCell<VariantOption, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format(Locale.US, "%.1f", item));
            }
        });

        // Budynki - Zwracamy Integer, natywny tekst JavaFX wystarczy
        colBuildings.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().buildings));
        
        // Surowce - Zwracamy CAŁY obiekt wariantu, by mieć dostęp i do Stringa i do sumy.
        colInputs.setCellValueFactory(data -> new javafx.beans.property.ReadOnlyObjectWrapper<>(data.getValue()));
        colInputs.setCellFactory(param -> new TableCell<VariantOption, VariantOption>() {
            @Override
            protected void updateItem(VariantOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.inputsStr); // Pokazujemy ładny tekst
            }
        });
        
        // Tłumaczymy JavaFX jak ma matematycznie porównywać ten obiekt
        colInputs.setComparator((v1, v2) -> Double.compare(v1.totalInputsSum, v2.totalInputsSum));

        // Przycisk akcji
        colAction.setCellFactory(param -> new TableCell<VariantOption, Void>() {
            private final Button btn = new Button("Wybierz");
            {
                btn.setOnAction(event -> {
                    VariantOption opt = getTableView().getItems().get(getIndex());
                    drawGraphForVariant(opt);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });
    }

    @FXML
    private void addDemand() {
        String selection = demandsCombo.getValue();
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
            for (String k : surplusSet) { list.add(engine.getName(k)); }
            surplusList.setItems(list);
        }
    }

    @FXML private void clearDemands() { demandsMap.clear(); demandsList.getItems().clear(); }
    @FXML private void clearProvided() { providedMap.clear(); providedList.getItems().clear(); }
    @FXML private void clearSurplus() { surplusSet.clear(); surplusList.getItems().clear(); }

    private String extractKey(String comboText) {
        return comboText.substring(comboText.lastIndexOf("(") + 1, comboText.length() - 1);
    }

    private void updateListView(ListView<String> listView, Map<String, Double> map) {
        ObservableList<String> list = FXCollections.observableArrayList();
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            list.add(engine.getName(entry.getKey()) + " : " + entry.getValue() + " /min");
        }
        listView.setItems(list);
    }

    @FXML
    private void handleGenerate() {
        if (demandsMap.isEmpty()) {
            statusLabel.setText("BŁĄD: Wybierz i dodaj (+) przynajmniej jeden produkt!");
            return;
        }

        try {
            statusLabel.setText("Generowanie kombinacji procesów...");
            
            // 1. Zawsze startujemy ze "świeżej" oryginalnej bazy z pliku
            engine.loadDatabase("recipes.json");
            Map<String, Recipe> pristineDB = new HashMap<>(engine.recipesDB);
            
            // 2. Znajdź wszystkie klucze, dla których istnieje alternatywa
            List<String> itemsWithAlts = new ArrayList<>();
            for (String key : pristineDB.keySet()) {
                if (key.endsWith("_alt")) {
                    itemsWithAlts.add(key.replace("_alt", ""));
                }
            }
            
            // 3. Stwórz wszystkie możliwe permutacje baz z recepturami
            List<Map<String, Recipe>> allPermutations = new ArrayList<>();
            generatePermutations(itemsWithAlts, 0, pristineDB, allPermutations);
            
            // 4. Testuj bazę. "Set" odrzuca puste ścieżki i powtórki użytych alternatyw!
            Set<Set<String>> seenAltSets = new HashSet<>();
            ObservableList<VariantOption> tableData = FXCollections.observableArrayList();
            
            for (Map<String, Recipe> dbVariant : allPermutations) {
                engine.recipesDB = dbVariant; 
                CalculationResult res = engine.calculateFactory(demandsMap, providedMap, surplusSet);
                
                // Zapisujemy unikalny wariant tylko, jeśli wnosi nowe przepisy dla naszej fabryki
                if (!seenAltSets.contains(res.usedAltRecipes)) {
                    seenAltSets.add(res.usedAltRecipes);
                    
                    String vName = res.usedAltRecipes.isEmpty() ? "Standardowa" : 
                                   "Alt: " + res.usedAltRecipes.stream()
                                                .map(engine::getName)
                                                .collect(Collectors.joining(", "));
                                                
                    // NOWE: Szybkie podliczenie faktycznej sumy sztuk wszystkich surowców wejściowych
                    double sumInputs = 0.0;
                    for (Double val : res.ingredients.values()) {
                        sumInputs += val;
                    }
                    
                    String inputsStr = formatExternalInputs(res.ingredients);
                    
                    // Dodajemy 'sumInputs' do wywołania konstruktora wariantu
                    tableData.add(new VariantOption(vName, res.totalPowerMW, res.totalBuildings, inputsStr, sumInputs, dbVariant, res));
                }
            }
            
            // Sortowanie (Najpierw standard, potem wg zużycia energii)
            tableData.sort(Comparator.comparingDouble(v -> v.power));
            variantsTable.setItems(tableData);
            
            statusLabel.setText("Odkryto " + tableData.size() + " unikalnych linii dla tych żądań.");
            
            // Od razu narysuj pierwszy (domyślny) wykres
            if (!tableData.isEmpty()) {
                drawGraphForVariant(tableData.get(0));
            }

        } catch (Exception e) {
            statusLabel.setText("Błąd krytyczny podczas analizy!");
            e.printStackTrace();
        }
    }
    
    private void generatePermutations(List<String> itemsWithAlts, int index, Map<String, Recipe> currentDB, List<Map<String, Recipe>> results) {
        if (index >= itemsWithAlts.size()) {
            // Czyścimy zbędne znaczniki _alt (graf na nich nie pracuje bez podmiany)
            Map<String, Recipe> clean = new HashMap<>(currentDB);
            clean.keySet().removeIf(k -> k.endsWith("_alt"));
            results.add(clean);
            return;
        }
        
        String baseItem = itemsWithAlts.get(index);
        String altItem = baseItem + "_alt";
        
        // Ścieżka 1: Zostawiamy przepis standardowy
        generatePermutations(itemsWithAlts, index + 1, currentDB, results);
        
        // Ścieżka 2: Podmieniamy główny przepis na jego alternatywę
        if (currentDB.containsKey(altItem)) {
            Map<String, Recipe> branchDB = new HashMap<>(currentDB);
            branchDB.put(baseItem, branchDB.get(altItem));
            generatePermutations(itemsWithAlts, index + 1, branchDB, results);
        }
    }
    
    private String formatExternalInputs(Map<String, Double> ext) {
        if (ext.isEmpty()) return "Brak (Samowystarczalne)";
        List<String> parts = new ArrayList<>();
        
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(ext.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); // Sortuj po największych
        
        for (Map.Entry<String, Double> e : sorted) {
            if (e.getValue() > 0.001) {
                double val = e.getValue();
                String valStr = (val % 1 == 0) ? String.format(Locale.US, "%.0f", val) : String.format(Locale.US, "%.1f", val);
                parts.add(valStr + "x " + engine.getName(e.getKey()));
            }
        }
        return String.join(", ", parts);
    }

    private void drawGraphForVariant(VariantOption opt) {
        engine.recipesDB = opt.db;
        try {
            statusLabel.setText("Rysowanie schematu dla: " + opt.variantName);
            String dotString = buildDotString(opt.result);
            MutableGraph g = new Parser().read(dotString);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            Graphviz.fromGraph(g).render(Format.PNG).toOutputStream(os);

            currentGraphImageBytes = os.toByteArray(); 
            ByteArrayInputStream is = new ByteArrayInputStream(currentGraphImageBytes);
            graphImageView.setImage(new Image(is));

        } catch (Exception e) {
            statusLabel.setText("Błąd przy renderowaniu grafu Graphviz!");
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

    @FXML
    private void openNewWindow() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(getClass().getResource("/view/MainView.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("FICSIT INC. - Nowy Terminal (Niezależny)");
            stage.setScene(new javafx.scene.Scene(root, 1200, 800));
            stage.show();
        } catch (Exception e) {
            statusLabel.setText("Błąd krytyczny przy otwieraniu nowej instancji!");
            e.printStackTrace();
        }
    }
}