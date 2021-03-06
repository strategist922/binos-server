package com.klose.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.io.IOUtils;

import com.klose.Slave.SlaveArgsParser;
import com.klose.Slave.SlaveExecutor;
import com.transformer.compiler.JobProperties;

/**
 * The class is learned from Hadoop-0.21.0 (org.apache.hadoop.util.RunJar).
 * RunJar is used for running class in a jar. 
 * @author Bing Jiang
 *
 */
public class RunJar {
	 
	private static final Logger LOG = Logger.getLogger(RunJar.class.getName());
	/** Pattern that matches any string */
	  public static final Pattern MATCH_ANY = Pattern.compile(".*");
	
	/**
	   * Unpack a jar file into a directory.
	   *
	   * This version unpacks all files inside the jar regardless of filename.
	   */
	  public static void unJar(File jarFile, File toDir) throws IOException {
	    unJar(jarFile, toDir, MATCH_ANY);
	  }
	
	/**
	   * Unpack matching files from a jar. Entries inside the jar that do
	   * not match the given pattern will be skipped.
	   *
	   * @param jarFile the .jar file to unpack
	   * @param toDir the destination directory into which to unpack the jar
	   * @param unpackRegex the pattern to match jar entries against
	   */
	  public static void unJar(File jarFile, File toDir, Pattern unpackRegex)
	    throws IOException {
	    JarFile jar = new JarFile(jarFile);
	    try {
	      Enumeration<JarEntry> entries = jar.entries();
	      while (entries.hasMoreElements()) {
	        JarEntry entry = (JarEntry)entries.nextElement();
	        if (!entry.isDirectory() &&
	            unpackRegex.matcher(entry.getName()).matches()) {
	          InputStream in = jar.getInputStream(entry);
	          try {
	            File file = new File(toDir, entry.getName());
	            ensureDirectory(file.getParentFile());
	            OutputStream out = new FileOutputStream(file);
	            try {
	              IOUtils.copyBytes(in, out, 8192, false);
	            } finally {
	              out.close();
	            }
	          } finally {
	            in.close();
	          }
	        }
	      }
	    } finally {
	      jar.close();
	    }
	  }
	  
	  /**
	   * Ensure the existence of a given directory.
	   *
	   * @throws IOException if it cannot be created and does not already exist
	   */
	  public static void ensureDirectory(File dir) throws IOException {
	    if (!dir.mkdirs() && !dir.isDirectory()) {
	      throw new IOException("Mkdirs failed to create " +
	                            dir.toString());
	    }
	  }
	  /**
	   * Execute the Class that implements com.transformer.compiler.Operation
	   * 
	   * @param JobProperties
	   * @param args
	   * @throws Throwable
	   */
	  public static void executeOperationJar(JobProperties pros, String[] args) throws Throwable {
		 if(args.length < 4) {
			 LOG.log(Level.WARNING, "args lack!");
			 return;
		 }
		 int firstArgs = 0;
		 String jarPath = args[firstArgs++];
		 File jarFile = new File(jarPath);
		 String operationClass = args[firstArgs++];
		 File tmpDir = new File(SlaveArgsParser.getWorkDir());
		  ensureDirectory(tmpDir);
		 final File workDir = File.createTempFile("Transformer-unjar", "", tmpDir);
		if (!workDir.delete()) {
			System.err.println("Delete failed for " + workDir);
			System.exit(-1);
		}
		ensureDirectory(workDir);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {

					try {
						FileUtil.fullyDelete(workDir);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				
			}
		});

		unJar(jarFile, workDir);

