package main;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ErrorMessage {
    public static void showMessage(String message, Stage parent) {
        Label label = new Label(message);
        label.setTextAlignment(TextAlignment.CENTER);
        Button button = new Button();
        button.setText("Ok");
        button.setOnAction(event -> {
            Stage stage = (Stage) button.getScene().getWindow();
            stage.close();
        });
        Stage stage = new Stage();
        VBox vBox = new VBox(label, button);
        vBox.setSpacing(16);
        vBox.setPadding(new Insets(32, 64, 16, 64));
        vBox.setStyle("-fx-border-color: black;" + "-fx-background-color: GhostWhite;");

        vBox.setAlignment(Pos.CENTER);
        Scene scene = new Scene(vBox);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setX(parent.getX() + 50);
        stage.setY(parent.getY() + 50);
        stage.show();
        stage.sizeToScene();
    }
}
