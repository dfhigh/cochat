package org.mib.cochat.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.mib.cochat.message.Image;
import org.mib.cochat.message.RawFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Iterator;

import static org.mib.common.validator.Validator.validateObjectNotNull;
import static org.mib.common.validator.Validator.validateStringNotBlank;

@Slf4j
public class FileService {

    private static final String FILE_PATH_FORMAT = "%s%s%s_%s";

    private final String directory;

    public FileService(final String directory) {
        validateStringNotBlank(directory, "file directory");
        this.directory = directory;
    }

    public RawFile createFile(String filename, File tmpFile, String mimeType) throws IOException {
        validateObjectNotNull(tmpFile, "tmp file");
        validateStringNotBlank(mimeType, "file mime type");
        log.info("creating file {} at {} of type {}...", filename, tmpFile.getAbsolutePath(), mimeType);
        RawFile file = null;
        if (StringUtils.startsWithIgnoreCase(mimeType, "image/")) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(mimeType);
            if (!readers.hasNext()) {
                log.error("no image readers retrieved for mime type {}", mimeType);
                throw new IllegalArgumentException("invalid image mime type " + mimeType);
            }
            while (readers.hasNext()) {
                ImageReader reader = readers.next();
                try (ImageInputStream iis = new FileImageInputStream(tmpFile)) {
                    reader.setInput(iis);
                    int height = reader.getHeight(reader.getMinIndex()), width = reader.getWidth(reader.getMinIndex());
                    file = new Image(filename, height, width);
                    break;
                } catch (IOException e) {
                    log.error("failed to read height and width for image {}", filename, e);
                } finally {
                    reader.dispose();
                }
            }
            if (file == null) {
                throw new RuntimeException("no valid image reader for image " + filename);
            }
        } else {
            file = new RawFile(filename);
        }
        String path = getFilePath(file);
        try (OutputStream os = new FileOutputStream(path); InputStream stream = new FileInputStream(tmpFile)) {
            IOUtils.copy(stream, os);
            return file;
        } catch (IOException e) {
            log.error("failed to write content to file {}", path, e);
            throw new RuntimeException(e);
        }
    }

    public void deleteFile(RawFile file) {
        validateObjectNotNull(file, "raw file");
        String token = file.getToken();
        log.info("deleting file {}...", token);
        File f = new File(getFilePath(file));
        if (f.exists() && f.isFile()) {
            if (f.delete()) {
                log.info("deleted file {} for token {}", f.getAbsolutePath(), token);
            } else {
               log.error("unable to delete file {} for token {}, restoring record...", f.getAbsolutePath(), token);
               throw new RuntimeException("failed to delete file " + f.getAbsolutePath());
            }
        } else {
            log.warn("file for token {} is not deletable, ignoring...", token);
        }
    }

    public String getFilePath(RawFile file) {
        validateObjectNotNull(file, "file");
        return getFilePath(file.getToken(), file.getName());
    }

    public void refreshLocationForFile(RawFile file, String originToken) throws IOException {
        validateObjectNotNull(file, "file");
        String cur = getFilePath(originToken, file.getName()), next = getFilePath(file);
        moveFile(cur, next);
    }

    private void copyFile(String src, String dest) throws IOException {
        log.debug("copying file {} to {}...", src, dest);
        try (OutputStream os = new FileOutputStream(dest); InputStream stream = new FileInputStream(src)) {
            IOUtils.copy(stream, os);
        }
    }

    private void moveFile(String src, String dest) throws IOException {
        log.debug("moving file from {} to {}...", src, dest);
        copyFile(src, dest);
        if (!new File(src).delete()) {
            log.error("failed to delete src file {} after moving it to {}", src, dest);
        }
    }

    private String getFilePath(String token, String filename) {
        return String.format(FILE_PATH_FORMAT, directory, File.separator, token, filename);
    }
}
