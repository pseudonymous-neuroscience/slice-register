package com.mridb.sliceRegister;

import java.io.File;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
//import java.lang.ProcessHandle;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.gson.Gson;

import com.mridb.sliceRegister.clientInterface.gui.Time;

// executes a process while monitoring its resource usage
public class Executor {

	// stdout and stderr both use the filesystem. the files can be moved to a database to reduce the use of individual files
	private static boolean saveCommand = true;
	private static boolean moveToDatabaseOnCompletion = false; // todo: implement

	private transient String creationTime = null;
	private transient File tempFolder = null;
	public enum ExecutionStatus{UNDEFINED, CREATED, RUNNING, INTERRUPTED, ERROR, COMPLETED}

	private ExecutionStatus status = ExecutionStatus.UNDEFINED;
	private static int childTimeoutAfterStopRequest = 1000;
	private static ExecutorService threadPool;

	private UUID uuid;
	private String[] command;
	private transient File workingDirectory;
	private Map<String, String> env;
	private String dir;
	private Time startTime;
	private Time endTime;
//	private Instant startTime;
//	private Instant endTime;
	private int errorCode;
	private transient Process process;
	private long processId;
	private transient Exception error;
	//	private transient ProcessHandle processHandle;
	private transient Runtime runtime;
//	private transient File stdOutFolder;
//	private transient File stdErrFolder;
	private transient File stdOut;
	private transient File stdErr;
	private transient File resourceUse;
	private String stdOutPath;
	private String stdErrPath;
	private String outputText;
	private String errorText;
	private String resourceUsePath;
	private Integer processorCount = null;
	private boolean interrupted = false;
	private transient Future<Executor> futureResult;
//	private transient boolean logResourceUsage = false; // 
//	private transient int logResourceIntervalMilliseconds = 1000; // 
//	private transient Thread executionThread;


	public static String executeSyncronously(String spaceDelimitedCommandAndArgs) throws Exception {
		Executor exec = new Executor();
		exec.setCommand(spaceDelimitedCommandAndArgs);
		exec.executeSynchronously();
		String result = exec.getRelevantText();
		return result;
	}
	public static String executeSyncronously(String[] commandAndArgs) throws Exception {
		Executor exec = new Executor();
		exec.setCommand(commandAndArgs);
		exec.executeSynchronously();
		String result = exec.getRelevantText();
		return result;
	}
	public static String executeSyncronouslyWithoutLogging(String[] commandAndArgs) throws Exception {
		Executor exec = new Executor();
		exec.setCommand(commandAndArgs);
		exec.saveCommand = false;
		exec.executeSynchronously();
		String result = exec.getRelevantText();
		return result;
	}

	public static Executor executeAsynchronously(String shellInput) throws Exception {
		Executor exec = new Executor();
		exec.setCommand(shellInput);
		exec.executeAsynchronously();
		return exec;
	}
	public static Executor[] executePoolSynchronously(List<String> commands, int maxConcurrent) throws Exception {
		String[] cArray = new String[commands.size()];
		for(int i = 0; i < commands.size(); i++) {
			cArray[i] = commands.get(i);
		}
		return executePoolSynchronously(cArray, maxConcurrent);
	}

	public static Executor[] executePoolSynchronously(String[] commands, int maxConcurrent) throws Exception {
		
		// set up executors
		Executor[] result = new Executor[commands.length];
		boolean[] isFinished = new boolean[commands.length];
		boolean[] isStarted = new boolean[commands.length];
		int numberRunning = 0;
		int numberFinished = 0;
		for(int i = 0; i < commands.length; i++) {
			result[i] = new Executor();
			result[i].setCommand(commands[i]);
			isFinished[i] = false;
			isStarted[i] = false;
			if(i < maxConcurrent) {
				isStarted[i] = true;
				numberRunning++;
				System.out.println(commands[i]);
				result[i].executeAsynchronously();
			}
		}
		
		// loop until all finished
		boolean allFinished = false;
		while(!allFinished) {
			for(int i = 0; i < commands.length; i++) {
				if(!isFinished[i] && isStarted[i]) {
					if(result[i].isFinished()) {
						isFinished[i] = true;
						numberRunning--;
						numberFinished++;
						System.out.println(String.format("%d commands left in queue", commands.length - numberFinished));
						if(numberFinished >= commands.length) {
							allFinished = true;
						}
					}
				} else if(!isStarted[i]) {
					if(numberRunning < maxConcurrent) {
						isStarted[i] = true;
						numberRunning++;
						System.out.println(commands[i]);
						result[i].executeAsynchronously();
					}
				}
			}
			Thread.sleep(100);
		}
		return result;
	}

