package gate.cloud.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.JarURLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * Command-line driver for GCP.
 */
public class Main {
  
  protected static void printUsageMessage() {
    System.out.println(
      "Usage: java -jar gcp-cli.jar [optionalArguments] batch.xml\n" +
      "       java -jar gcp-cli.jar [optionalArguments] -d workingDir\n" +
      "\n" +
      "Optional arguments:\n" +
      "  -m maxMem    : maximum Java heap size, passed as -Xmx (default 12G)\n" +
      "  -t threads   : number of parallel processing threads (default 6)\n" +
      "  -Dname=value : Java system property settings, passed directly to the VM\n" +
      "\n" +
      "If -d is specified, the following parameter is taken to refer to a \"working\"\n" +
      "directory.  This directory is expected to contain a subdirectory named \"in\".\n" +
      "Batch specification files dropped in the in directory will be processed one\n" +
      "at a time, with the standard output and error piped to a file named for the\n" +
      "batch in a \"logs\" directory under the working directory.  Successful batches\n" +
      "will be moved to a directory named \"out\" under the working directory,\n" +
      "batches that could not be processed are moved to a directory named \"err\".\n" +
      "Note that additional batches can be added to the input directory at any time\n" +
      "- whenever a batch finishes GCP will look in the input directory to see if\n" +
      "any more batches are available.  If the directory contains a file named\n" +
      "\"shutdown.gcp\", GCP will shut down at the end of the currently running\n" +
      "batch, or immediately if there are no batches running.\n" +
      "\n" +
      "If -d is not specified, then the single non-optional argument is interpreted\n" +
      "as a batch file, which is executed, after which the process ends.\n" +
      "\n" +
      "This script respects the JAVA_OPTS environment variable, any VM options\n" +
      "placed in this variable will be passed to the Java VM.\n"
    );
  }
  
  public static class TeeStreamGobbler extends Thread {
    private PrintWriter pw = null;
    private Process process;
    public TeeStreamGobbler(Process p, File f) throws IOException {
      this.process = p;
      if(f != null) {
        pw = new PrintWriter(new FileWriter(f));
      }
    }
    
    public void run() {
      InputStream in = process.getInputStream();
      BufferedReader br = new BufferedReader(new InputStreamReader(in));
      try {
        String line = null;
        while((line = br.readLine()) != null) {
          if(pw != null) {
            pw.println(line);
          }
          System.out.println(line);
        }
      } catch(IOException e) {
        System.out.println("Exception reading from GCP process");
        e.printStackTrace();
      }
      finally {
        if(pw != null) pw.close();
        try {
          br.close();
        }
        catch(IOException e) {
          System.out.println("Exception closing GCP process input stream");
          e.printStackTrace();
        }
      }
      
    }
  }
  
