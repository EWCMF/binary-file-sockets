package main;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class Send {
    public int sendDetails(ArrayList<File> files, String host) throws IOException {
        Socket socket;
        try {
            socket = new Socket(host, Constants.RECEIVE_APP_PORT);
        } catch (ConnectException e) {
            System.out.println("Connection failed: Server not started");
            return Constants.SERVER_NOT_STARTED;
        } catch (SocketException se) {
            System.out.println("Connection failed: Permission denied");
            return Constants.PERMISSION_DENIED;
        }

        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        out.writeInt(files.size());

        DatagramSocket datagramSocket = new DatagramSocket();
        datagramSocket.connect(InetAddress.getByName(Constants.FIND_OWN_IP_HOST), Constants.FIND_OWN_IP_PORT);
        String ip = datagramSocket.getLocalAddress().getHostAddress();
        out.writeUTF(ip);
        datagramSocket.close();

        for (File file : files) {
            try {
                out.writeUTF(file.getName());
                out.writeLong(file.length());
                out.writeUTF(file.getAbsolutePath());
                out.writeUTF(getFileChecksum(file));

            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        out.close();
        socket.close();

        return 0;
    }

    public void send(String host, String filePath, long filePosition) throws IOException {
        Socket socket = null;
        DataOutputStream dataOutputStream = null;
        ByteArrayInputStream byteArrayInputStream = null;
        try {
            socket = new Socket(host, Constants.RECEIVE_APP_PORT);
            dataOutputStream = new DataOutputStream(socket.getOutputStream());
            File file = new File(filePath);

            if (!file.exists()) {
                dataOutputStream.writeInt(Constants.FILE_DOES_NOT_EXIST);
                dataOutputStream.flush();
                dataOutputStream.close();
                socket.close();
                return;
            }

            dataOutputStream.writeInt(Constants.READY);

            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r");
            randomAccessFile.seek(filePosition);

            byte[] byteArray = new byte[(int) file.length()];
            randomAccessFile.readFully(byteArray);

            byteArrayInputStream = new ByteArrayInputStream(byteArray);
            int count;
            byte[] buffer = new byte[Constants.IDEAL_BUFFER_SIZE];
            while ((count = byteArrayInputStream.read(buffer)) > 0) {
                dataOutputStream.write(buffer, 0, count);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (byteArrayInputStream != null) {
                byteArrayInputStream.close();
            }
            if (dataOutputStream != null) {
                dataOutputStream.close();
            }
            if (socket != null) {
                socket.close();
            }
        }

    }

    public String scan(String host) {
        Socket socket;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, Constants.RECEIVE_APP_PORT), 2000);
            DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
            dataOutputStream.writeInt(-1);
            dataOutputStream.close();
            socket.close();
            return host;
        } catch (IOException e) {
            return "";
        }
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
}
