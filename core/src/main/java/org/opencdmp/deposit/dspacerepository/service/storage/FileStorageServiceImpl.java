package org.opencdmp.deposit.dspacerepository.service.storage;

import gr.cite.tools.logging.LoggerService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {
    private static final LoggerService logger = new LoggerService(LoggerFactory.getLogger(FileStorageServiceImpl.class));

    private final FileStorageServiceProperties properties;

    @Autowired
    public FileStorageServiceImpl(FileStorageServiceProperties properties) {
        this.properties = properties;
    }

    @Override
    public String storeFile(byte[] data) {
        try {
            String fileName = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
            Path storagePath = Paths.get(properties.getTransientPath() + "/" + fileName);
            Files.write(storagePath, data, StandardOpenOption.CREATE_NEW);
            return fileName;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public byte[] readFile(String fileRef) {
        try (FileInputStream inputStream = new FileInputStream(properties.getTransientPath() + "/" + fileRef)) {
            return inputStream.readAllBytes();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return new byte[0];
    }
}
