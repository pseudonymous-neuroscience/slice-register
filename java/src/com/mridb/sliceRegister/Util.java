package com.mridb.sliceRegister;

import java.awt.Desktop;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

//import com.mridb.sliceRegister.clientInterface.ExecutorGui;
import com.mridb.sliceRegister.pipeline.Settings;

import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public class Util {

	private static GsonBuilder _gsonBuilder = null;
	private static GsonBuilder _gsonPrettyBuilder = null;

	/** Converts a byte array into a (url-safe) base64-encoded string
	 * @param string
	 * @return
	 */
	public static String toBase64(byte[] bytes) {
		String string = BaseEncoding.base64().omitPadding().encode(bytes);
		string = string.replace('+', '-');
		string = string.replace('/', '_');
		return string;
	}

	public static String toBase64(String text) {
		return toBase64(text.getBytes(StandardCharsets.UTF_8));
	}


	/** Converts a (url-safe) base64-encoded string to a byte array
	 * @param string
	 * @return
	 */
	public static byte[] fromBase64(String string) throws java.lang.IllegalArgumentException {
		string = string.replace('-', '+');
		string = string.replace('_', '/');
		//		if(BaseEncoding.base64().canDecode(string)){
		return BaseEncoding.base64().omitPadding().decode(string);			
		//		}else {
		//			throw new java.lang.IllegalArgumentException("unable to decode string: " + string);
		//		}
	}

	public static byte[] gunzip(byte[] src) throws IOException {

		ByteArrayInputStream is = new ByteArrayInputStream(src);
		ByteArrayOutputStream os = new ByteArrayOutputStream(src.length);
		GZIPOutputStream zipOut = new GZIPOutputStream(os);

		byte[] buf = new byte[1024];
		int len;
		while ((len = is.read(buf)) > -1) {
			zipOut.write(buf, 0, len);
		}
		is.close();

		// Complete the GZIP file
		zipOut.finish();
		zipOut.close();

		return os.toByteArray();
	}
	public static float[] gunzipFloats(byte[] src) throws IOException {
		byte[] bytes = gunzip(src);
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		buffer.order(ByteOrder.LITTLE_ENDIAN);
		float[] result = new float[bytes.length / 4];
		for(int i = 0; i < result.length; i++) {
			result[i] = Float.NaN;
		}
		for(int i = 0; i < result.length; i++) {
			result[i] = buffer.getFloat();
		}
		return result;

	}



	/** Converts base64-encoded String to UTF-8 encoded string
	 * @param string
	 * @return
	 */
	public static String stringFromBase64(String string) throws java.lang.IllegalArgumentException {
		byte[] bytes = Util.fromBase64(string);
		return bytes2Utf8(bytes);
	}

	/** Converts a byte array to UTF-8 encoding
	 * @param string
	 * @return
	 */
	public static String bytes2Utf8(byte[] bytes) throws java.lang.IllegalArgumentException {
		String result = new String(bytes, StandardCharsets.UTF_8);
		return result;
	}
	public static byte[] utf82Bytes(String utf8) {
		return utf8.getBytes();
	}

	/** Utility function that gives you a random string 32 characters long  
	 * @return string using the base64 char set
	 */
	public static String randomString() {
		return randomString(32);
	}

	public static long getFileSize(String path) throws IOException {
		return java.nio.file.Files.size(Paths.get(path));
	}


	/** Utility function that gives you a random string of arbitrary length  
	 * @return string using the base64 char set
	 */
	public static String randomString(int length) {
		SecureRandom rand = new SecureRandom();
		byte[] bytes = new byte[length];
		rand.nextBytes(bytes);		
		String str = Util.toBase64(bytes);
		return str.substring(0, length);
	}

	public static String system(String command) throws Exception {
		Executor exec = new Executor();
		exec.setCommand(command);
		exec.executeSynchronously();
		String result = exec.getRelevantText();
		return result;
	}
	public static Executor fork(String shellInput) throws Exception {
		Executor exec = new Executor();
		String[] commandPlusArgs = shellInput.split(" ");
		exec.setCommand(commandPlusArgs);
		exec.executeAsynchronously();
		return exec;
	}
	//	public static ExecutorGui forkGui(String shellInput) throws Exception {
	//		Executor exec = new Executor();
	//		exec.setCommand(shellInput);
	//		ExecutorGui execGui = new ExecutorGui(exec);
	//		exec.executeAsynchronously();
	//		return execGui;
	//	}

	public static void openBrowser(String address) throws IOException, URISyntaxException {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			Desktop.getDesktop().browse(new URI(address));
		}
	}

	public static boolean isPc() {
		String osName = System.getProperty("os.name");
		if(osName.startsWith("Windows")) {
			return true;
		}
		return false;
	}
	public static boolean isLinux() {
		String osName = System.getProperty("os.name");
		if(osName.startsWith("Linux")) {
			return true;
		}
		return false;
	}

	public static String dateString() {
		//		Instant now = Instant.now();
		//		DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.from(ZoneOffset.UTC));
		//		// 2023-09-12T08:20:49.4864985
		//		String result = formatter.format(now);
		//		while(result.length() < "2023-09-12T08:20:49.4864985".length()) {
		//			result = result + "0";
		//		}
		//		return result;
		Instant now = Instant.now();
		ZoneId z = ZoneId.from(ZoneOffset.UTC);
		ZonedDateTime zdt = now.atZone(z);
		int mills = (int)(zdt.getNano() / 1e6);
		String result = String.format("%04d-%02d-%02d-%02d-%02d-%02d-%03d", 
				zdt.getYear(),
				zdt.getMonthValue(),
				zdt.getDayOfMonth(),
				zdt.getHour(),
				zdt.getMinute(),
				zdt.getSecond(),
				mills);
		return result;
	}
	public static String dateStringWithNanos() {
		Instant now = Instant.now();
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd-HH-mm-ss-nnnnnnnnn").withZone(ZoneId.from(ZoneOffset.UTC));
		return formatter.format(now);
	}
	public static Date parseDate(String dateString) {
		//		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("YYYY-MM-dd-HH-mm-ss-nnn").withZone(ZoneId.from(ZoneOffset.UTC));
		//		TemporalAccessor acc = formatter.parse(dateString);
		//		acc.get(TemporalField.)
		//		int dummy = 1;
		//		return Instant.from(acc);


		//		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss-SSS a z");
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss-SSS");
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		Date d = null;
		try {
			d = format.parse( dateString );
		} catch (java.text.ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return d;

	}
	public static String readStream(InputStream stream) {
		String result = new BufferedReader(
				new InputStreamReader(stream, StandardCharsets.UTF_8))
				.lines()
				.collect(Collectors.joining("\n"));
		return result;
	}
	//	public static byte[] readStreamBytes(InputStream stream) throws IOException {
	//		ByteSource byteSource = new ByteSource() {
	//			@Override
	//			public InputStream openStream() throws IOException {
	//				return stream;
	//			}
	//		};
	//		return byteSource.read();
	//	}
	public static byte[] loadFileBytes(String filePath) throws IOException {
		File file = new File(filePath);
		FileInputStream stream = new FileInputStream(filePath);
		BufferedInputStream bufStream = new BufferedInputStream(stream);
		long byteCount = file.length();
		byte[] bytes = readStreamBytes(bufStream, byteCount);
		bufStream.close();
		stream.close();
		return bytes;
	}
	public static byte[] readStreamBytes(InputStream stream, long byteCount) throws IOException {
		return readStreamBytes(stream, byteCount);
//
//
//		byte[] byteArray = new byte[(int)byteCount];
//		int totalRead = 0;
//		boolean finished = false;
//		while(!finished) {
//			int bytesRead = stream.read(byteArray);
//			if(bytesRead > -1) {
//				totalRead += bytesRead;
//				if(totalRead >= byteCount) {
//					finished = true;
//				}
//			} else {
//				finished = true;
//			}
//		}
//		return byteArray;
	}
	public static void skipNBytes(InputStream stream, long offset) throws IOException{
		long totalSkipped = 0;
		long remaining = offset;
		while(totalSkipped < offset) {
			long actuallySkipped = stream.skip(remaining);
			totalSkipped += actuallySkipped;
			remaining -= actuallySkipped;
		}

	}
	public static byte[] readStreamBytes(InputStream stream, long offset, int length) throws IOException {
		byte[] bytes = new byte[length];
		Util.skipNBytes(stream, offset);
		//		long totalSkipped = 0;
		//		long remaining = offset;
		//		while(totalSkipped < offset) {
		//			long actuallySkipped = stream.skip(remaining);
		//			totalSkipped += actuallySkipped;
		//			remaining -= actuallySkipped;
		//		}

		int bytesRead = stream.read(bytes);
		if(bytesRead < length) {
			int totalRead = bytesRead;
			boolean finished = false;
			byte[] buffer = new byte[4096];
			while(!finished) {
				bytesRead = stream.read(buffer);
				if(bytesRead == -1) {
					finished = true;
				} else {
					for(int i = 0; i < bytesRead; i++) {
						bytes[totalRead++] = buffer[i];
					}
				}
				if(totalRead >= length) {
					finished = true;
				}
			}
		}
		//		ByteStreams.read(stream, bytes, 0, length);
		return bytes;		
	}
	public static void save(String path, String value) throws IOException {
		save(path, value, true);
	}
	public static void save(String path, String value, boolean overwrite) throws IOException {
		int maxTries = 10;
		int errorCount = 0;
		boolean success = false;
		while(success == false) {

			try {
				File file = new File(path);
				if(file.exists()) {
					file.delete();
				}

				BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
				writer.write(value);
				writer.close();
				success = true;
			}
			catch(IOException ex) {
				// retry assuming disk is busy
				errorCount++;
				try {
					Thread.sleep(100);					
				} catch(InterruptedException ex1) {
					// it's ok
				}
				if(errorCount >= maxTries) {
					throw(ex);
				}
			}
		}

	}
	public static void append(String path, String value) throws IOException {
		save(path, value, false);
	}

	public static void save(String path, byte[] value, long offset) throws IOException {
		//		ByteSink byteSink = MoreFiles.asCharSink(path, StandardOpenOption.WRITE);
		//		OutputStream stream = byteSink.openStream();


		File file = new File(path);
		FileOutputStream stream = new FileOutputStream(path);
		FileChannel channel = stream.getChannel();
		channel.position(offset);
		ByteBuffer buffer = ByteBuffer.wrap(value);
		channel.write(buffer);


		//		BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8, StandardOpenOption.CREATE);
		//		writer.write(char[])value);
		//		writer.close();
	}

	public static byte[] loadBytes(String path) throws IOException {
		File file = new File(path);
		if(!file.exists()) {
			throw new java.io.FileNotFoundException("file " + path + " does not exist");
		}
		byte[] result = com.google.common.io.Files.toByteArray(file);
		return result;
	}
	public static String load(String path) throws IOException {
		byte[] bytes = loadBytes(path);
		String result = new String(bytes, StandardCharsets.UTF_8);
		return result;
	}

	public static String createTimestampedFolder(String rootFolderPath) throws IOException {		
		File folder = new File(rootFolderPath);
		boolean isNew = false;
		File result = null;
		while(!isNew) {
			//			String fileName = Util.randomString() + ".txt";
			String timeFolder = Util.dateStringWithNanos();
			String timePath = Util.getCanonicalPath(rootFolderPath, timeFolder);
			result = new File(timePath);
			if(!result.exists()) {
				isNew = true;
			}
		}
		Util.ensureDirectoryExists(result);
		//		result.createNewFile();
		return result.getCanonicalPath();
	}


	// returns a JSONObject (from json.org) which can be inspected for keys programmatically
	public static JSONObject parseJsonObject(String json) throws ParseException {
		// remove the "ans = " part that comes default from matlab/octave
		json = removeAns(json); 
		JSONParser parser = new JSONParser();
		JSONObject result = (JSONObject) parser.parse(json);
		return result;
	}
	public static Object getJsonValue(String key, JSONObject object) {
		Object result = null;
		//		for(int i = 0; i < object.len)
		return result;
	}

	// returns an Object (from google.com) which is sometimes easier to work with
	public static Object parseJson(String json) {
		json = removeAns(json);
		Object result = newGson().fromJson(json, Object.class);
		return result;
	}

	public static String toJson(Object value) {
		return Util.newGson().toJson(value);
	}
	public static String toPrettyJson(Object value) {
		return Util.newGson(true).toJson(value);
	}

	// removes the "ans = " text (and its variants) that precede output from octave commands
	public static String removeAns(String input) {
		String[] answerPrefixes = new String[] {"ans =\n", "ans = ", "ans ="};
		String result = input;
		boolean finished = false;
		for(int i = 0; i < answerPrefixes.length && finished == false; i++) {
			String prefix = answerPrefixes[i];
			if(input.length() >= prefix.length()) {
				String testSub = input.substring(0, prefix.length());
				if(testSub.contentEquals(prefix)) {
					result = input.substring(prefix.length());
					finished = true;
				}
			}
		}
		return result;
	}


	public static String getJarLocation() {
		String jarPath;
		try {
			jarPath = Settings.class
					.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI()
					.getPath();
			return jarPath;
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return null;
		}
	}


	public static Gson newGson() {
		return newGson(false);
	}
	public static Gson newGson(boolean prettyPrint) {
		return gsonBuilder(prettyPrint).create();
	}
	private static GsonBuilder gsonBuilder() {
		return gsonBuilder(false);
	}
	private static GsonBuilder gsonBuilder(boolean prettyPrint) {
		if(_gsonBuilder == null) {
			_gsonBuilder = new GsonBuilder();
			_gsonBuilder.disableHtmlEscaping();
			_gsonBuilder.serializeSpecialFloatingPointValues();
		}
		if(prettyPrint) {
			_gsonBuilder.setPrettyPrinting();
		}
		return _gsonBuilder;
	}
	public static String serialize(Object thing) {
		return serialize(false);
	}
	public static String serialize(Object thing, boolean prettyPrint) {
		return newGson(prettyPrint).toJson(thing);
		//		return newGson().toJson(thing);
	}

	public static String indentJson(String input) {
		Object obj = newGson().fromJson(input, Object.class);
		String result = newGson(true).toJson(obj);
		return result;
	}

	public static double getElapsedSeconds(Instant start, Instant end) {
		double nanos = (double) getElapsedNanos(start, end);
		double result = nanos / 1e9;
		return result;
	}
	public static long getElapsedNanos(Instant start, Instant end) {
		Duration dur = Duration.between(start, end);
		long result = dur.getSeconds() * (long)(1e9) + dur.getNano();
		return result;		
	}
	public static long getNanos(Duration duration) {
		long result = duration.getSeconds() * (long)(1e9) + duration.getNano();
		return result;
	}

	// return the maximum amount of RAM that can be allocated
	public static long getMaxMemory() {
		Runtime rt = Runtime.getRuntime();
		return rt.maxMemory();
	}

	// return then number of available CPU cores
	public static int getProcessorCount() {
		Runtime rt = Runtime.getRuntime();
		return rt.availableProcessors();
	}
	public static boolean ensureDirectoryExists(File folderPath) throws IOException {
		return ensureDirectoryExists(folderPath.getCanonicalPath());
	}
	public static boolean ensureFileHasFolder(String filePath) throws IOException {
		String separatorRegex = File.separator;
		if(separatorRegex.contentEquals("\\")) {
			separatorRegex = "\\\\";
		}
		String[] parts = filePath.split(separatorRegex);
		StringBuilder sb = new StringBuilder();
		int maxPart = parts.length - 1;
		for(int i = 0; i < maxPart; i++) {
			sb.append(parts[i]);
			sb.append(File.separator);			
		}
		return ensureDirectoryExists(sb.toString());
	}
	public static boolean ensureDirectoryExists(String absoluteFolderPath) {
		String separatorRegex = File.separator;
		if(separatorRegex.contentEquals("\\")) {
			separatorRegex = "\\\\";
		}
		String[] pieces = absoluteFolderPath.split(separatorRegex);
		int index = 0;
		String built = pieces[index];
		boolean finished = false;
		while(!finished) {
			index++;
			if(index >= pieces.length) {
				finished = true;
			}else {
				built = built + File.separator + pieces[index];
				File testFile = new File(built);
				if(testFile.exists() == false) {
					Path path = testFile.toPath();
					try {
						Files.createDirectory(path);
					} catch (IOException e) {
						LogError(e);
						return false;
					}
				}
			}
		}
		return true;					
	}

	public static void LogError(Exception ex) {
		// todo: implement
	}
	public static void Log(Exception ex) {
		// todo: implement
	}

	public static int executeCommandSync(String command, String[] environmentParameters, File workingDirectory, File standardOut) throws IOException{

		Runtime rt = Runtime.getRuntime();
		Process pr = rt.exec(command, environmentParameters, workingDirectory);
		int status = -9999;
		try {
			status = pr.waitFor();
		} catch (InterruptedException exception) {
			// todo: log the error
			status = -7777;
		}
		return status;
	}
	public static void executeCommandAsync(String command, String[] environmentParameters, File workingDirectory) throws IOException{
		Runtime rt = Runtime.getRuntime();
		Process pr = rt.exec(command, environmentParameters, workingDirectory);
	}

	public static String getCanonicalPath(String path) throws IOException {
		return Util.getCanonicalPath(path, false);
	}
	public static String getCanonicalPath(String firstPiece, String ... pieces) throws IOException {
		return Util.getCanonicalPath(false, firstPiece, pieces);
	}

	public static String getCanonicalPath(boolean isUsb, String firstPiece, String... pieces) throws IOException {
		FilePath filePath = FilePath.createPath(isUsb, Machine.isWindows(), firstPiece, pieces);
		return filePath.getPath();
	}

	public static String getCanonicalPath(String path, boolean isUsb) throws IOException {
		FilePath filePath = FilePath.createPath(path, isUsb);
		return filePath.getPath();
	}
	public static String[] fileparts(String filePath) {
		String[] result = new String[3];
		String separatorRegex = File.separator;
		if(separatorRegex.contentEquals("\\")) {
			separatorRegex = "\\\\";
		}
		String[] parts = filePath.split(separatorRegex);
		StringBuilder folder = new StringBuilder();
		int maxPart = parts.length - 1;
		for(int i = 0; i < maxPart; i++) {
			folder.append(parts[i]);
			folder.append(File.separator);			
		}

		String extension = "";
		String file = "";
		String fileName = parts[parts.length - 1];
		String lowerFileName = fileName.toLowerCase();
		if(lowerFileName.endsWith(".nii.gz")) {
			file = fileName.substring(0, fileName.length() - ".nii.gz".length());
			extension = fileName.substring(fileName.length() - ".nii.gz".length(), fileName.length());
		} else if(lowerFileName.endsWith(".tar.gz")) {
			file = fileName.substring(0, fileName.length() - ".tar.gz".length());
			extension = fileName.substring(fileName.length() - ".tar.gz".length(), fileName.length());			
		} else {
			int lastDot = fileName.lastIndexOf('.');
			extension = "";
			file = fileName;
			if(lastDot > -1) {
				extension = fileName.substring(lastDot);
				file = fileName.substring(0, lastDot);
			}			
		}


		result[0] = folder.toString();
		result[1] = file;
		result[2] = extension;
		return result;
	}
	public static String fullfile(String ... fileparts) {
		StringBuilder result = new StringBuilder();
		for(int i = 0; i < fileparts.length; i++) {
			result.append(fileparts[i]);
			if(i < (fileparts.length - 1)){
				result.append(File.separator);
			}
		}
		String path = null;
		try {
			path = Util.getCanonicalPath(result.toString());
		} catch (IOException e) {
			path = result.toString();
		}
		return path;
	}

	public static boolean fileExists(String path) {
		File file = new File(path);
		return file.exists();
	}
	public static String[] splitStringIgnoreEmpty(String input) {
		return splitStringIgnoreEmpty(input, " ");
	}
	public static String[] splitStringIgnoreEmpty(String input, String delimiter) {
		int backslashIndex = delimiter.indexOf('\\');
		if(backslashIndex > -1) {
			String subbedDelimiter = delimiter.replaceAll("\\\\", "\\\\\\\\");
			delimiter = subbedDelimiter;
		}
		String[] allParts = input.split(delimiter);
		ArrayList<String> parts = new ArrayList<String>();
		for(int i = 0; i < allParts.length; i++) {
			if(allParts[i].length() > 0) {
				parts.add(allParts[i]);
			}
		}
		String[] result = new String[parts.size()];
		result = parts.toArray(result);
		return result;
	}

	// todo: test in linux environment to see whether this is necessary or we can
	// always use the simpler getExistingFilePathCaseInsensitive2()
	public static String getExistingFilePathCaseInsensitive(String filePath) {
		if(fileExists(filePath)) {
			return filePath;
		}else {
			String[] fileParts = fileparts(filePath);
			String folderPath = fileParts[0];
			String fileName = fileParts[1] + fileParts[2];
			if(folderPath.length() == 0) {
				return null;
			}else {				
				File folder = new File(folderPath);
				if(!folder.exists()) {
					folderPath = getExistingFilePathCaseInsensitive(folderPath);
					if(folderPath == null) {
						return null;
					}else {
						folder = new File(folderPath);
					}
				}
				String[] files = folder.list();
				for(int i = 0; i < files.length; i++) {
					if(files[i].equalsIgnoreCase(fileName)) {
						return(folder + fileName); 
					}
				}
				return null;
			}
		}
	}
	//	public static String getExistingFilePathCaseInsensitive2(String filePath) throws IOException {
	//		return Util.getCanonicalPath(filePath);
	//	}


	//	public static void main(String[] args) throws Exception {
	//		String str = Util.dateString();
	////		Util.forkGui("docker run --rm -i -P autobrainstorm");
	////		Util.forkGui("docker run --rm -i -P -e DISPLAY_WIDTH=2400 -e DISPLAY_HEIGHT=1200 autobrainstorm");
	//		Util.forkGui("docker run --rm -i -P  -e DISPLAY_WIDTH=2400   -e DISPLAY_HEIGHT=1200  autobrainstorm");
	//		Util.forkGui("docker run --rm -i -P brainstorm");
	//		
	//		Instant big = Instant.ofEpochSecond(1000000000, 100);
	//		Instant small = Instant.ofEpochSecond(0, 200);
	//		Instant difference = big.minusSeconds(small.getEpochSecond()).minusNanos(small.getNano());
	//		long nanos1 = getElapsedNanos(small, big);
	//		Duration dur = Duration.between(small, big);
	//		long nanos = Util.getNanos(dur);
	//		int dummy = 1;
	//		
	//	}


}
