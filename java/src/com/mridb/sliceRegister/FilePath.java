package com.mridb.sliceRegister;

import java.io.File;
import java.io.IOException;

import com.google.gson.Gson;

public class FilePath {

	private transient File file;
	private String[] pieces;
	private boolean isUsb;
	private boolean isWindows;
	
	private FilePath(String[] pieces, boolean isUsb, boolean isWindows) {
		this.pieces = new String[pieces.length];
		for(int i = 0; i < pieces.length; i++) {
			this.pieces[i] = pieces[i];
		}
		this.file = computeCanonicalPath(this.pieces);
		this.isUsb = isUsb;
		this.isWindows = isWindows;
	}
	
	
	public static FilePath createPath(String path) {
		return createPath(path, false);		
	}
	public static FilePath createPath(boolean isUsb, boolean isWindows, String firstPiece, String[] subsequentPieces) {
		String[] allPieces = new String[subsequentPieces.length + 1];
		allPieces[0] = firstPiece;
		for(int i = 0;i < subsequentPieces.length; i++) {
			allPieces[i + 1] = subsequentPieces[i];
		}
		FilePath filePath = new FilePath(allPieces, isUsb, isWindows);
		return filePath;
	}

	public static FilePath createPath(String path, boolean isUsb) {
		if(path.length() > 2) {
			if(path.charAt(1) == ':' && path.charAt(1) == '\\' ) {
				return createWindowsPath(path, isUsb);				
			}
		}
		return createLinuxPath(path, isUsb);				
	}
	
	private static File computeCanonicalPath(String[] pieces) {
		StringBuilder sb = new StringBuilder();
		if(pieces.length == 0) {
			return new File("/");
		}else {			
			sb.append(pieces[0]);{
				for(int i = 1; i < pieces.length; i++) {
					sb.append(File.separator);
					sb.append(pieces[i]);
				}
			}
		}
		String filePath = sb.toString();
		return new File(filePath);
	}
	
	public String serialize() {
		Gson gson = Util.newGson();
		return gson.toJson(this);
	}
	public FilePath deserialize(String json) {
		Gson gson = Util.newGson();
		FilePath result = gson.fromJson(json, FilePath.class);
		result.file = result.computeCanonicalPath(this.pieces);
		return result;
	}
	public String getPath() throws IOException {
		return this.file.getCanonicalPath();
	}

	public static FilePath createWindowsPath(String path) {
		return createWindowsPath(path, false);
	}
	public static FilePath createWindowsPath(String path, boolean isUsb) {
		String[] pieces = path.split("\\\\");
		FilePath filePath = new FilePath(pieces, isUsb, true);
		return filePath;
	}

	public static FilePath createLinuxPath(String path) {
		return createLinuxPath(path, false);
	}
	public static FilePath createLinuxPath(String path, boolean isUsb) {
		String[] pieces = path.split("/");
		FilePath filePath = new FilePath(pieces, isUsb, false);
		return filePath;
	}
	
	
	
	public static void main(String[] args) throws IOException {
		FilePath path = FilePath.createPath("/usr/lib/jvm/java-8-openjdk-amd64/jre/bin");
		String newPath = path.getPath();
		String serialized = path.serialize();
		FilePath deserialized = path.deserialize(serialized);
		String newNewPath = deserialized.getPath();
		
		int dummy = 1;
	}
	

}