  /**
   * Install a signal handler that kills the spawned GCP process
   * on CTRL-C, rather than killing this process.  This is done using
   * reflection so as not to compile-time depend on sun.misc.  If
   * anything goes wrong when installing the signal handler we warn
   * and carry on without it.
   */
  protected static void installSignalHandler() {
    try {
      Class<?> signalClass = Class.forName("sun.misc.Signal");
      Class<?> sigHandlerClass = Class.forName("sun.misc.SignalHandler");
      Object sigInt = signalClass.getConstructor(String.class).newInstance("INT");
      
      class KillGcpHandler implements InvocationHandler {
        public Object oldHandler;
        
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
          if(method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
          }
          if("handle".equals(method.getName())) {
            if(currentGcpProcess != null) {
              System.out.println("Interrupting GCP process");
              currentGcpProcess.destroy();
            } else {
              method.invoke(oldHandler, args);
            }
          }
          return null;
        }
      }
      
      KillGcpHandler handler = new KillGcpHandler();
      Object handlerProxy = Proxy.newProxyInstance(Main.class.getClassLoader(), new Class<?>[] {sigHandlerClass}, handler);
      handler.oldHandler = signalClass.getMethod("handle", signalClass, sigHandlerClass).invoke(null, sigInt, handlerProxy);
    }
    catch(Exception e) {
      System.out.println("Warning: couldn't install signal handler: " + e);
    }
  }
  
  private static File gcpHome;

  private static String maxMem = "12G";
  private static String threads = "6";
  
  // non-null only in dir mode
  private static File workingDir = null;
  // non-null only in file mode
  private static File batchFile = null;
  
  private static ProcessBuilder processBuilder = null;
  
  private static Process currentGcpProcess = null;

  private static List<String> javaOpts = new ArrayList<String>();

  protected static void findGcpHome() throws Exception {
    String gcpHomeFromSysprop = System.getProperty("gcp.home");
    if(gcpHomeFromSysprop != null) {
      gcpHome = new File(gcpHomeFromSysprop);
      return;
    }

    String gcpHomeFromEnv = System.getenv("GCP_HOME");
    if(gcpHomeFromEnv != null) {
      gcpHome = new File(gcpHomeFromEnv);
      return;
    }

    URL thisClassUrl = Main.class.getResource("Main.class");
    if(thisClassUrl.getProtocol().equals("jar")) {
      JarURLConnection conn = (JarURLConnection)thisClassUrl.openConnection();
      String cliJar = conn.getJarFile().getName();
      gcpHome = new File(cliJar).getParentFile();
      return;
    }

    System.out.println("Could not determine location of GCP, please set either");
    System.out.println("- The system property gcp.home or");
    System.out.println("- The environment variable GCP_HOME");
    System.out.println("to point to the home directory of your GCP installation");
    System.exit(1);
  }

  protected static void parseArgs(String[] args) throws Exception {
    int i = 0;
    for(i = 0; i < args.length; i++) {
      if(!args[i].startsWith("-")) break;
      if("-m".equals(args[i])) {
        maxMem = args[++i];
      } else if("-t".equals(args[i])) {
        threads = args[++i];
      } else if("-d".equals(args[i])) {
        workingDir = new File(args[++i]);
      } else if(args[i].startsWith("-D")) {
        javaOpts.add(args[i]);
      } else {
        System.out.println("Unrecognised option " + args[i]);
        printUsageMessage();
        System.exit(1);
      }
    }
    
    if(workingDir == null) {
      // not dir mode, first non-option argument is batch file
      batchFile = new File(args[i]);
    }
  }
  
  protected static void prepareProcessBuilder() {
    List<String> cmdline = new ArrayList<String>();
    cmdline.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
    cmdline.add("-classpath");
    StringBuilder classpath = new StringBuilder();
    classpath.append(new File(gcpHome, "conf").getAbsolutePath());
    classpath.append(File.pathSeparator);
    classpath.append(new File(new File(gcpHome, "lib"), "*").getAbsolutePath());
    cmdline.add(classpath.toString());
    cmdline.add("-Xmx" + maxMem);
    cmdline.addAll(javaOpts);
    cmdline.add("-Dgcp.home=" + gcpHome.getAbsolutePath());
    cmdline.add("-Djava.protocol.handler.pkgs=gate.cloud.util.protocols");
    cmdline.add("gate.cloud.batch.BatchRunner");
    cmdline.add(threads);
    // this will be replaced with the right batch file path at run time
    cmdline.add("dummyBatchFileName.xml");
    processBuilder = new ProcessBuilder(cmdline);
    processBuilder.redirectErrorStream(true);
  }
  
  protected static int runGcp(File batchSpec, File outputFile) throws Exception {
    int returnCode = -1;
    try {
      List<String> cmdline = processBuilder.command();
      cmdline.set(cmdline.size() - 1, batchSpec.getAbsolutePath());
      currentGcpProcess = processBuilder.start();
      new TeeStreamGobbler(currentGcpProcess, outputFile).start();
      boolean gcpFinished = false;
      while(!gcpFinished) {
        try {
          returnCode = currentGcpProcess.waitFor();
          gcpFinished = true;
        }
        catch(InterruptedException e) {
          // do nothing, wait again
        }
      }
    } finally {
      currentGcpProcess = null;
    }
    return returnCode;
  }

  public static void main(String[] args) throws Exception {
    if(args.length == 0) {
      printUsageMessage();
      System.exit(1);
    }
    findGcpHome();
    String javaOptsEnv = System.getenv("JAVA_OPTS");
    if(javaOptsEnv != null) {
      javaOpts.addAll(Arrays.asList(javaOptsEnv.split("\\s+")));
    }
    parseArgs(args);
    prepareProcessBuilder();
    installSignalHandler();
    
    if(workingDir == null) {
      if(!batchFile.canRead()) {
        System.out.println("Specified batch file " + batchFile.getPath() + " not found or not readable");
        System.exit(1);
      }
      System.exit(runGcp(batchFile, null));
    } else {
      // dir mode
      if(!workingDir.canRead()) {
        System.out.println("Working directory " + workingDir.getPath() + " not found or not readable");
        System.exit(1);
      }
      File inDir = new File(workingDir, "in");
      if(!inDir.canRead() || !inDir.canWrite()) {
        System.out.println("Input directory " + inDir.getPath() + " not found or not accessible");
        System.exit(1);
      }
      
      File outDir = new File(workingDir, "out");
      outDir.mkdirs();
      File errDir = new File(workingDir, "err");
      errDir.mkdirs();
      File logDir = new File(workingDir, "logs");
      logDir.mkdirs();
      
      FilenameFilter xmlFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
          return name.endsWith(".xml");
        }
      };
      
      File shutdownFile = new File(inDir, "shutdown.gcp");

      while(!shutdownFile.exists()) {
        String[] batchFiles = inDir.list(xmlFilter);
        if(batchFiles.length == 0) {
          // nothing to do, wait ten seconds and then try again
          Thread.sleep(10000);
        } else {
          // find the first batch file in lexicographic order
          Arrays.sort(batchFiles);
          File theBatchFile = new File(inDir, batchFiles[0]);
          File theLogFile = new File(logDir, batchFiles[0] + ".log");
          // run GCP, move the batch file to out or err as appropriate
          if(runGcp(theBatchFile, theLogFile) == 0) {
            theBatchFile.renameTo(new File(outDir, batchFiles[0]));
          } else {
            theBatchFile.renameTo(new File(errDir, batchFiles[0]));
          }
        }
      }
      shutdownFile.delete();
    }
  }
}