		ArrayList<URL> classPath = new ArrayList<URL>();
		classPath.add(new File(workDir + "/").toURI().toURL());
		classPath.add(jarFile.toURI().toURL());
		classPath.add(new File(workDir, "classes/").toURI().toURL());
		File[] libs = new File(workDir, "lib").listFiles();
		if (libs != null) {
			for (int i = 0; i < libs.length; i++) {
				classPath.add(libs[i].toURI().toURL());
			}
		}
		ClassLoader loader = new URLClassLoader(classPath.toArray(new URL[0]));
		Class cls = loader.loadClass(operationClass);
		Thread.currentThread().setContextClassLoader(loader);
		Method m = cls.getMethod("operate", JobProperties.class, String[].class, String[].class);
		int inputNum = Integer.parseInt(args[firstArgs++].split(" ")[1]);
		int outputNum = Integer.parseInt(args[firstArgs++].split(" ")[1]);
		String [] inputPath = null;
		String [] outputPath = null;
		if(inputNum != 0) {
			inputPath = args[firstArgs++].split(" ");
		}
		if(outputNum != 0) {
			outputPath = args[firstArgs].split(" ");
		}
		m.invoke(cls.newInstance(), pros, inputPath, 
					outputPath);
		try {
			FileUtil.fullyDelete(workDir);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	  }
	  
	  
	  /** Run a task jar.  If the main class is not in the jar's manifest,
	   * then it must be provided on the command line. */
	  public static void run(String[] args) throws Throwable {
	    String usage = "RunJar jarFile [mainClass] args...";

	    if (args.length < 1) {
	      System.err.println(usage);
	      System.exit(-1);
	    }

	    int firstArg = 0;
	    String fileName = args[firstArg++];
	    File file = new File(fileName);
	    String mainClassName = null;

	    JarFile jarFile;
	    try {
	      jarFile = new JarFile(fileName);
	    } catch(IOException io) {
	      throw new IOException("Error opening task jar: " + fileName)
	        .initCause(io);
	    }

	    Manifest manifest = jarFile.getManifest();
	    if (manifest != null) {
	      mainClassName = manifest.getMainAttributes().getValue("Main-Class");
	    }
	    jarFile.close();

	    if (mainClassName == null) {
	      if (args.length < 2) {
	        System.err.println(usage);
	        System.exit(-1);
	      }
	      mainClassName = args[firstArg++];
	    }
	    mainClassName = mainClassName.replaceAll("/", ".");

	    File tmpDir = new File(SlaveArgsParser.getWorkDir());
	    ensureDirectory(tmpDir);

	    final File workDir = File.createTempFile("Transformer-unjar", "", tmpDir);
	    if (!workDir.delete()) {
	      System.err.println("Delete failed for " + workDir);
	      System.exit(-1);
	    }
	    ensureDirectory(workDir);

	    Runtime.getRuntime().addShutdownHook(new Thread() {
	        public void run() {
	         
	            try {
					FileUtil.fullyDelete(workDir);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	         
	        }
	      });

	    unJar(file, workDir);

	    ArrayList<URL> classPath = new ArrayList<URL>();
	    classPath.add(new File(workDir+"/").toURI().toURL());
	    classPath.add(file.toURI().toURL());
	    classPath.add(new File(workDir, "classes/").toURI().toURL());
	    File[] libs = new File(workDir, "lib").listFiles();
	    if (libs != null) {
	      for (int i = 0; i < libs.length; i++) {
	        classPath.add(libs[i].toURI().toURL());
	      }
	    }
	    
	    ClassLoader loader =
	      new URLClassLoader(classPath.toArray(new URL[0]));

	    Thread.currentThread().setContextClassLoader(loader);
	    Class<?> mainClass = Class.forName(mainClassName, true, loader);
	    Method main = mainClass.getMethod("main", new Class[] {
	      Array.newInstance(String.class, 0).getClass()
	    });
	    String[] newArgs = Arrays.asList(args)
	      .subList(firstArg, args.length).toArray(new String[0]);
	    System.out.println(newArgs.toString());
	    try {
	      main.invoke(null, new Object[] { newArgs });
	    } catch (InvocationTargetException e) {
	      throw e.getTargetException();
	    }
	  }
}
