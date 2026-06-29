package com.ficsit.calculator;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Ścieżka do widoku - upewnij się, że MainView.fxml jest w src/main/resources/view/
        Parent root = FXMLLoader.load(getClass().getResource("/view/MainView.fxml"));
        primaryStage.setTitle("Terminal FICSIT");
        primaryStage.setScene(new Scene(root, 1150, 780));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}