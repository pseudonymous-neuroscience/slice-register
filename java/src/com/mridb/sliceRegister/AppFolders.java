package com.mridb.sliceRegister;

import java.io.IOException;

public class AppFolders {

	public static String getProjectRoot() {
		return "/source/matlabLessons";
	}

	public static String getDataFolder()  throws IOException {
		String result = Util.getCanonicalPath(getProjectRoot(), "data");
		Util.ensureDirectoryExists(result);
		return result;
	}
	public static String getRecipeFolder() throws IOException {
		String result = Util.getCanonicalPath(getDataFolder(), "recipes");
		Util.ensureDirectoryExists(result);
		return result;
	}
	public static String getFileDescriptionFolder() throws IOException {
		String result = Util.getCanonicalPath(getDataFolder(), "fileDesc");
		Util.ensureDirectoryExists(result);
		return result;
	}
	public static String getFileAssociationFolder() throws IOException {
		String result = Util.getCanonicalPath(getDataFolder(), "assoc");
		Util.ensureDirectoryExists(result);
		return result;
	}
	public static String getOpsFolder() throws IOException {
		String result = Util.getCanonicalPath(getDataFolder(), "ops");
		Util.ensureDirectoryExists(result);
		return result;
	}
	public static String getTempFolder() throws IOException {
		String tempFolder = Util.getCanonicalPath(getDataFolder(), "temp");
		Util.ensureDirectoryExists(tempFolder);
		return tempFolder;
		
		
	}

	public static String getNewTempFile() throws IOException {
		String fileName = Util.randomString() + ".txt";
		String path = Util.getCanonicalPath(getTempFolder(), fileName);
		return path;
				
	}
	
}