	public static Executor executeAsynchronously(String[] commandAndArgs) throws Exception {
		Executor exec = new Executor();
		exec.setCommand(commandAndArgs);
		exec.executeAsynchronously();
		return exec;
	}


	
	public Executor() throws IOException {
		this(getTempUniqueFolder());
	}
	
	public Executor(File workingDir) throws IOException {

		this.uuid = UUID.randomUUID();
		this.workingDirectory = workingDir;		
		Util.ensureDirectoryExists(workingDirectory);
		String folder = this.workingDirectory.getCanonicalPath();
		this.stdOutPath = Paths.get(folder, "stdout.txt").toString();
		this.stdErrPath = Paths.get(folder, "stderr.txt").toString();
		this.stdOut = new File(stdOutPath);
		this.stdErr = new File(stdErrPath);
		
		this.env = new HashMap<String, String>();
		if(threadPool == null) {
			threadPool = Executors.newCachedThreadPool();			
		}
		this.status = ExecutionStatus.CREATED;
		
	}

//	public void setResourceUseLogging(boolean value) {
//		this.logResourceUsage = value;
//	}


	// not sure if you can call this 
	public void stopExecution() throws InterruptedException{
		stopExecution(null);
	}
	public void stopExecution(Integer millisecondTimeout) throws InterruptedException{
		if(millisecondTimeout == null) {
			millisecondTimeout = Executor.childTimeoutAfterStopRequest;			
		}

		boolean stopNeeded = false;
		if(isPending())
		{
			stopNeeded = true;
			if(millisecondTimeout > 0) {
				Thread.sleep(millisecondTimeout, 0);
				if(!isPending()) {
					stopNeeded = false;
				}
			}
		}
		
		if(stopNeeded) {
			this.status = ExecutionStatus.INTERRUPTED;
			if(this.futureResult != null) {
				this.futureResult.cancel(true);
			}
			this.endTime = new Time();
			if(this.process != null) {
				this.process.destroy();				
			}
		}



	}
	private boolean isPending() {
		return (     
				this.status == ExecutionStatus.UNDEFINED ||
				this.status == ExecutionStatus.CREATED ||
				this.status == ExecutionStatus.RUNNING
				);
	}

	// todo: add locking (static) data structure (e.g. ArrayList of paths used this session) that guarantees uniqueness
	private static File getTempUniqueFolder() throws IOException {

		File tempFolder = null;
		//		if(this.creationTime == null) {						
		boolean isUnique = false;
		while(isUnique == false) {
			//				this.creationTime = Util.dateStringWithNanos();
			String time = Util.dateStringWithNanos();
			//				this.tempFolder = new File(getTempFolder(this.creationTime));
			tempFolder = new File(getTempFolder(time));
			//				if(this.tempFolder.exists() == false) {
			if(tempFolder.exists() == false) {
				isUnique = true;
			}
		}
		//		}
		return tempFolder;
	}
	public static String getExecutionFolder() throws IOException {
		String root = AppFolders.getProjectRoot();
		return Util.getCanonicalPath(false, root, "data", "execution");
	}

	private static String getTempFolder(String creationTime) throws IOException {
		String root = AppFolders.getProjectRoot();
		return Util.getCanonicalPath(false, root, "data", "execution", creationTime);
	}

