package main;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.*;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class StartController {

    @FXML
    private Label mainLabel, fileNameLabel, fileSizeLabel, progressLabel, speedLabel, downloadedLabel, locationLabel;

    @FXML
    private Button button, locationButton;

    private static String inputLocation = System.getProperty("user.home") + File.separator + "Downloads";

    private ServerSocket server;
    private String currentClient;
    private int downloaded;
    private int received;

    private ArrayList<DownloadingFile> processingFiles;
    private ArrayList<String> errors;
    private boolean shuttingDown;

    public void initialize() {
        locationLabel.setText(inputLocation);
        mainLabel.setText("");
        fileNameLabel.setText("");
        fileSizeLabel.setText("");
        progressLabel.setText("");
        speedLabel.setText("");
        downloadedLabel.setText("");
        button.setVisible(false);
        locationButton.setVisible(true);
        shuttingDown = false;
        downloaded = 0;
        received = 0;
    }

    public void addShutdownListen() {
        mainLabel.getScene().getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            shuttingDown = true;
            System.out.println("Closing");
            try {
                server.close();
                System.out.println("server closed");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public void listen() {
        new Thread(() -> {
            try {
                server = new ServerSocket(Constants.RECEIVE_APP_PORT);
                if (processingFiles != null && processingFiles.size() > 0) {
                    Platform.runLater(() -> mainLabel.setText("Resuming download"));
                    downloadFiles();
                    return;
                }

                Platform.runLater(() -> {
                    locationLabel.setText(inputLocation);
                    mainLabel.setText("Server ready");
                    fileNameLabel.setText("");
                    fileSizeLabel.setText("");
                    progressLabel.setText("");
                    speedLabel.setText("");
                    downloadedLabel.setText("");
                    button.setVisible(false);
                    locationButton.setVisible(true);
                });


                int numOfFiles = -1;
                DataInputStream in;
                Socket socket = null;
                while (numOfFiles < 0) {
                    socket = server.accept();
                    in = new DataInputStream(socket.getInputStream());
                    numOfFiles = in.readInt();
                }

                received = numOfFiles;
                Platform.runLater(() -> {
                    mainLabel.setText(received + " File(s) received. Preparing download(s)");
                    locationButton.setVisible(false);
                });
                processingFiles = new ArrayList<>();
                errors = new ArrayList<>();
                in = new DataInputStream(socket.getInputStream());

                currentClient = in.readUTF();

                for (int i = 0; i < numOfFiles; i++) {
                    String fileName = in.readUTF();
                    long fileSize = in.readLong();
                    String inputPath = in.readUTF();
                    String inputMD5 = in.readUTF();

                    File file = new File(inputLocation + File.separator + fileName);
                    if (file.exists()) {
                        errors.add(String.format("%s already exists", fileName));
                    } else {
                        DownloadingFile downloadingFile = new DownloadingFile(fileName, inputPath, inputMD5, file.getAbsolutePath(), fileSize);
                        processingFiles.add(downloadingFile);
                    }
                }
                socket.close();
                downloadFiles();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void downloadFiles() {
        new Thread(() -> {
            try {
                Stage stage = (Stage) button.getScene().getWindow();

                DatagramSocket datagramSocket = new DatagramSocket();
                datagramSocket.connect(InetAddress.getByName(Constants.FIND_OWN_IP_HOST), Constants.FIND_OWN_IP_PORT);
                String ip = datagramSocket.getLocalAddress().getHostAddress();
                datagramSocket.close();

                for (DownloadingFile downloadingFile : processingFiles) {
                    File file = new File(downloadingFile.getOutputPath());
                    if (file.exists()) {
                        String checksum = getFileChecksum(file);
                        if (checksum.equals(downloadingFile.getInputMd5())) {
                            continue;
                        }
                    }

                    Socket socket = new Socket(currentClient, Constants.SEND_APP_PORT);
                    DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

                    dataOutputStream.writeInt(Constants.REQUEST_DOWNLOAD);
                    dataOutputStream.writeUTF(ip);
                    dataOutputStream.writeUTF(downloadingFile.getInputPath());
                    dataOutputStream.writeLong(downloadingFile.getBytesWritten());

                    dataOutputStream.close();
                    socket.close();

                    Socket receiveSocket = server.accept();
                    DataInputStream in = new DataInputStream(receiveSocket.getInputStream());

                    int status = in.readInt();

                    switch (status) {
                        case Constants.READY -> {
                            FileOutputStream out = new FileOutputStream(file, true);

                            Platform.runLater(() -> {
                                mainLabel.setText(String.format("Downloading %d out of %d file(s)", processingFiles.indexOf(downloadingFile) + 1, received));
                                fileNameLabel.setText("File name: " + downloadingFile.getFileName());
                                fileSizeLabel.setText(String.format("File size: %,d bytes", downloadingFile.getFileSize()));
                                progressLabel.setText("Downloading: 0%");
                            });

                            int count;
                            long total = downloadingFile.getFileSize();
                            double bytesPerPercent = total / 100.0;
                            int check = 0;
                            byte[] buffer = new byte[Constants.IDEAL_BUFFER_SIZE];
                            boolean printProgress = total >= Constants.IDEAL_BUFFER_SIZE;
                            long start = System.currentTimeMillis();
                            while ((count = in.read(buffer)) > 0) {
                                out.write(buffer, 0, count);
                                check += count;
                                downloadingFile.addBytesWritten(count);
                                if (check >= bytesPerPercent && printProgress) {
                                    long cost = System.currentTimeMillis() - start;
                                    long read = downloadingFile.getBytesWritten();
                                    Platform.runLater(() -> {
                                        speedLabel.setText(String.format("Read %,d bytes, speed: %,d MB/s%n", read, read / cost / 1000));
                                        progressLabel.setText("Downloading: " + (int) ((read / (float) total) * 100) + "%");
                                    });
                                    check = 0;
                                }
                            }
                            out.close();

                            Platform.runLater(() -> {
                                speedLabel.setText(String.format("Read %,d bytes", downloadingFile.getFileSize()));
                                progressLabel.setText("Downloading: 100%");
                                downloadedLabel.setText("Comparing file checksum");
                            });

                            String checksum = getFileChecksum(file);
                            if (!checksum.equals(downloadingFile.getInputMd5())) {
                                errors.add(String.format("File %s failed to download correctly", downloadingFile.getFileName()));
                            } else {
                                downloaded++;
                            }
                            Platform.runLater(() -> downloadedLabel.setText(downloaded + " out of " + received + " file(s) successfully downloaded"));
                        }
                        case Constants.FILE_DOES_NOT_EXIST -> Platform.runLater(() -> ErrorMessage.showMessage("File " + downloadingFile.getFileName() + " Does not exist", stage));
                    }
                    in.close();
                    receiveSocket.close();
                }

                if (downloaded == received) {
                    successfulTransfer(currentClient);
                    Platform.runLater(() -> {
                        mainLabel.setText("Transfer successful");
                        button.setVisible(true);
                    });
                } else {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append("Transfer did not fully complete:\n");
                    for (String error : errors) {
                        stringBuilder.append(error).append("\n");
                    }
                    failedTransfer(currentClient, stringBuilder.toString());
                    Platform.runLater(() -> {
                        mainLabel.setText("Transfer did not fully complete");
                        ErrorMessage.showMessage(stringBuilder.toString(), stage);
                        button.setVisible(true);
                    });
                }

                processingFiles = null;
                errors = null;
                currentClient = null;
                downloaded = 0;
                received = 0;
                server.close();
            } catch (IOException e) {
                try {
                    if (!shuttingDown) {
                        Platform.runLater(() -> mainLabel.setText("Lost connection - Attempting reconnect in 10 seconds"));
                        Thread.sleep(10000);
                        listen();
                    }
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

    public void failedTransfer(String host, String message) throws IOException {
        Socket socket = new Socket(host, Constants.SEND_APP_PORT);
        DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
        dataOutputStream.writeInt(Constants.TRANSFER_FAILED);
        dataOutputStream.writeUTF(message);
        dataOutputStream.flush();
        dataOutputStream.close();
        socket.close();
    }

    public void successfulTransfer(String host) throws IOException {
        Socket socket = new Socket(host, Constants.SEND_APP_PORT);
        DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
        dataOutputStream.writeInt(Constants.SUCCESSFUL_TRANSFER);
        dataOutputStream.flush();
        dataOutputStream.close();
        socket.close();
    }

    @FXML
    private void restart() {
        listen();
    }

    private String getFileChecksum(File file) throws IOException {
        MessageDigest messageDigest = null;
        try {
            messageDigest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        assert messageDigest != null;

        FileInputStream fis = new FileInputStream(file);

        byte[] byteArray = new byte[Constants.IDEAL_BUFFER_SIZE];
        int bytesCount;

        while ((bytesCount = fis.read(byteArray)) != -1) {
            messageDigest.update(byteArray, 0, bytesCount);
        }

        fis.close();
        byte[] bytes = messageDigest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte aByte : bytes) {
            sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    @FXML
    private void changeLocation() {
        if (!locationButton.isVisible()) {
            return;
        }
        Stage stage = (Stage) button.getScene().getWindow();
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Choose folder");
        File file = new File(inputLocation);
        directoryChooser.setInitialDirectory(file);
        File selected = directoryChooser.showDialog(stage);
        if (selected != null) {
            inputLocation = selected.getAbsolutePath();
            locationLabel.setText(inputLocation);
        }
    }
}
