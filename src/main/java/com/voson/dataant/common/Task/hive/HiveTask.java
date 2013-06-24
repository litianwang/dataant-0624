/**
 * This file created at 2013-6-8.
 *
 * Copyright (c) 2002-2013 Bingosoft, Inc. All rights reserved.
 */
package com.voson.dataant.common.Task.hive;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.voson.dataant.common.Task.Task;

/**
 * <code>{@link HiveTask}</code>
 *
 * TODO : document me
 *
 * @author litianwang
 */
public class HiveTask implements Task {
	private static Logger log = LoggerFactory.getLogger(HiveTask.class);
	private String preScript;
	private String coreScript;
	private String postScript;
	private String taskId;
	private String workDir;
	private long lineNum = 0;
	private String resultDir;
	// 最大输出数量：默认1000万
	private long maxCnt = 10000000;
	private  OutputStreamWriter hiveLogtWriter = null;
	
	public HiveTask(String preScript, String coreScript, String postScript, String taskId, String workDir, String resultDir){
		this.preScript = preScript;
		this.coreScript = coreScript;
		this.postScript = postScript;
		this.taskId =taskId;
		this.workDir =workDir;
		this.resultDir = resultDir;
		File file = new File(workDir);
		if (!file.exists()) {
			file.mkdirs();
		}
		file = new File(resultDir);
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
		
		File hiveLogF = new File(workDir + File.separator+ taskId + ".hive.log");
		if (!hiveLogF.exists()) {
			hiveLogF.createNewFile();
		}
		hiveLogtWriter = new OutputStreamWriter(new FileOutputStream(hiveLogF),Charset.forName("utf-8"));
		int exitCode = 0;
		
		try{
		
			if(0 == exitCode && StringUtils.isNotBlank(preScript)){
				File f = new File(workDir + File.separator + taskId + ".pre.hive");
				if (!f.exists()) {
					f.createNewFile();
				}
				OutputStreamWriter writer = null;
				try {
					writer = new OutputStreamWriter(new FileOutputStream(f),
							Charset.forName("utf-8"));
					writer.write(preScript.replaceAll("^--.*", ""));
				} catch (Exception e) {
					return -1;
				} finally {
					IOUtils.closeQuietly(writer);
				}
				StringBuffer sb = new StringBuffer();
				sb.append("hive");
				// 引入常用udf函数
				sb.append(" -f ").append(f.getAbsolutePath());
				// 执行shell
				exitCode = this.preAndPostRun(sb.toString());
			}
			if(0 == exitCode  && StringUtils.isNotBlank(coreScript)){
				File f = new File(workDir + File.separator + taskId + ".core.hive");
				if (!f.exists()) {
					f.createNewFile();
				}
				OutputStreamWriter writer = null;
				try {
					writer = new OutputStreamWriter(new FileOutputStream(f),
							Charset.forName("utf-8"));
					writer.write(coreScript.replaceAll("^--.*", ""));
				} catch (Exception e) {
					return -1;
				} finally {
					IOUtils.closeQuietly(writer);
				}
				StringBuffer sb = new StringBuffer();
				sb.append("hive");
				// 引入常用udf函数
				sb.append(" -f ").append(f.getAbsolutePath());
				// 执行shell
				exitCode = this.coreRun(sb.toString());
			}
			if(0 == exitCode && StringUtils.isNotBlank(postScript)){
				File f = new File(workDir + File.separator + taskId + ".post.hive");
				if (!f.exists()) {
					f.createNewFile();
				}
				OutputStreamWriter writer = null;
				try {
					writer = new OutputStreamWriter(new FileOutputStream(f),
							Charset.forName("utf-8"));
					writer.write(postScript.replaceAll("^--.*", ""));
				} catch (Exception e) {
					return -1;
				} finally {
					IOUtils.closeQuietly(writer);
				}
				StringBuffer sb = new StringBuffer();
				sb.append("hive");
				// 引入常用udf函数
				sb.append(" -f ").append(f.getAbsolutePath());
				// 执行shell
				exitCode = this.preAndPostRun(sb.toString());
			}
		} finally {
			IOUtils.closeQuietly(hiveLogtWriter);
		}
		
		return exitCode;
	}
	
	/**
	 * @param s
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private int preAndPostRun(String s) throws IOException, FileNotFoundException {
		String os=System.getProperties().getProperty("os.name");
		if(os!=null && (os.startsWith("win") || os.startsWith("Win"))){
			// windows 系统开发测试，不运行
			// TODO:delete
			return 0;
		} else {
			
		}
		Process process;
		ProcessBuilder builder = new ProcessBuilder(partitionCommandLine(s));
		builder.directory(new File(workDir));
		// builder.environment().putAll(envMap);
		process=builder.start();
		final InputStream inputStream = process.getInputStream();
		final InputStream errorStream = process.getErrorStream();
		
		String threadName= "task=" + taskId;
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try{
					BufferedReader reader=new BufferedReader(new InputStreamReader(inputStream));
					String line;
					while((line=reader.readLine())!=null){
						hiveLogtWriter.write(line + "\n");
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
						hiveLogtWriter.write(line + "\n");
						
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

	/**
	 * @param s
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private int coreRun(String s) throws IOException, FileNotFoundException {
		String os=System.getProperties().getProperty("os.name");
		if(os!=null && (os.startsWith("win") || os.startsWith("Win"))){
			// windows 系统开发测试，不运行
			// TODO:delete
			return 0;
		} else {
			
		}
		Process process;
		ProcessBuilder builder = new ProcessBuilder(partitionCommandLine(s));
		builder.directory(new File(workDir));
		// builder.environment().putAll(envMap);
		process=builder.start();
		final InputStream inputStream = process.getInputStream();
		final InputStream errorStream = process.getErrorStream();
		
		String threadName= "task=" + taskId;
		File hiveResultF = new File(resultDir + File.separator+ taskId + ".tmp");
		if (!hiveResultF.exists()) {
			hiveResultF.createNewFile();
		}
		final OutputStreamWriter HiveQueryresultWriter = new OutputStreamWriter(new FileOutputStream(hiveResultF),Charset.forName("utf-8"));
		lineNum = 0;
		new Thread(new Runnable() {
			@Override
			public void run() {
				try{
					BufferedReader reader=new BufferedReader(new InputStreamReader(inputStream));
					String line;
					while((line=reader.readLine())!=null){
						lineNum++;
						// 最大输出数量
						if(lineNum <=maxCnt){
							HiveQueryresultWriter.write(line + "\n");
						}
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
						hiveLogtWriter.write(line + "\n");
						
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
			IOUtils.closeQuietly(HiveQueryresultWriter);
			process=null;
		}
		if(exitCode!=0){
			return exitCode;
		} else {
			// 重命名结果文件
			if(null != hiveResultF && hiveResultF.exists()){
				if(lineNum == 0){
					hiveResultF.delete();
				} else {
					hiveResultF.renameTo(new File(resultDir + File.separator+ taskId + ".txt"));
				}
			}
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

	public long getMaxCnt() {
		return maxCnt;
	}
	
	public void setMaxCnt(long maxCnt) {
		this.maxCnt = maxCnt;
	}

	public long getLineNum() {
		return lineNum;
	}

	public void setLineNum(long lineNum) {
		this.lineNum = lineNum;
	}

}