	private static File createFile(File folder, String fileName) throws IOException {
		Util.ensureDirectoryExists(folder);
		File result = null;
		String filePath = Util.getCanonicalPath(folder.getCanonicalPath(), fileName);
		result = new File(filePath);
		result.createNewFile();
		return result;
	}


	
	public int executeSynchronously() throws IOException {
		
		File workingDirectory = this.stdOut.getParentFile();
		String folderText = workingDirectory.getCanonicalPath();
		
		ProcessBuilder builder = createRuntime();
		this.startTime = new Time();
		this.process = builder.start();
		// this exception doesn't even get caught. probably a problem with jdk 8
		//		try {
		//			this.processId = this.process.pid(); 
		//		}
		//		catch(Exception ex) {
		//			// some runtimes don't define Process.pid();
		//			this.processId = -1;
		//		}
		this.status = ExecutionStatus.RUNNING;
		
		if(saveCommand) {
			String startedPath = Util.getCanonicalPath(folderText, "started.json");
			String startedJson = this.toString();
			Util.save(startedPath, startedJson);
		}
		//		if(this.logResourceUsage) {
		//			String resourcePath1 = Util.getCanonicalPath(folderText, "memory.txt");
		//			this.process
		//			String resourcePath2 = this.resourceUsePath;
		//			int dummy = 1;
		//		} else {
		try {
			this.errorCode = this.process.waitFor();
			this.status = ExecutionStatus.COMPLETED;
		} catch (InterruptedException e) {
			this.status = ExecutionStatus.INTERRUPTED;
			if(this.errorCode == 0) {
				this.errorCode = -7777;
			}
		}
		//		}
		this.endTime = new Time();
		this.errorText = this.getErrorText();
		this.outputText = this.getOutputText();
		if(saveCommand) {
			String completedPath = Util.getCanonicalPath(folderText, "completed.json");
			String commandJson = this.toString();
			Util.save(completedPath, commandJson);
		}
		return this.errorCode;
	}
	
	public int getErrorCode() {
		return this.errorCode;
	}

	public void setCommand(String[] commandAndArgs) {
		this.command = commandAndArgs;
	}

	
	public void setCommand(String shellInput) {
		// split into args using space as a delimeter
		String[] commandAndArgs = shellInput.split(" ");
		
		// drop empty arguments
		ArrayList<String> commandList = new ArrayList<String>();
		for(int i = 0;i < commandAndArgs.length; i++) {
			String piece = commandAndArgs[i];
			if(piece.length() > 0) {
				commandList.add(piece);
			}
		}
		String[] trimmed = commandList.toArray(new String[commandList.size()]);
		this.command = trimmed;
	}
	
	public String[] getCommandParts() {
		return this.command;
	}
	public String getCommand() {
		StringBuilder sb = new StringBuilder();
		int maxLength = command.length - 1;
		for(int i = 0; i < command.length; i++) {
			sb.append(command[i]);
			if(i < maxLength) {
				sb.append(" ");
			}
		}
		return sb.toString();
	}


	public Future<DispatchedCommand> executeAsynchronously() {
		CompletableFuture<DispatchedCommand> commandPromise = new CompletableFuture<DispatchedCommand>();
		Executor thisExec = this;

		threadPool.submit((Runnable) ()->{
			Thread currentThread = Thread.currentThread();

			DispatchedCommand command = new DispatchedCommand(thisExec, currentThread);
			try {
				thisExec.executeSynchronously();
				commandPromise.complete(command);
			} catch (IOException e) {
				Util.LogError(e);
				e.printStackTrace();
				commandPromise.cancel(false);
				
			}catch (Exception otherException) {
				Util.LogError(otherException);
				otherException.printStackTrace();
				commandPromise.cancel(false);
			}
			return;

		});

		return commandPromise;
	}

	private static String cocantenateStringArray(String[] input) {
		StringBuilder sb = new StringBuilder();
		int lastIndex = input.length - 1;
		for(int i = 0; i < input.length; i++) {
			sb.append(input[i]);
			if(i < lastIndex) {
				sb.append(" ");
			}
		}
		return sb.toString();
	}


	private ProcessBuilder createRuntime() {

		// prepare the process via process builder
		ProcessBuilder builder = new ProcessBuilder();
		builder.redirectError(this.stdErr);
		builder.redirectOutput(this.stdOut);
		builder.command(this.command);
		builder.directory(workingDirectory);
		builder.environment().putAll(env);
		return builder;
	}

