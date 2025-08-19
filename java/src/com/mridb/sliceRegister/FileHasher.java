package com.mridb.sliceRegister;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
//import java.sql.SQLException;
//import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

//import com.google.api.client.util.Base64;
//import com.google.common.io.Files;

//import warpDrive.MySqlWrapper;

// This creates a SHA-512 hash of a file
public class FileHasher {

	static int defaultChunkSize = 10 * 1024 * 1024; // 10 Mb
	static FileHasher singleton;
	int chunkSize;
	boolean isReadingFile;

	byte[] buffer;
	byte[] chunk;
	String algorithm = "SHA-512";

	// stores chunk size preference, and handles static functions
	// without requiring a constructor every time
	private static FileHasher getSingleton() {
		if(singleton == null) {
			singleton = new FileHasher();
		}
		return singleton;
	}

	private static FileHasher makeNewHasher() {
		FileHasher hasher = new FileHasher();
		hasher.chunkSize = getSingleton().getChunkSize();
		return hasher;
	}

	public FileHasher() {
		try {
			this.setChunkSize(defaultChunkSize);
			this.setBufferSize(defaultChunkSize);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}



	public static class FolderHash {
		public String path;
		public long bytes;
		public String hash;
		public ArrayList<FolderHash> children;

		public FolderHash() {
			this.children = new ArrayList<FolderHash>();
		}
		public FolderHash(String path, String hash, long bytes) {
			this();
			this.path = path;
			this.hash = hash;
			this.bytes = bytes;
		}
		public String toString() {
			return Util.newGson().toJson(this);
		}
		public static FolderHash fromJson(String json) {
			return Util.newGson().fromJson(json, FolderHash.class);
		}
		public long getSize() {
			return this.bytes;
		}
		public String getPath() {
			return this.path;
		}
		public String getHash() {
			return this.hash;
		}
		public int getChildCount() {
			return this.children.size();
		}
		public FolderHash getChild(int index) {
			return this.children.get(index);
		}
	}



	// this lets you control how much memory will be allocated (one for buffer, one for chunk)
	public void setChunkSize(int size) throws IOException {
		if(!this.isReadingFile) {
			this.chunkSize = size;
			this.chunk = new byte[size];	
		}
		else {
			// code is not written to handle chunk size changes in the midst of a file read
			throw new IOException("unable to set chunk size while a file is being read");
		}
	}
	public int getChunkSize() {
		return this.chunkSize;
	}

	public void setBufferSize(int size) throws IOException {
		if(!this.isReadingFile) {
			this.buffer = new byte[size];
		}
		else {
			// code is not written to handle chunk size changes in the midst of a file read
			throw new IOException("unable to set chunk size while a file is being read");
		}
	}
	public int getBufferSize() {
		return this.buffer.length;
	}


	// hash the entire file
	public static String hashFile(String filePath) throws IOException, NoSuchAlgorithmException {

		long size = java.nio.file.Files.size(Paths.get(filePath));		
		return hashFileChunk(filePath, 0, size);
	}

	public static String hashFileChunk(String filePath, long startIndex, long endIndex) throws IOException, NoSuchAlgorithmException {
		FileHasher hasher = makeNewHasher();
		String result = Util.toBase64(hasher.hashFileToBytesInternal(filePath, startIndex, endIndex));
		//System.out.println("string length: " + result.length());
		return result;

	}

	// actual logic to hash a file or folder
	private byte[] hashFileToBytesInternal(String filePath, long startIndex, long endIndex) throws IOException, NoSuchAlgorithmException {

		File file = new File(filePath);
		if(file.isDirectory()) {
			return this.hashFolderToBytesInternal(filePath);
		}else {

			MessageDigest digest;
			try {
				digest = MessageDigest.getInstance(this.algorithm);
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				return new byte[0];
			}		
			// prevent user from goofing with the chunk size while a file read is taking place
			isReadingFile = true;

			// read the file in chunks; this balances the tradeoff of time (# of disk reads) with memory (for giant files)
			java.io.FileInputStream stream = new java.io.FileInputStream(filePath);
			stream.skip(startIndex);
			long totalRead = 0;
			long pieceLength = endIndex - startIndex;
			int chunkIndex = 0;
			while((totalRead < pieceLength) && (stream.available() > 0)) {
				int bytesRead = stream.read(buffer, 0, chunkSize);
				if(bytesRead >= chunkSize && chunkIndex == 0) {
					// we have a full chunk in the buffer, so we don't have to copy to the chunk
					digest.update(buffer);						
				}else {
					// we are going to copy to the fixed size chunk to prevent a ton of new buffers from being allocated for a large file
					int chunkRemaining = chunkSize - chunkIndex;
					if(chunkRemaining > bytesRead) { 
						// the bytes we have won't fill up the chunk yet, just keep reading
						System.arraycopy(buffer, 0, chunk, chunkIndex, bytesRead);
						chunkIndex += bytesRead;
					}else {
						// chunk is full; update the hash
						System.arraycopy(buffer, 0, chunk, chunkIndex, chunkRemaining);
						digest.update(chunk);
						int newChunkSize = bytesRead - chunkRemaining;
						// start the next chunk with whatever is left
						System.arraycopy(buffer, chunkRemaining, chunk, 0, newChunkSize);
						chunkIndex = newChunkSize;
					}
				}
			}
			stream.close();
			isReadingFile = false;

			// finalize the hash with any leftover chunk
			byte[] hash;
			if(chunkIndex > 0) {
				byte[] truncated = java.util.Arrays.copyOf(buffer, chunkIndex);
				hash = digest.digest(truncated);
			}else {
				hash = digest.digest();			
			}
			//			System.out.println("bytes length: " + hash.length);
			return hash;
		}
	} // method FileHasher.hashFileToBytesInternal

	private byte[] hashFolderToBytesInternal(String path) throws NoSuchAlgorithmException, IOException {
		File file = new File(path);
		if(!file.exists()) {
			throw new IOException("file " + path + " does not exist");
		}
		if(!file.isDirectory()) {			
			return this.hashFileToBytesInternal(path, 0, Util.getFileSize(path));
		}else {
			if(path.charAt(path.length() - 1) != File.separatorChar) {
				path = path + File.separator;
			}
			String[] contents = file.list();
			TreeSet<String> hashes = new TreeSet<String>();
			for(String fileName : contents) {
				String subPath = path + fileName; 
				hashes.add(this.hashFolder(subPath));
			}
			StringBuilder sb = new StringBuilder();
			for(String hash : hashes) {
				sb.append(hash);
			}
			System.out.println("folder content (" + path + " hash");
			System.out.println(hashString(sb.toString()));

			byte[] hash = this.hashStringInternal(sb.toString());
			//			System.out.println("bytes length: " + hash.length);
			return hash;
		}
	} // private method FileHasher.hashFolderToBytesInternal


	public static FolderHash hashFolderStructure(String filePath) throws NoSuchAlgorithmException, IOException {
		FileHasher hasher = FileHasher.getSingleton();
		FolderHash result = new FolderHash();
		File file = new File(filePath);
		if(!file.exists()) {
			return null;
		}

		boolean isDir = file.isDirectory();
		if(isDir) {
			if(filePath.charAt(filePath.length() - 1) != File.separatorChar) {
				filePath = filePath + File.separator;
			}
			result.path = filePath;
			String[] contents = file.list();
			TreeSet<String> hashes = new TreeSet<String>();
			result.bytes = 0;
			for(String fileName : contents) {
				String subPath = filePath + fileName; 
				FolderHash child = hashFolderStructure(subPath);
				hashes.add(child.hash);
				result.children.add(child);
				result.bytes += child.bytes;
			}
			StringBuilder sb = new StringBuilder();
			for(String hash : hashes) {
				sb.append(hash);
			}
			System.out.println("folder (" + filePath + " content hash");
			System.out.println(hashString(sb.toString()));

			result.hash = hashString(sb.toString());

		}else {
			result.path = filePath;
			result.hash = hashFile(filePath);
			Path pathObj = file.toPath();
			result.bytes = java.nio.file.Files.size(pathObj);
		}
		//		System.out.println("structure hash length: " + result.hash.length());
		return result;
	} // method FileHasher.hashFolderStructure 



	public static String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < bytes.length; i++) {
			sb.append(byteToHex(bytes[i]));
		}
		return sb.toString();
	}

	private static String byteToHex(byte i) {
		int a = i >> 4;
		a = a & 0xF;
		int b = i & 0xF;
		String str = new String();
		str = str + byteToHex(a);
		str = str + byteToHex(b);
		return str;
	}

	private static char byteToHex(int b) {
		if(b < 0) {
			// 2's complement
			b = (b * -1) + 1;
		}
		if(b == 0) {return '0';} 
		else if(b == 1) {return '1';} 
		else if(b == 2) {return '2';} 
		else if(b == 3) {return '3';} 
		else if(b == 4) {return '4';} 
		else if(b == 5) {return '5';} 
		else if(b == 6) {return '6';} 
		else if(b == 7) {return '7';} 
		else if(b == 8) {return '8';} 
		else if(b == 9) {return '9';} 
		else if(b == 10) {return 'A';} 
		else if(b == 11) {return 'B';} 
		else if(b == 12) {return 'C';} 
		else if(b == 13) {return 'D';} 
		else if(b == 14) {return 'E';} 
		else if(b == 15) {return 'F';} 
		else {return 'X';}
	}

	private static byte[] hashBytesInternal(byte[] toHash) {
		MessageDigest digest = null;
		try {
			digest = MessageDigest.getInstance(getSingleton().algorithm);			
		}
		catch(NoSuchAlgorithmException ex){
			return new byte[0];
		}
		digest.update(toHash);
		byte[] hash = digest.digest();			
		//		System.out.println("bytes length: " + hash.length);
		return hash;
	}
	private static byte[] hashStringInternal(String toHash) {
		return hashBytesInternal(Util.fromBase64(toHash));
	}

	public static String hashBytes(byte[] toHash) {
		byte[] hash = hashBytesInternal(toHash);
		String result = Util.toBase64(hash);
		//System.out.println("string length: " + result.length());
		return result;
	}

	public static String hashString(String toHash) {
		return hashBytes(Util.utf82Bytes(toHash));
	}


	public static String hashFolder(String path) throws NoSuchAlgorithmException, IOException {
		byte[] hash = makeNewHasher().hashFolderToBytesInternal(path);
		String result = Util.toBase64(hash);
		//	System.out.println("string length: " + result.length());
		return result;
	}



	// class FileHasher::FolderMap
	public static class FolderMap {
		Map<String, Date> dateMap;
		Map<String, FolderHash> pathMap;
		Map<String, String> hashMap;
		Map<Long, String> sizeMap;
		boolean isSizeMapCurrent = true;

		public FolderMap() {
			dateMap = new TreeMap<String, Date>();
			pathMap = new TreeMap<String, FolderHash>();
			hashMap = new TreeMap<String, String>();
			sizeMap = new TreeMap<Long, String>();
		}

		public void addHash(FolderHash hash, Date date) {
			boolean putThis = true;
			String path = hash.path;
			if(dateMap.containsKey(path)) {
				Date oldDate = dateMap.get(path);
				if(oldDate.compareTo(date) < 0) {
					putThis = true;
				}
			} 
			if(putThis) {
				this.isSizeMapCurrent = false;
				dateMap.put(path, date);
				pathMap.put(path, hash);
				//				sizeMap.put(bytes, path);
			}
		}
		private void updateIndexMaps() {
			if(!this.isSizeMapCurrent) {
				sizeMap.clear();
				hashMap.clear();
				Iterator<String> pathIterator = pathMap.keySet().iterator();
				while(pathIterator.hasNext()) {
					String path = pathIterator.next();
					FolderHash hash = pathMap.get(path);
					if(!sizeMap.containsKey(hash.bytes)) {
						sizeMap.put(hash.bytes, path);
					} else {
						String oldPath = sizeMap.get(hash.bytes);
						sizeMap.put(hash.bytes, oldPath + "|" + path);
					}
					if(!hashMap.containsKey(hash.hash)) {
						hashMap.put(hash.hash, hash.path);
					} else {
						String oldHash = hashMap.get(hash.hash);
						hashMap.put(hash.hash, oldHash + "|" + path);
					}
				}
				this.isSizeMapCurrent = true;
			}
		}
		public boolean containsPath(String path) {
			return this.dateMap.containsKey(path);
		}
		public boolean containsHash(String hash) {
			return this.hashMap.containsKey(hash);
		}
		public boolean containsSize(Long size) {
			return this.sizeMap.containsKey(size);
		}

		public FolderHash getFolderHashFromPath(String path) {
			return this.pathMap.get(path);
		}
		public String[] getPathsFromHash(String hash) {
			if(hashMap.containsKey(hash)) {
				String paths = hashMap.get(hash);
				return paths.split("\\|");
			} else {
				return new String[0];
			}
		}
		public Date getIndexDate(String path) {
			return dateMap.get(path);
		}
		public int pathCount() {
			return this.pathMap.size();
		}
		public int sizeCount() {
			updateIndexMaps();
			return this.sizeMap.size();
		}
		public int hashCount() {
			updateIndexMaps();
			return this.hashMap.size();
		}

		public static class HashIterator implements Iterator<FolderHash>{

			Long[] sizeArray;
			int sizeIndex = -1;
			int sizeSubIndex = -1;
			String[] subItems;
			FolderMap parent;

			public HashIterator(FolderMap toIterate) {
				this.parent = toIterate;
				this.parent.updateIndexMaps();
				this.sizeArray = new Long[parent.sizeMap.keySet().size()];
				this.parent.sizeMap.keySet().toArray(this.sizeArray);
				this.sizeIndex = sizeArray.length - 1;
				this.sizeSubIndex = 0;
				if(this.sizeIndex > 0) {
					String paths = this.parent.sizeMap.get(sizeArray[sizeIndex]);
					subItems = paths.split("\\|");					
				}
			}

			@Override
			public boolean hasNext() {
				if(sizeIndex > 0) {
					return true;
				} else if(sizeIndex == 0) {
					if(sizeSubIndex >= subItems.length) {
						return false;
					} else {
						return true;
					}
				} else {
					return false;
				}
			}

			@Override
			public FolderHash next() {
				if(this.sizeSubIndex >= this.subItems.length) {
					this.sizeIndex--;
					this.sizeSubIndex = 0;
					if(this.sizeIndex >= 0) {
						Long nextSize = this.sizeArray[sizeIndex];
						String paths = this.parent.sizeMap.get(nextSize);
						this.subItems = paths.split("\\|");
					} else {
						return null;
					}
				}
				String path = this.subItems[this.sizeSubIndex];
				FolderHash hash = parent.pathMap.get(path);
				this.sizeSubIndex++;
				return hash;
			}
		}

		public Iterator<FolderHash> getFolderHashIteratorLargestFileFirst(){
			HashIterator iterator = new HashIterator(this);
			return iterator;
		}
		public Iterator<String> getHashIterator(){
			return hashMap.keySet().iterator();
		}
		public Iterator<String> getPathIteratorAlphabetized(){
			return pathMap.keySet().iterator();
		}

		public static FolderMap getMissingFiles(FolderMap toAudit, FolderMap targets) {
			FolderMap missingMap = new FolderMap();
			Iterator<String> hashIterator = targets.getHashIterator();
			while(hashIterator.hasNext()) {
				String hash = hashIterator.next();
				if(!toAudit.containsHash(hash)) {
					String[] paths = targets.getPathsFromHash(hash);
					String examplePath = paths[0];
					FolderHash folderHash = targets.getFolderHashFromPath(examplePath);
					Date date = targets.getIndexDate(examplePath);
					missingMap.addHash(folderHash, date);
				}
			}
			missingMap.updateIndexMaps();
			return missingMap;
		}

		private FolderMap filterFilePaths(boolean foldersOnly) {
			FolderMap result = new FolderMap();
			Iterator<FolderHash> iterator = this.getFolderHashIteratorLargestFileFirst();
			while(iterator.hasNext()) {
				FolderHash hash = iterator.next();
				String path = hash.path;
				Date date = this.dateMap.get(path);
				File file = new File(path);
				if(file.isDirectory() == foldersOnly) {
					result.addHash(hash, date);
				}
			}
			return result;
		}
		public FolderMap filterForFilesOnly() {
			return filterFilePaths(false);
		}
		public FolderMap filterForFoldersOnly() {
			return filterFilePaths(true);
		}

		private static FolderHash hashFolderToLogFile(String filePath, Writer writer) throws NoSuchAlgorithmException, IOException {

			FileHasher hasher = FileHasher.getSingleton();
			FolderHash result = new FolderHash();
			File file = new File(filePath);
			if(!file.exists()) {
				return null;
			}

			boolean isDir = file.isDirectory();
			if(isDir) {
				if(filePath.charAt(filePath.length() - 1) != File.separatorChar) {
					filePath = filePath + File.separator;
				}
				result.path = filePath;
				String[] contents = file.list();
				TreeSet<String> hashes = new TreeSet<String>();
				result.bytes = 0;
				for(String fileName : contents) {
					String subPath = filePath + fileName; 
					FolderHash child = hashFolderToLogFile(subPath, writer);
					hashes.add(child.hash);
					result.children.add(child);
					result.bytes += child.bytes;
				}
				StringBuilder sb = new StringBuilder();
				for(String hash : hashes) {
					sb.append(hash);
				}
				System.out.println("folder (" + filePath + " content hash");
				System.out.println(hashString(sb.toString()));

				result.hash = hashString(sb.toString());
				String now = Util.dateString();
				String toLog = String.format("%s|%s|%d|%s\n", result.hash, now, result.bytes, result.path);
				writer.write(toLog);

			}else {
				result.path = filePath;
				result.hash = hashFile(filePath);
				Path pathObj = file.toPath();
				result.bytes = java.nio.file.Files.size(pathObj);
				String now = Util.dateString();
				String toLog = String.format("%s|%s|%d|%s\n", result.hash, now, result.bytes, result.path);
				writer.write(toLog);
			}
			//		System.out.println("structure hash length: " + result.hash.length());
			return result;

		} // method FileHasher::FolderMap.hashFolderToLogFile

		public static void hashFolderToLogFile(String folder, String logFilePath) throws IOException, NoSuchAlgorithmException {
			Util.ensureFileHasFolder(logFilePath);
			File logFile = new File(logFilePath);
			FileWriter fileWriter = new FileWriter(logFile, true);
			BufferedWriter writer = new BufferedWriter(fileWriter);

			hashFolderToLogFile(folder, writer);

			writer.close();
			fileWriter.close();
		}


		public static FolderMap loadLogFile(String logFilePath) {

			// initialize return value
			FolderMap result = new FolderMap();

			// parse the file
			File logFile = new File(logFilePath);
			if(logFile.exists()) {
				try {
					BufferedReader reader = new BufferedReader(new FileReader(logFilePath));
					boolean finished = false;
					while(!finished) {
						String line = reader.readLine();
						if(line == null) {
							finished = true;
						} else {
							String[] items = line.split("\\|");
							if(items.length != 4) {
								int dummy = 1;
							}
							String hash = items[0];
							String dateString = items[1];
							String byteString = items[2];
							String path = items[3];
							long bytes = Long.parseLong(byteString);
							Date date = Util.parseDate(dateString);
							boolean putThis = true;
							if(result.containsPath(path)) {
								Date oldDate = result.getIndexDate(path);
								if(oldDate.compareTo(date) < 0) {
									putThis = true;
								} else {
									putThis = false;
								}
							} 
							if(putThis) {
								result.addHash(new FolderHash(path, hash, bytes), date);
							}
						}
					} // parse file loop
					result.updateIndexMaps();				
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
			}
			return result;
		} // static method FileHasher::FolderMap.loadLogFile 

		public static FolderMap merge(FolderMap map1, FolderMap map2) {
			FolderMap result = new FolderMap();

			Iterator<FolderHash> iterator1 = map1.getFolderHashIteratorLargestFileFirst();
			Iterator<FolderHash> iterator2 = map2.getFolderHashIteratorLargestFileFirst();
			while(iterator1.hasNext()) {
				FolderHash folderHash = iterator1.next();
				Date date = map1.getIndexDate(folderHash.path);
				result.addHash(folderHash, date);
			}
			while(iterator2.hasNext()) {
				FolderHash folderHash = iterator2.next();
				Date date = map2.getIndexDate(folderHash.path);
				result.addHash(folderHash, date);
			}
			return result;
		}

	} // class FileHasher::FolderMap
	// resume class FileHasher


	public static String combineHashes(String[] hashes) throws NoSuchAlgorithmException, IOException {
		StringBuilder sb = new StringBuilder();
		TreeSet<String> hashSet = new TreeSet<String>();
		for(String hash : hashes) {
			hashSet.add(hash);
		}
		Iterator<String> iterator = hashSet.iterator();
		while(iterator.hasNext()) {
			sb.append(iterator.next());
		}
		return hashString(sb.toString());
	}

	private static void testFolderHashToLog(String logPath1, String logPath2, String photo1Folder, String photo2Folder) throws NoSuchAlgorithmException, IOException {

		boolean log1Exists = new File(logPath1).exists();
		boolean log2Exists = new File(logPath2).exists();
		if(!log1Exists) { FolderMap.hashFolderToLogFile(photo1Folder, logPath1); }
		if(!log2Exists) { FolderMap.hashFolderToLogFile(photo2Folder, logPath2); }

		FolderMap map1 = FileHasher.FolderMap.loadLogFile(logPath1);
		FolderMap map2 = FileHasher.FolderMap.loadLogFile(logPath2);

		//		FolderMap missingFrom2 = FolderMap.getMissingFiles(map2, map1);
		//		FolderMap missingFrom1 = FolderMap.getMissingFiles(map1, map2);


		FolderMap missingFrom1 = FolderMap.getMissingFiles(map2, map1).filterForFilesOnly();
		FolderMap missingFrom2 = FolderMap.getMissingFiles(map1, map2).filterForFilesOnly();

		int pathCount1 = missingFrom1.pathCount();
		int pathCount2 = missingFrom2.pathCount();
		int hashCount1 = missingFrom1.hashCount();
		int hashCount2 = missingFrom2.hashCount();
		int sizeCount1 = missingFrom1.sizeCount();
		int sizeCount2 = missingFrom2.sizeCount();

		Iterator<FolderHash> iterator = missingFrom1.getFolderHashIteratorLargestFileFirst();
		while(iterator.hasNext()) {
			System.out.println(iterator.next());
		}


		int dummy = 1;
	}


	// 
	public static void main(String[] args) throws IOException, NoSuchAlgorithmException {




		String dataFolder = "Z:\\";
		String logPath = "G:\\data\\metadata\\Z.txt";
		FileHasher.FolderMap.hashFolderToLogFile(dataFolder, logPath);
		



	}






}
