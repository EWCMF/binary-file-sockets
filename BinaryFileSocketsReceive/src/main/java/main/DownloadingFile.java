package main;

public class DownloadingFile {

    private final String fileName;
    private final String inputPath;
    private final String inputMd5;
    private final String outputPath;
    private final long fileSize;
    private long bytesWritten;


    public DownloadingFile(String fileName, String inputPath, String inputMd5, String outputPath, long fileSize) {
        this.fileName = fileName;
        this.inputPath = inputPath;
        this.inputMd5 = inputMd5;
        this.outputPath = outputPath;
        this.fileSize = fileSize;
        this.bytesWritten = 0;
    }

    public String getFileName() {
        return fileName;
    }

    public String getInputPath() {
        return inputPath;
    }

    public String getInputMd5() {
        return inputMd5;
    }

    public String getOutputPath() {
        return outputPath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }

    public void addBytesWritten(long bytesWritten) {
        this.bytesWritten += bytesWritten;
    }
}
