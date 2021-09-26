package main;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class StartController {
    private ServerSocket serverSocket;

    private final ArrayList<File> files = new ArrayList<>();
    private boolean transferring;
    private boolean shuttingDown;

    @FXML
    private Button scanButton;

    @FXML
    private AnchorPane dropArea;

    @FXML
    private TextField ipField;

    @FXML
    private Hyperlink clearTextHyperlink;

    @FXML
    private Label transferLabel, scanLabel;

    @FXML
    private ChoiceBox<String> ipChoiceBox;

    public void initialize() {
        Platform.runLater(() -> dropArea.requestFocus());

        final ContextMenu contextMenu = new ContextMenu();
        MenuItem paste = new MenuItem();
        contextMenu.setOnShowing(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            if (clipboard.hasFiles()) {
                paste.setText("Paste file(s)");
                paste.setOnAction(e -> {
                    files.addAll(clipboard.getFiles());
                    sendDetails();
                });
            } else {
                paste.setText("No files copied");
            }
        });

        contextMenu.getItems().add(paste);
        dropArea.setOnContextMenuRequested(event -> contextMenu.show(dropArea, event.getScreenX(), event.getScreenY()));
        dropArea.setOnMouseClicked(event -> {
            if (contextMenu.isShowing()) {
                contextMenu.hide();
            }
        });

        dropArea.setOnDragOver(event -> {
            if (event.getGestureSource() != dropArea
                    && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        dropArea.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = false;
            if (dragboard.hasFiles()) {
                files.addAll(dragboard.getFiles());
                success = true;
            }

            event.setDropCompleted(success);
            if (event.isDropCompleted()) {
                sendDetails();
            }

            event.consume();
        });

        ipChoiceBox.setOnAction(event -> {
            ipField.setText(ipChoiceBox.getSelectionModel().getSelectedItem());
            ipChoiceBox.getSelectionModel().clearSelection();
        });

        shuttingDown = false;
    }

    public void addShutdownListen() {
        dropArea.getScene().getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            shuttingDown = true;
            try {
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void sendDetails() {
        Stage stage = (Stage) dropArea.getScene().getWindow();

        if (transferring) {
            ErrorMessage.showMessage("Already transferring files", stage);
            return;
        }

        if (files.isEmpty()) {
            ErrorMessage.showMessage("No files selected", stage);
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                ErrorMessage.showMessage("One or more selected files is a directory. Cancelling transfer", stage);
                return;
            }
        }

        if (!ipField.getText().isEmpty()) {
            transferring = true;
            transferLabel.setVisible(true);
            transferLabel.setText("Attempting to transfer " + files.size() + " file(s)");
            new Thread(() -> {
                Send send = new Send();
                try {
                    int sendDetails = send.sendDetails(files, ipField.getText());
                    switch (sendDetails) {
                        case Constants.SERVER_NOT_STARTED -> Platform.runLater(() -> ErrorMessage.showMessage("Connection failed: Server not started", stage));
                        case Constants.PERMISSION_DENIED -> Platform.runLater(() -> ErrorMessage.showMessage("Connection failed: Permission denied", stage));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        } else {
            files.clear();
            ErrorMessage.showMessage("Input a host.", (Stage) dropArea.getScene().getWindow());
        }
    }

    public void listenForRequests() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(Constants.SEND_APP_PORT);
                while (!shuttingDown && !serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    DataInputStream dataInputStream = new DataInputStream(socket.getInputStream());

                    int requestType = dataInputStream.readInt();

                    switch (requestType) {
                        case Constants.REQUEST_DOWNLOAD -> {
                            String host = dataInputStream.readUTF();
                            String filePath = dataInputStream.readUTF();
                            long filePosition = dataInputStream.readLong();
                            Send send = new Send();
                            send.send(host, filePath, filePosition);
                        }
                        case Constants.SUCCESSFUL_TRANSFER -> Platform.runLater(() -> {
                            ErrorMessage.showMessage("Successfully transferred " + files.size() + " file(s)", (Stage) dropArea.getScene().getWindow());
                            transferLabel.setVisible(false);
                            transferring = false;
                            files.clear();
                        });
                        case Constants.TRANSFER_FAILED -> {
                            files.clear();
                            String message = dataInputStream.readUTF();
                            Platform.runLater(() -> {
                                ErrorMessage.showMessage(message, (Stage) dropArea.getScene().getWindow());
                                transferLabel.setVisible(false);
                                transferring = false;
                            });
                        }
                    }
                    dataInputStream.close();
                }
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    if (!shuttingDown) {
                        Platform.runLater(() -> {
                            transferLabel.setVisible(true);
                            transferLabel.setText("Lost connection - Attempting reconnect in 10 seconds");
                        });
                        Thread.sleep(10000);
                        Platform.runLater(() -> transferLabel.setVisible(false));
                        listenForRequests();
                    }
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

    @FXML
    private void clearTextArea() {
        ipField.setText("");
        clearTextHyperlink.setVisited(false);
    }

    @FXML
    private void scanNetwork() throws ExecutionException, InterruptedException {
        scanButton.setDisable(true);
        Send send = new Send();
        String ip;
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName(Constants.FIND_OWN_IP_HOST), Constants.FIND_OWN_IP_PORT);
            ip = socket.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            return;
        }

        ExecutorService executor = Executors.newFixedThreadPool(254);
        List<Callable<String>> callables = new ArrayList<>();
        for (int i = 1; i <= 254; i++) {
            String output = ip.substring(0, ip.lastIndexOf(".") + 1) + i;
            Callable<String> callable = () -> send.scan(output);
            callables.add(callable);
        }

        List<Future<String>> futures = executor.invokeAll(callables);
        executor.shutdown();

        for (Future<String> future : futures) {
            String found = future.get();
            if (found.length() > 0) {
                boolean duplicate = false;
                for (String value : ipChoiceBox.getItems()) {
                    if (value.equals(found)) {
                        duplicate = true;
                        break;
                    }
                }
                if (duplicate) {
                    continue;
                }

                ipChoiceBox.getItems().add(found);
            }
        }

        scanLabel.setText("Scanning finished");
        scanLabel.setVisible(true);
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
        exec.schedule(() -> {
            Platform.runLater(() -> scanLabel.setVisible(false));
            scanButton.setDisable(false);
        }, 3, TimeUnit.SECONDS);
        exec.shutdown();
    }
}
