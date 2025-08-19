package com.mridb.sliceRegister;

import java.io.UnsupportedEncodingException;
//import java.nio.ByteBuffer;
//import java.nio.charset.StandardCharsets;
//import java.security.InvalidAlgorithmParameterException;
//import java.security.InvalidKeyException;
//import java.security.NoSuchAlgorithmException;
//import java.security.SecureRandom;
import java.util.ArrayList;
//import java.util.Arrays;
//
//import javax.crypto.BadPaddingException;
//import javax.crypto.Cipher;
//import javax.crypto.IllegalBlockSizeException;
//import javax.crypto.NoSuchPaddingException;
//import javax.crypto.SecretKey;
//import javax.crypto.spec.*;
//
//import com.google.common.io.BaseEncoding;
//import com.google.gson.Gson;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;

public class EncryptedFolder {
	
//	private static int blockLength = PasswordProtectedKey.aesKeyLength;
//	private static String algName = "AES/CBC/PKCS5Padding"; 
	
	private boolean encryptFilenames;
	private String zipFilePath;
	private transient ArrayList<String> matchingPaths;
	private transient String password;
	
	public EncryptedFolder(String password, String zipFilePath) {
		this.matchingPaths = new ArrayList<String>();
		this.password = password;
		this.setZipFilePath(zipFilePath);
	}

	private void setZipFilePath(String path) {
		this.zipFilePath = path;
		matchingPaths.clear();
		
		// todo: check for wildcards
		matchingPaths.add(path);
	}

	public String zip(String sourcePath) throws ZipException {
		String errorString = "";
		for(int destNumber = 0; destNumber < this.matchingPaths.size(); destNumber++) {
			try {
				String destPath = this.matchingPaths.get(destNumber);
				ZipFile zipFile = new ZipFile(destPath, password.toCharArray());		
				ZipParameters zipParams = new ZipParameters();
				zipParams.setEncryptFiles(true);
				zipParams.setEncryptionMethod(EncryptionMethod.AES);
				
//				ZipModel model = zip
//				zip.setPassword(password.toCharArray());
				boolean isEncrypted = zipFile.isEncrypted();
				
				zipFile.addFile(sourcePath, zipParams);
				boolean isEncrypted1 = zipFile.isEncrypted();
				int dummy = 1;
			}catch(ZipException ex) {
				String msg = ex.getMessage();
				String target = "File does not exist: ";
				if(msg.startsWith(target)) {
					// todo: handle					
				}
				else {
					throw(ex);
				}
			}
		}
		return errorString;
	}

	
	public static void main(String[] args) throws UnsupportedEncodingException, ZipException {
		
		String plain = "test";
		String password = "pass";
		
		String folder = "C:\\Users\\Neuro\\Downloads\\";
		String[] files = new String[] {"eyelink eyetracker notes.txt", "eyelink eyetracker notes (1).txt"};
		String dest = "C:\\Users\\Neuro\\Downloads\\testZip.zip";
		
		EncryptedFolder ef = new EncryptedFolder(password, dest); 
		for(int i = 0; i < files.length; i++) {
			String path = folder + files[i];
			ef.zip(path);
		}
	}
	

}
