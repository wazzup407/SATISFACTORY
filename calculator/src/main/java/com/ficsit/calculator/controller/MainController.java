package com.ficsit.calculator.controller;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.model.MutableGraph;
import guru.nidi.graphviz.parse.Parser;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
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
    @FXML private ComboBox<String> demandsCombo, providedCombo, surplusCombo, maxItemCombo;
    @FXML private TextField demandsQty, providedQty;
    @FXML private Label statusLabel;
    @FXML private ImageView graphImageView;
    @FXML private CheckBox maxModeCheckbox;
    @FXML private CheckBox roundMachinesCheckbox;

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

    public static class VariantOption {
        public String variantName;
        public double power;
        public int buildings;
        public String inputsStr;
        public double totalInputsSum;
        public Map<String, Recipe> db;
        public CalculationResult result;

        public VariantOption(String variantName, double power, int buildings, String inputsStr, double totalInputsSum, Map<String, Recipe> db, CalculationResult result) {
            this.variantName = variantName;
            this.power = power;
            this.buildings = buildings;
            this.inputsStr = inputsStr;
            this.totalInputsSum = totalInputsSum;
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
            if (!key.matches(".*_alt\\d*$")) { 
                items.add(engine.getName(key) + " (" + key + ")");
            }
        }
        Collections.sort(items);

        ObservableList<String> options = FXCollections.observableArrayList(items);
        demandsCombo.setItems(options);
        providedCombo.setItems(options);
        surplusCombo.setItems(options);
        maxItemCombo.setItems(options); // Lista uzupełniona dla trybu dopychania
        
        setupTable();
        statusLabel.setText("System FICSIT gotowy. Wybierz przedmioty z list.");
    }
    
    @FXML
    private void toggleMaxMode() {
        // Blokowanie/Odblokowywanie rozwijanej listy wyboru
        maxItemCombo.setDisable(!maxModeCheckbox.isSelected());
    }
    
    private void setupTable() {
        colVariant.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().variantName));
        
        colPower.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().power));
        colPower.setCellFactory(param -> new TableCell<VariantOption, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format(Locale.US, "%.1f", item));
            }
        });

        colBuildings.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().buildings));
        
        colInputs.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        colInputs.setCellFactory(param -> new TableCell<VariantOption, VariantOption>() {
            @Override
            protected void updateItem(VariantOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.inputsStr);
            }
        });
        colInputs.setComparator((v1, v2) -> Double.compare(v1.totalInputsSum, v2.totalInputsSum));

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

    @FXML private void addDemand() { processInput(demandsCombo, demandsQty, demandsMap, demandsList); }
    @FXML private void addProvided() { processInput(providedCombo, providedQty, providedMap, providedList); }
    
    private void processInput(ComboBox<String> combo, TextField qtyField, Map<String, Double> map, ListView<String> list) {
        String selection = combo.getValue();
        String qtyStr = qtyField.getText().replace(",", "."); 
        if (selection != null && !qtyStr.isEmpty()) {
            try {
                double qty = Double.parseDouble(qtyStr);
                String key = extractKey(selection);
                map.put(key, map.getOrDefault(key, 0.0) + qty);
                updateListView(list, map);
                qtyField.clear();
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
        if (demandsMap.isEmpty() && (!maxModeCheckbox.isSelected() || maxItemCombo.getValue() == null)) {
            statusLabel.setText("BŁĄD: Wybierz i dodaj (+) przynajmniej jeden produkt lub włącz tryb dopychania!");
            return;
        }

        boolean isMaxMode = maxModeCheckbox != null && maxModeCheckbox.isSelected();
        boolean doRounding = roundMachinesCheckbox != null && roundMachinesCheckbox.isSelected();
        String maxSelectedItemText = isMaxMode ? maxItemCombo.getValue() : null;
        String maxItem = null;

        if (isMaxMode) {
            if (maxSelectedItemText == null) {
                statusLabel.setText("BŁĄD: Wybierz produkt z listy poniżej 'Dopchnij pod korek'!");
                return;
            }
            maxItem = extractKey(maxSelectedItemText);
            if (providedMap.isEmpty()) {
                statusLabel.setText("BŁĄD: Podaj limity (np. ropa 600) w sekcji zasilania, aby wyznaczyć sufit!");
                return;
            }
        }
        
        engine.allowRounding = doRounding && !isMaxMode;

        try {
            statusLabel.setText("Obliczanie optymalnych linii produkcyjnych...");
            
            engine.loadDatabase("recipes.json");
            Map<String, Recipe> pristineDB = new HashMap<>(engine.recipesDB);
            
            Map<String, List<String>> baseToVariants = new HashMap<>();
            for (String key : pristineDB.keySet()) {
                String baseKey = key.replaceAll("_alt\\d*$", "");
                baseToVariants.putIfAbsent(baseKey, new ArrayList<>());
                baseToVariants.get(baseKey).add(key);
            }
            
            List<String> itemsWithAlts = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : baseToVariants.entrySet()) {
                if (entry.getValue().size() > 1) {
                    itemsWithAlts.add(entry.getKey());
                }
            }
            
            List<Map<String, Recipe>> allPermutations = new ArrayList<>();
            generatePermutations(itemsWithAlts, 0, pristineDB, baseToVariants, allPermutations);
            
            Set<Set<String>> seenAltSets = new HashSet<>();
            ObservableList<VariantOption> tableData = FXCollections.observableArrayList();
            
            for (Map<String, Recipe> dbVariant : allPermutations) {
                engine.recipesDB = dbVariant; 
                
                Map<String, Double> activeDemands = new HashMap<>(demandsMap);
                Map<String, Double> activeProvided = new HashMap<>(providedMap);
                boolean foundLimit = false;

                // --- NOWY SYSTEM DOPYCHANIA POJEDYNCZEGO PRODUKTU ---
                if (isMaxMode && maxItem != null) {
                    // 1. Liczymy "żelazną porcję" wziętą z panelu (np. sam proch)
                    CalculationResult baseRes = engine.calculateFactory(activeDemands, new HashMap<>(), surplusSet);
                    
                    // 2. Liczymy "koszt jednostkowy" wybranego surowca (np. 1 Turbopaliwo = x Ropy)
                    Map<String, Double> singleDemand = new HashMap<>();
                    singleDemand.put(maxItem, 1.0);
                    CalculationResult singleRes = engine.calculateFactory(singleDemand, new HashMap<>(), surplusSet);
                    
                    double minAdded = Double.MAX_VALUE;
                    
                    // 3. Sprawdzamy każdy limit i szukamy na ile nowych sztuk nam starczy
                    for (Map.Entry<String, Double> limit : providedMap.entrySet()) {
                        String inputKey = limit.getKey();
                        double limitVal = limit.getValue();
                        
                        double usedCurrently = baseRes.ingredients.getOrDefault(inputKey, 0.0);
                        double remaining = limitVal - usedCurrently; 
                        
                        double costPerUnit = singleRes.ingredients.getOrDefault(inputKey, 0.0);
                        
                        // Zabezpieczenie przed dzieleniem przez 0
                        if (costPerUnit > 0.0001) {
                            double addedUnits = remaining / costPerUnit; // Może być ujemne, co przytnie zamówienie
                            if (addedUnits < minAdded) {
                                minAdded = addedUnits;
                            }
                            foundLimit = true;
                        }
                    }
                    
                    // 4. Aktualizujemy "listę życzeń" fabryki
                    if (foundLimit && minAdded != Double.MAX_VALUE) {
                        double currentVal = activeDemands.getOrDefault(maxItem, 0.0);
                        double newVal = currentVal + minAdded;
                        if (newVal < 0.001) newVal = 0.0; // Nie możemy produkować poniżej 0
                        activeDemands.put(maxItem, newVal);
                    }
                }

                CalculationResult res = engine.calculateFactory(activeDemands, activeProvided, surplusSet);
                
                if (!seenAltSets.contains(res.usedAltRecipes)) {
                    seenAltSets.add(res.usedAltRecipes);
                    
                    String altStr = res.usedAltRecipes.isEmpty() ? "Standardowa" : 
                                   "Alt: " + res.usedAltRecipes.stream()
                                                .map(engine::getName)
                                                .collect(Collectors.joining(", "));
                    
                    String vName = altStr;
                    // Formatowanie, żeby na liście dumnie prezentował wyciśnięty wynik!
                    if (isMaxMode && maxItem != null && foundLimit) {
                        double finalQty = activeDemands.getOrDefault(maxItem, 0.0);
                        vName = String.format(Locale.US, "[DOPCHNIĘTO: %.1fx %s]\n%s", finalQty, engine.getName(maxItem), altStr);
                    }
                                                
                    double sumInputs = 0.0;
                    for (Double val : res.ingredients.values()) {
                        sumInputs += val;
                    }
                    
                    String inputsStr = formatExternalInputs(res.ingredients);
                    tableData.add(new VariantOption(vName, res.totalPowerMW, res.totalBuildings, inputsStr, sumInputs, dbVariant, res));
                }
            }
            
            tableData.sort(Comparator.comparingDouble(v -> v.power));
            variantsTable.setItems(tableData);
            
            statusLabel.setText(isMaxMode ? "Gotowe! Wyciśnięto ile się dało z limitów." : "Odkryto " + tableData.size() + " unikalnych schematów.");
            
            if (!tableData.isEmpty()) {
                drawGraphForVariant(tableData.get(0));
            }

        } catch (Exception e) {
            statusLabel.setText("Błąd krytyczny podczas analizy!");
            e.printStackTrace();
        }
    }
    
    private void generatePermutations(List<String> itemsWithAlts, int index, Map<String, Recipe> currentDB, Map<String, List<String>> baseToVariants, List<Map<String, Recipe>> results) {
        if (index >= itemsWithAlts.size()) {
            Map<String, Recipe> clean = new HashMap<>(currentDB);
            clean.keySet().removeIf(k -> k.matches(".*_alt\\d*$"));
            results.add(clean);
            return;
        }
        
        String baseItem = itemsWithAlts.get(index);
        List<String> variants = baseToVariants.get(baseItem); 
        
        for (String variantKey : variants) {
            Map<String, Recipe> branchDB = new HashMap<>(currentDB);
            branchDB.put(baseItem, branchDB.get(variantKey));
            
            generatePermutations(itemsWithAlts, index + 1, branchDB, baseToVariants, results);
        }
    }
    
    private String formatExternalInputs(Map<String, Double> ext) {
        if (ext.isEmpty()) return "Brak (Samowystarczalne)";
        List<String> parts = new ArrayList<>();
        
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(ext.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue())); 
        
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
            statusLabel.setText("Rysowanie schematu...");
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
        dot.append("rankdir=LR; splines=polyline; nodesep=0.6; ranksep=1.2;\n");
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
            stage.setTitle("FICSIT INC. - Nowy Terminal");
            stage.setScene(new javafx.scene.Scene(root, 1200, 800));
            stage.show();
        } catch (Exception e) {
            statusLabel.setText("Błąd krytyczny przy otwieraniu nowej instancji!");
            e.printStackTrace();
        }
    }
}