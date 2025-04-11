// test and set-up instructions:
// https://openjfx.io/openjfx-docs/#install-javafx:~:text=Non%2Dmodular%20projects-,IDE,-Follow%20these%20steps

// modified from
// https://www.tutorialspoint.com/sqlite/sqlite_java.htm
// https://examples.javacodegeeks.com/java-development/desktop-java/javafx/javafx-media-api/

package test;

import java.sql.*;

import java.net.URL;
import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaPlayer.Status;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Get Java and JavaFX version information
        String javaVersion = System.getProperty("java.version");
        String javafxVersion = System.getProperty("javafx.version");
        Label versionLabel = new Label("Java Version: " + javaVersion + " | JavaFX Version: " + javafxVersion);

        // Locate the media content in the CLASSPATH
        URL mediaUrl = getClass().getResource("test.mp3");
        System.out.println(mediaUrl);
        String mediaStringUrl = mediaUrl.toExternalForm();

        // Create a Media
        Media media = new Media(mediaStringUrl);

        // Create a Media Player
        final MediaPlayer player = new MediaPlayer(media);
        // Automatically begin the playback
        player.setAutoPlay(true);

        // Create a 400X300 MediaView
        MediaView mediaView = new MediaView(player);
        mediaView.setFitWidth(400);
        mediaView.setFitHeight(300);
        mediaView.setSmooth(true);

        // Create the DropShadow effect
        DropShadow dropshadow = new DropShadow();
        dropshadow.setOffsetY(5.0);
        dropshadow.setOffsetX(5.0);
        dropshadow.setColor(Color.WHITE);

        mediaView.setEffect(dropshadow);

        // Create the Buttons
        Button playButton = new Button("Play");
        Button stopButton = new Button("Stop");

        // Create the Event Handlers for the Button
        playButton.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent event) {
                if (player.getStatus() == Status.PLAYING) {
                    player.stop();
                    player.play();
                } else {
                    player.play();
                }
            }
        });

        stopButton.setOnAction(new EventHandler<ActionEvent>() {
            public void handle(ActionEvent event) {
                player.stop();
            }
        });

        // Create the HBox
        HBox controlBox = new HBox(5, playButton, stopButton);

        // Create the VBox
        VBox root = new VBox(5, versionLabel, mediaView, controlBox);

        // Set the Style-properties of the VBox
        root.setStyle("-fx-padding: 10;" +
                "-fx-border-style: solid inside;" +
                "-fx-border-width: 2;" +
                "-fx-border-insets: 5;" +
                "-fx-border-radius: 5;" +
                "-fx-border-color: blue;");

        // Create the Scene
        Scene scene = new Scene(root);
        // Add the scene to the Stage
        stage.setScene(scene);
        // Set the title of the Stage
        stage.setTitle("A simple Media Example");
        // Display the Stage
        stage.show();
    }

    public static void main(String[] args) {
        // SQLite database connection and operations
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:test.db")) {
            Class.forName("org.sqlite.JDBC");
            c.setAutoCommit(false);

            // Create the COMPANY table
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS COMPANY (
                    ID INT PRIMARY KEY NOT NULL,
                    NAME TEXT NOT NULL,
                    AGE INT NOT NULL,
                    ADDRESS CHAR(50),
                    SALARY REAL
                );
            """;
            try (Statement stmt = c.createStatement()) {
                stmt.executeUpdate(createTableSQL);
            }

            // Insert sample data
            String[] insertSQLs = {
                "INSERT OR IGNORE INTO COMPANY (ID, NAME, AGE, ADDRESS, SALARY) VALUES (1, 'Paul', 32, 'California', 20000.00);",
                "INSERT OR IGNORE INTO COMPANY (ID, NAME, AGE, ADDRESS, SALARY) VALUES (2, 'Allen', 25, 'Texas', 15000.00);",
                "INSERT OR IGNORE INTO COMPANY (ID, NAME, AGE, ADDRESS, SALARY) VALUES (3, 'Teddy', 23, 'Norway', 20000.00);",
                "INSERT OR IGNORE INTO COMPANY (ID, NAME, AGE, ADDRESS, SALARY) VALUES (4, 'Mark', 25, 'Rich-Mond', 65000.00);"
            };
            try (Statement stmt = c.createStatement()) {
                for (String sql : insertSQLs) {
                    stmt.executeUpdate(sql);
                }
            }

            // Query and display the data
            String selectSQL = "SELECT * FROM COMPANY;";
            try (Statement stmt = c.createStatement();
                 ResultSet rs = stmt.executeQuery(selectSQL)) {

                while (rs.next()) {
                    int id = rs.getInt("ID");
                    String name = rs.getString("NAME");
                    int age = rs.getInt("AGE");
                    String address = rs.getString("ADDRESS");
                    float salary = rs.getFloat("SALARY");

                    System.out.printf("ID = %d%nNAME = %s%nAGE = %d%nADDRESS = %s%nSALARY = %.2f%n%n",
                            id, name, age, address, salary);
                }
            }

            c.commit();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        // Launch the JavaFX application
        launch(args);
    }
}