	public CharSource getOutput() {
		try {
			return Files.asCharSource(this.stdOut, StandardCharsets.UTF_8);		
		}catch(NullPointerException e) {
			return null;
		}
	}
	public CharSource getError() {
		try {
			return Files.asCharSource(this.stdErr, StandardCharsets.UTF_8);		
		}catch(NullPointerException e) {
			return null;
		}
	}
	private String tryToReadFile(CharSource input) {
		if(input == null) {
			return null;			
		}
		try {
			return input.read();
		} catch (IOException e) {
			return null;
		}		
	}
	public static class DispatchedCommand {
		Executor command;
		Thread thread;
		
		public DispatchedCommand(Executor exec, Thread thr){
			this.command = exec;
			this.thread = thr;
		}
	}
	public static class EarlyThreadStopHandler implements UncaughtExceptionHandler{

		public EarlyThreadStopHandler() {
			
		}
		@Override
		public void uncaughtException(Thread arg0, Throwable arg1) {
			String threadName = arg0.getName();
			String a = Util.newGson().toJson(arg1);
			System.out.println("thread named ("+threadName+") terminated early");
			
		}
		
	}
	public ExecutionStatus getStatus() {
		return this.status;
	}
	public String getOutputFilePath() {
		return this.stdOutPath;
	}
	public String getErrorFilePath() {
		return this.stdErrPath;
	}
	
	public String getOutputText() throws IOException {

		return tryToReadFile(this.getOutput());		
	}
	public String getErrorText() throws IOException {
		return tryToReadFile(this.getError());		
	}
	public String toString() {
		Gson gson = Util.newGson();
		return gson.toJson(this);
	}

	public String getRelevantText() throws Exception {
		if(this.errorCode == 0) {
			return this.getOutputText();
		}else {
			throw new Exception(this.getErrorText());			
		}		
	}
	public File getWorkingDirectory() {
		return this.workingDirectory;
	}

	public String getInputCommand() {
		return Executor.cocantenateStringArray(command);
	}
	
	public boolean isFinished() {
		if(this.status == ExecutionStatus.COMPLETED) {
			return true;
		} else if(this.status == ExecutionStatus.ERROR) {
			return true;
		} else if(this.status == ExecutionStatus.INTERRUPTED) {
			return true;
		}
		return false;
	}


	public static Executor prepareOctaveScript(String octaveCommand) throws IOException {
		Executor executor = new Executor();
		File folder = executor.workingDirectory;
//		File folder = executor.getTempUniqueFolder();
		String scriptPath = Util.getCanonicalPath(folder.toString(), "command.m");
		Util.save(scriptPath, octaveCommand);
		String[] shellCommand = new String[] {"octave", "--no-gui", "-p", folder.toString(), "--eval", "command"};
		executor.command = shellCommand;
		return executor;
	}

	public static String evaluateOctaveCommand(String octaveCommand) throws Exception {
		return evaluateOctaveCommand(octaveCommand, false);
	}
	public static String evaluateOctaveCommand(String octaveCommand, boolean logRamUse) throws Exception {
	
		Executor exec = Executor.prepareOctaveScript(octaveCommand);
//		exec.setResourceUseLogging(logRamUse);
		int resultCode = exec.executeSynchronously();
		String resultText = exec.getRelevantText();
		resultText = Util.removeAns(resultText);
		return resultText;
	}

	// quick tests


	public static void main(String[] args) throws Exception {
		
		String noVncCommand = "docker run --rm -i -P novnc";
		Executor forkExec = Util.fork(noVncCommand);
		Thread.sleep(1000);
		
		forkExec.stopExecution(null);
		
		
		int dummyVnc = 1;
		
		Executor exec = new Executor();
		String[] commandArray = new String[] {"echo", "hello"};
		System.out.println("executing commands in queue");
		
		Executor sleepScript = Executor.prepareOctaveScript("javaMethod('sleep', 'System.Threading.Thread', 1000); 2 + i");
		for(int i = 0; i < 100; i++) {
			sleepScript.executeAsynchronously();
		}

		String five = Executor.evaluateOctaveCommand("2 + 3", false);
		String seven = Executor.evaluateOctaveCommand("3 + 4", false);
		



	}
	
	@FunctionalInterface
	public interface Callback {
		String run(Object input);
	}

	public String addCallback(Callback callback, Object info) {
		String result = callback.run(info);
		return result;
	}

		
	

}

