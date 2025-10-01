package org.opencdmp.deposit.dspacerepository.service.storage;

public interface FileStorageService {
	String storeFile(byte[] data);

	byte[] readFile(String fileRef);
}
