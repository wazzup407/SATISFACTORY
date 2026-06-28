package main.java.com.ficsit.calculator;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Upewnij się, że ścieżka do FXML jest poprawna dla Twojej struktury katalogów!
        Parent root = FXMLLoader.load(getClass().getResource("/view/MainView.fxml"));
        primaryStage.setTitle("Kalkulator Satisfactory");
        primaryStage.setScene(new Scene(root, 1100, 750));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}