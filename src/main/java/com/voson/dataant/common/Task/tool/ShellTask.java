/**
 * This file created at 2013-6-8.
 *
 * Copyright (c) 2002-2013 Bingosoft, Inc. All rights reserved.
 */
package com.voson.dataant.common.Task.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voson.dataant.common.Task.Task;

/**
 * <code>{@link ShellTask}</code>
 *
 *
 * @author litianwang
 */
public class ShellTask implements Task {
	private static Logger log = LoggerFactory.getLogger(ShellTask.class);
	private String shellFile;
	private String workDir;
	private long lineNum = 0;
	
	public ShellTask(String shellFile, String workDir){
		this.shellFile = shellFile;
		this.workDir =workDir;
		File file = new File(workDir);
		if (!file.exists()) {
			file.mkdirs();
		}
	}

	/* (non-Javadoc)
	 * @see com.voson.common.Task.Task#cancel()
	 */
	@Override
	public void cancel() {

	}

	/* (non-Javadoc)
	 * @see com.voson.common.Task.Task#isCanceled()
	 */
	@Override
	public boolean isCanceled() {
		return false;
	}

	/* (non-Javadoc)
	 * @see com.voson.common.Task.Task#run()
	 */
	@Override
	public Integer run() throws Exception {
		String os=System.getProperties().getProperty("os.name");
		if(os!=null && (os.startsWith("win") || os.startsWith("Win"))){
			// windows 系统开发测试，不运行
			// TODO:delete
			return 0;
		} else {
			
		}
		String command = "sh " + shellFile;
		Process process;
		ProcessBuilder builder = new ProcessBuilder(partitionCommandLine(command));
		builder.directory(new File(workDir));
		// builder.environment().putAll(envMap);
		process=builder.start();
		final InputStream inputStream = process.getInputStream();
		final InputStream errorStream = process.getErrorStream();
		
		String threadName= "task=runDataAntShell";
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try{
					BufferedReader reader=new BufferedReader(new InputStreamReader(inputStream));
					String line;
					while((line=reader.readLine())!=null){
						lineNum ++;
					}
				}catch(Exception e){
					log.error("接收日志出错，推出日志接收");
				}
			}
		},threadName).start();
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					BufferedReader reader=new BufferedReader(new InputStreamReader(errorStream));
					String line;
					while((line=reader.readLine())!=null){
					}
				} catch (Exception e) {
						log.error("接收日志出错，推出日志接收");
					}
			}
		},threadName).start();
		int exitCode = -999;
		try {
			exitCode = process.waitFor();
		} catch (InterruptedException e) {
			// log(e);
		} finally{
			process=null;
		}
		if(exitCode!=0){
			return exitCode;
		}
		return 0;
	}
	

	
	public static String[] partitionCommandLine(String command) {
		
		ArrayList<String> commands = new ArrayList<String>();
		
		String os=System.getProperties().getProperty("os.name");
		if(os!=null && (os.startsWith("win") || os.startsWith("Win"))){
			commands.add("CMD.EXE");
			commands.add("/C");
			commands.add(command);
		}else{
			int index = 0;

	        StringBuffer buffer = new StringBuffer(command.length());

	        boolean isApos = false;
	        boolean isQuote = false;
	        while(index < command.length()) {
	            char c = command.charAt(index);

	            switch(c) {
	                case ' ':
	                    if(!isQuote && !isApos) {
	                        String arg = buffer.toString();
	                        buffer = new StringBuffer(command.length() - index);
	                        if(arg.length() > 0) {
	                            commands.add(arg);
	                        }
	                    } else {
	                        buffer.append(c);
	                    }
	                    break;
	                case '\'':
	                    if(!isQuote) {
	                        isApos = !isApos;
	                    } else {
	                        buffer.append(c);
	                    }
	                    break;
	                case '"':
	                    if(!isApos) {
	                        isQuote = !isQuote;
	                    } else {
	                        buffer.append(c);
	                    }
	                    break;
	                default:
	                    buffer.append(c);
	            }

	            index++;
	        }

	        if(buffer.length() > 0) {
	            String arg = buffer.toString();
	            commands.add(arg);
	        }
		}
        return commands.toArray(new String[commands.size()]);
	}
}
