/*
*  BatchRunner.java
*  Copyright (c) 2007-2011, The University of Sheffield.
*
*  This file is part of GCP (see http://gate.ac.uk/), and is free
*  software, licenced under the GNU Affero General Public License,
*  Version 3, November 2007.
*
*
*  $Id: BatchRunner.java 20267 2017-09-30 13:34:59Z ian_roberts $ 
*/
package gate.cloud.batch;

import gate.CorpusController;
import gate.Gate;
import gate.cloud.batch.BatchJobData.JobState;
import gate.cloud.batch.ProcessResult.ReturnCode;
import gate.cloud.io.DocumentEnumerator;
import gate.cloud.io.IOConstants;
import gate.cloud.io.InputHandler;
import gate.cloud.io.OutputHandler;
import gate.cloud.io.StreamingInputHandler;
import gate.cloud.util.CLibrary;
import gate.cloud.util.Tools;
import gate.cloud.util.XMLBatchParser;
import gate.creole.Plugin;
import gate.creole.ResourceInstantiationException;
import gate.util.GateException;
import gate.util.persistence.PersistenceManager;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.JMException;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import com.sun.jna.Platform;
import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import gate.cloud.io.ListDocumentEnumerator;
import static gate.cloud.io.IOConstants.PARAM_COMPRESSION;
import static gate.cloud.io.IOConstants.PARAM_DOCUMENT_ROOT;
import static gate.cloud.io.IOConstants.PARAM_ENCODING;
import static gate.cloud.io.IOConstants.PARAM_FILE_EXTENSION;
import static gate.cloud.io.IOConstants.PARAM_REPLACE_EXTENSION;
import static gate.cloud.io.IOConstants.VALUE_COMPRESSION_GZIP;
import static gate.cloud.io.IOConstants.VALUE_COMPRESSION_NONE;
import static gate.cloud.io.IOConstants.VALUE_COMPRESSION_SNAPPY;
import static gate.cloud.io.ListDocumentEnumerator.PARAM_FILE_NAME;
import gate.cloud.io.file.JSONOutputHandler;
import static gate.cloud.io.file.JSONOutputHandler.PARAM_ANNOTATION_TYPE_PROPERTY;
import static gate.cloud.io.file.JSONOutputHandler.PARAM_GROUP_ENTITIES_BY;

/**
* This class is a Batch Runner, i.e. it manages the execution of a batch job,
* specified by a {@link Batch} object.
*/
public class BatchRunner {
private static final Logger log = Logger.getLogger(BatchRunner.class);
//private static final long LOOP_WAIT = 5 * 60 * 1000;
private static final long LOOP_WAIT = 10 * 1000;

/**
* This class manages the execution of a batch job. It also exposes a
* {@link BatchJobData} interface that provides information about the
* execution progress.
*/
private class BatchHandler implements BatchJobData {
/**
* The document processor that runs the actual jobs.
*/
private DocumentProcessor processor;

/**
* The batch being run.
*/
private Batch batch;

private int totalDocs;
private int successDocs;
private int errorDocs;
private JobState state;
private String id;

/**
* The moment when the execution of this batch started.
*/
private long startTime;

/**
* The sum of {@link ProcessResult#getOriginalFileSize()} values for all 
* processed documents.
*/
private long totalBytes;

/**
* The sum of {@link ProcessResult#getDocumentLength()} values for all 
* processed documents.
*/    
private long totalChars;

/**
* The results queue for this batch.
*/
private BlockingQueue<ProcessResult> resultQueue;
/**
* The report file for this batch.
*/
private File reportFile;
/**
* Writer to write the report file.
*/
private XMLStreamWriter reportWriter;
/**
* Thread that pushes jobs into the DocumentProcessor for this batch.
*/
private Thread jobPusher;

private BatchHandler(final Batch batch) throws GateException, IOException {
successDocs = 0;
errorDocs = 0;
totalBytes = 0;
totalChars = 0;
this.batch = batch;
id = batch.getBatchId();
}

public void start() throws IOException, XMLStreamException, 
ResourceInstantiationException {
reportWriter = batch.getReportWriter();
// any existing report file has now been processed, so we know
// the correct number of unprocessed document IDs
totalDocs = batch.getUnprocessedDocumentIDs() == null ? -1 : batch.getUnprocessedDocumentIDs().length;
startTime = System.currentTimeMillis();
setState(JobState.RUNNING);
resultQueue = new LinkedBlockingQueue<ProcessResult>();
if(totalDocs != 0) {
final InputHandler inputHandler = batch.getInputHandler();
processor = new PooledDocumentProcessor(executor.getCorePoolSize());
processor.setController(batch.getGateApplication());
processor.setExecutor(executor);
processor.setInputHandler(inputHandler);
processor.setOutputHandlers(batch.getOutputs());
processor.setResultQueue(resultQueue);
processor.init();
log.info("Duplication finished");
System.gc();
log.info("Total allocated memory: "+(runtime.totalMemory()/MB)+"M");
log.info("Used memory: "+((runtime.totalMemory()-runtime.freeMemory())/MB)+"M");
duplicationFinishedTime = System.currentTimeMillis();
log.info("Duplication time (seconds): "+(duplicationFinishedTime-loadingFinishedTime)/1000.0);
jobPusher = new Thread(new Runnable() {
  public void run() {
    if(batch.getDocumentIDs() == null && inputHandler instanceof StreamingInputHandler) {
      ((StreamingInputHandler)inputHandler).startBatch(batch);
      processor.processStreaming();
      if(Thread.interrupted()) { return; }
    } else {
      for(DocumentID id : batch.getUnprocessedDocumentIDs()) {
	processor.processDocument(id);
	if(Thread.interrupted()) { return; }
      }
    }
    // shut down the executor and wait for it to terminate
    executor.shutdown();
    while(!executor.isTerminated()) {
      try {
	executor.awaitTermination(60L, TimeUnit.SECONDS);
      } catch(InterruptedException e) {
	// just re-interrupt ourselves and give up
	Thread.currentThread().interrupt();
      }
    }

    // now we know the batch is finished
    resultQueue.add(new EndOfBatchResult());
  }
}, "Batch \"" + getBatchId() + "\"-job-pusher");
jobPusher.start();  
} else {
  // no documents, so fire end of batch straight away
  resultQueue.add(new EndOfBatchResult());
}
}

/*
* (non-Javadoc)
* 
* @see gate.sam.batch.BatchJobData#getErrorDocumentCount()
*/
public int getErrorDocumentCount() {
return errorDocs;
}

/*
* (non-Javadoc)
* 
* @see gate.sam.batch.BatchJobData#getProcessedDocumentCount()
*/
public int getProcessedDocumentCount() {
return errorDocs + successDocs;
}

/*
* (non-Javadoc)
* 
* @see gate.sam.batch.BatchJobData#getRemainingDocumentCount()
*/
public int getRemainingDocumentCount() {
return (totalDocs < 0) ? -1 : totalDocs - errorDocs - successDocs;
}

/*
* (non-Javadoc)
* 
* @see gate.sam.batch.BatchJobData#getSuccessDocumentCount()
*/
public int getSuccessDocumentCount() {
return successDocs;
}

/*
* (non-Javadoc)
* 
* @see gate.sam.batch.BatchJobData#getTotalDocumentCount()
*/
public int getTotalDocumentCount() {
return totalDocs;
}

public long getTotalDocumentLength() {
return totalChars;
}

public long getTotalFileSize() {
return totalBytes;
}

/*
* (non-Javadoc)
* 
* @see gate.sam.batch.BatchJobData#isFinished()
*/
public JobState getState() {
return state;
}

private void setState(JobState state) {
this.state = state;
}

private void setErrorDocumentCount(int newCount) {
errorDocs = newCount;
}

private void setSuccessDocumentCount(int newCount) {
successDocs = newCount;
}

/* (non-Javadoc)
* @see gate.sam.batch.BatchJobData#getStartTime()
*/
public long getStartTime() {
return startTime;
}

/* (non-Javadoc)
* @see gate.sam.batch.BatchJobData#getBatchId()
*/
public String getBatchId() {
return id;
}


}
/**
* A SynchronousQueue in which offer delegates to put. ThreadPoolExecutor uses
* offer to run a new task. Using put instead means that when all the threads
* in the pool are occupied, execute will wait for one of them to become free,
* rather than failing to submit the task.
*/
public static class AlwaysBlockingSynchronousQueue
					    extends
					      SynchronousQueue<Runnable> {
/**
* Yes, I know this technically breaks the contract of BlockingQueue, but it
* works for this case.
*/
public boolean offer(Runnable task) {
try {
put(task);
} catch(InterruptedException e) {
return false;
}
return true;
}
}
/**
* A thread that runs continuously while the batch runner is active. Its role
* is to monitor the running jobs, collect process results, save the report
* files for each running batch, and shutdown the batch runner and/or Java
* process when all the batches have completed (if requested via the
* {@link BatchRunner#shutdownWhenFinished(boolean)} and
* {@link BatchRunner#exitWhenFinished(boolean)} methods).
*/
private class JobMonitor implements Runnable {
public void run() {
boolean finished = false;
while(!finished) {
long startTime = System.currentTimeMillis();
try {
  boolean jobsStillRunning = false;
  BatchHandler job = runningJob;
  if(job.getState() == JobState.RUNNING) {
    List<ProcessResult> results = new ArrayList<ProcessResult>();
    int resultsCount = job.resultQueue.drainTo(results);
    boolean finishedBatch = false;
    try {
      for(ProcessResult result : results) {
	if(result.getReturnCode() == ReturnCode.END_OF_BATCH) {
	  finishedBatch = true;
	} else {
	  long fileSize = result.getOriginalFileSize();
	  long docLength = result.getDocumentLength();
	  if(fileSize > 0) job.totalBytes += fileSize;
	  if(docLength > 0) job.totalChars += docLength;
	  
	  job.reportWriter.writeCharacters("\n");
	  Tools.writeResultToXml(result, job.reportWriter);
	  switch(result.getReturnCode()){
	    case SUCCESS:
	      job.successDocs++;
	      break;
	    case FAIL:
	      job.errorDocs++;
	      break;
	  }
	}
      }
      job.reportWriter.flush();
      if(finishedBatch) {
	job.setState(JobState.FINISHED);
	//close the <documents> element
	job.reportWriter.writeCharacters("\n");
	job.reportWriter.writeEndElement();
	//write the whole batch report element
	Tools.writeBatchResultToXml(job, job.reportWriter);
	job.reportWriter.close();
	// this will be null if no documents needed to be processed
	if(job.processor != null) job.processor.dispose();
      } else {
	jobsStillRunning = true;
      }
    } catch(XMLStreamException e) {
      log.error("Can't write to report file for batch " + job.getBatchId()
	      + ", shutting down batch", e);
      job.jobPusher.interrupt();
      job.setState(JobState.ERROR);
    }
  }
  // if all jobs finished and we should shutdown, then let's shutdown
  if(!jobsStillRunning) {
    shutdown();
    finished = true;
    if(exitWhenFinished) {
      System.exit(0);
    }
  }
  long remainingSleepTime = LOOP_WAIT
	  - (System.currentTimeMillis() - startTime);
  if(!finished && remainingSleepTime > 0)
    Thread.sleep(remainingSleepTime);
} catch(InterruptedException e) {
  // re-interrupt
  Thread.currentThread().interrupt();
  finished = true;
}
}
}
}

/**
* Creates a new BatchRunner, with a given number of threads.
* 
* @param numThreads
*/
public BatchRunner(int numThreads) {
// start the executors pool
// create the executor
// This is similar to an Executors.newFixedThreadPool, but instead
// of an unbounded queue for tasks, we block the caller when they
// try and execute a task and there are no threads available to run
// it.
executor = new ThreadPoolExecutor(numThreads, numThreads, 0L,
    TimeUnit.MILLISECONDS, new AlwaysBlockingSynchronousQueue());
}

public void exitWhenFinished(boolean flag) {
synchronized(this) {
this.exitWhenFinished = flag;
}
}

/**
* Stops this batch runner in an orderly fashion.
*/
public void shutdown() {
long processingFinishedTime = System.currentTimeMillis();
log.info("Processing finished");
System.gc();
log.info("Total allocated memory: "+(runtime.totalMemory()/MB)+"M");
log.info("Used memory: "+((runtime.totalMemory()-runtime.freeMemory())/MB)+"M");
// if we did not need to process anything then the duplicationFinishedTime will not have
// been set and be 0. In that case, set it to the loadingFinishedTime
if(duplicationFinishedTime==0) duplicationFinishedTime = loadingFinishedTime;
log.info("Processing time (seconds): "+(processingFinishedTime-duplicationFinishedTime)/1000.0);
log.info("Total time (seconds): "+(processingFinishedTime-startTime)/1000.0);
}

/**
* Stores data about the currently running batch jobs.
*/
private BatchHandler runningJob;
/**
* Executor used to run the tasks.
*/
private ThreadPoolExecutor executor;
/**
* Thread to monitor jobs.
*/
private Thread monitorThread;
/**
* A flag used to signal that the batch runner should exit the Java process
* when all currently running batches have completed.
*/
private boolean exitWhenFinished = true;

/**
* Starts executing the batch task specified by the provided parameter.
* 
* @param batch
*          a {@link Batch} object describing a batch job.
* @throws IllegalArgumentException
*           if there are problems with the provided batch specification (e.g.
*           the new batch has the same ID as a previous batch).
* @throws IOException
* @throws GateException
* @throws XMLStreamException
*/
public void runBatch(Batch batch) throws IllegalArgumentException,
  GateException, IOException, XMLStreamException {
synchronized(this) {
// record the new batch
String batchId = batch.getBatchId();
runningJob = new BatchHandler(batch);
// register the batch with JMX
      try {
        StandardMBean batchMBean = new StandardMBean(runningJob, BatchJobData.class);
        Hashtable<String, String> props = new Hashtable<String, String>();
        props.put("type", "Batch");
        props.put("id", ObjectName.quote(batch.getBatchId()));
        ObjectName name = ObjectName.getInstance("net.gatecloud", props);
        ManagementFactory.getPlatformMBeanServer().registerMBean(batchMBean, name);
      }
      catch(JMException e) {
        log.warn("Could not register batch with platform MBean server", e);
      }
      // queue the batch for execution
      runningJob.start();
      if(monitorThread == null) {
        // start the thread that monitors the batches, saves the reports, and
        // manages the automatic shutdown at the end of all jobs.
        monitorThread = new Thread(new JobMonitor(), this.getClass()
                .getCanonicalName()
                + "-Job-monitor");
        monitorThread.start();
      }
    }
  }

  static long startTime = System.currentTimeMillis();
  static long loadingFinishedTime;
  static long duplicationFinishedTime;
  static Runtime runtime = Runtime.getRuntime();
  static final int MB = 1024*1024;

  /**
   * Main entry point.  This can be invoked in one of two ways: the "legacy" 
   * mode which expects two parameters and a command line mode which allows
   * to specify various options and is more flexible. The "legacy" mode is intended
   * to be used with the gcp-cli program and should work exactly is it did before.
   * In legacy mode, this program expects two parameters, a number of threads and a batch
   * file location, and runs the batch in a thread pool of the specified size,
   * exiting when the batch is complete.
   * In command line mode, the commons-cli option parser is used .. see its
   * option definitions for which arguments exactly can be provided.
   */  
  public static void main(String[] args) {
    Options options = new Options();
    // there are two ways of how this program can get invoked: from the 
    // GCP-CLI program or from the command line. 
    // The GCP-CLI way to invoke is "nthreads configfile" with no 
    // option flags while the command line invokation always includes
    // requried option flags.  Both styles can use the -C and -p options
    // to load additional plugins.
    // Options for the command line invokation
    // TODO: may be useful to be able to override the default user config and
    // session files here?
    options.addOption("b","batchFile",true,"Batch file (required, replaces -i, -o, -x, -r, -I)");
    options.addOption("C","cache",true,"Maven cache directory to use when resolving plugins (optional, and may be specified more than once)");
    options.addOption("p","plugin",true,"GATE plugin to pre-load. Values of the form groupId:artifactId:version are treated as Maven plugins, anything else is tried first as a URL and if that fails then as a path relative to the GCP working directory (optional, and may be specified more than once)");
    options.addOption("i","inputDirectoryOrFile",true,"Input directory or file listing document IDs (required, unless -b given)");
    options.addOption("f","outputFormat",true,"Output format, optional, one of 'xml'|'gatexml', 'finf', 'ser', 'json', default is 'finf'");
    options.addOption("o","outputDirectory",true,"Output directory (not output if missing)");
    options.addOption("x","executePipeline",true,"Pipeline/application file to execute (required, unless -b given)");
    options.addOption("r","reportFile",true,"Report file (optional, default: report.xml");
    options.addOption("t","numberThreads",true,"Number of threads to use (required)");
    options.addOption("I","batchId",true,"Batch ID (optional, default: GCP");
    options.addOption("ci","compressedInput",false,"Input files are gzip-compressed (.gz)");
    options.addOption("co","compressedOutput",false,"Output files are gzip-compressed (.gz)");
    options.addOption("so","snappyOutput",false,"Output files are snappy-compressed (.snappy)");
    options.addOption("si","snappyInput",false,"Input files are snappy-compressed (.snappy)");
    options.addOption("h","help",false,"Print this help information");
    BasicParser parser = new BasicParser();
    
    int numThreads = 0;
    File batchFile = null;  
    boolean invokedByGcpCli = true;
    String outFormat = "finf";
    
    CommandLine line = null;
    try {
      line = parser.parse(options, args);
    } catch (Exception ex) {
      log.error("Could not parse command line arguments: "+ex.getMessage());
      System.exit(1);
    }
    String[] nonOptionArgs = line.getArgs();
    if(nonOptionArgs.length == 2) {
      numThreads = Integer.parseInt(nonOptionArgs[0]);
      batchFile = new File(nonOptionArgs[1]);
      if(!batchFile.exists()){
        log.error("The provided file (" + batchFile + ") does not exist!");
        System.exit(1);
      }
      if(!batchFile.isFile()){
        log.error("The provided file (" + batchFile + ") is not a file!");
        System.exit(1);
      }      
    } else {
      invokedByGcpCli = false;
      if(line.hasOption('h')) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("gcp-direct.sh [options]", options);
        System.exit(0);
      }
      if(!line.hasOption('t') || 
         (!line.hasOption('b') && (!line.hasOption('i') || !line.hasOption('x')))) {
        log.error("Required argument missing!");
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("gcp-direct.sh [options]", options);
        System.exit(1);
      }     
      if(line.hasOption('b')) {
        batchFile = new File(line.getOptionValue('b'));
      }
      if(line.hasOption('f')) {
        outFormat = line.getOptionValue('f');
        if(!outFormat.equals("xml") && !outFormat.equals("finf") && 
           !outFormat.equals("gatexml") &&
           !outFormat.equals("ser") && !outFormat.equals("json")) {
          log.error("Output format (option 'f') must be either 'json', 'ser', xml' or 'finf'");
          System.exit(1);
        }
      } // if we have option 'f', otherwise use the preset default
      numThreads = Integer.parseInt(line.getOptionValue('t'));
    }
    if(batchFile != null) {
      try {
        batchFile = batchFile.getCanonicalFile();
      } catch (IOException ex) {
        log.error("Could not get canonical file name for "+batchFile+": "+ex.getMessage());
        System.exit(1);
      }
    }

    // write PID file if requested
    String pidFileName = System.getProperty("gcp.pid.file");
    if(pidFileName != null) {
      // need to write PID to file.  We write it to a temporary file and then renameTo
      // the target file name, as this renaming is atomic on the platforms we care
      // about and the parent process will probably be watching to see when the PID
      // file exists.
      if(!Platform.isWindows()) {
        try {
          int pid = CLibrary.INSTANCE.getpid();
          File pidFile = new File(pidFileName);
          File tmpFile = File.createTempFile(pidFile.getName(), ".tmp", pidFile.getParentFile());
          FileUtils.write(tmpFile, String.valueOf(pid), "UTF-8");
          if(!tmpFile.renameTo(pidFile)) {
            throw new IOException("Couldn't rename " + tmpFile.getPath() + " to " + pidFile.getPath());
          }
        } catch(Throwable e) {
          // oh well, we tried
          log.warn("Error writing PID to " + pidFileName, e);
        }
      } else {
        log.warn("PID file not supported on Windows");
      }
    }
    File gcpHome = new File(System.getProperty("gcp.home", "."));
    log.info("Using GCP home directory "+gcpHome);

    // exit the whole GCP process if an Error (such as OOM) occurs, rather than
    // just killing the thread in which the error occurred.
    final Thread.UncaughtExceptionHandler defaultExceptionHandler =
      Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      
      public void uncaughtException(Thread t, Throwable e) {
        if(e instanceof Error) {
          e.printStackTrace();
          // special case - don't exit the process on ThreadDeath
          if(!(e instanceof ThreadDeath)) {
            System.exit(2);
          }
        }
        else if(defaultExceptionHandler != null) {
          defaultExceptionHandler.uncaughtException(t, e);
        }
      }
    });

    System.gc();
    log.info("Processors available: "+runtime.availableProcessors());
    log.info("Initial total allocated memory: "+(runtime.totalMemory()/MB)+"M");
    log.info("Initial used memory: "+((runtime.totalMemory()-runtime.freeMemory())/MB)+"M");

    try {
      File gateHome = null;
      File gcpGate = new File(gcpHome,"gate-home");
      if(System.getProperty("gate.home") != null) {
        gateHome = new File(System.getProperty("gate.home"));
        Gate.setGateHome(gateHome);
      }
      // use the site and user config files from gcp/gate-home even
      // if we are using another location for the actual GATE home
      // dir.
      Gate.setSiteConfigFile(new File(gcpGate,"gate.xml"));
      // we always set the user config file to the one in gcp gate
      Gate.setUserConfigFile(new File(gcpGate, "user-gate.xml"));
      // we always set the session file to a non-existent file in gcpGate
      // This should never get created anyway since the user config
      // file we use disables the session file.
      Gate.setUserSessionFile(new File(gcpGate, "empty.session"));
  
      // Add any Maven caches specified on the command line
      String[] cacheDirs = line.getOptionValues('C');
      if(cacheDirs != null) {
        for(String dir : cacheDirs) {
          gate.util.maven.Utils.addCacheDirectory(new File(dir));
        }
      }

      Gate.init();
      
      // If we run from gcp-direct, we try to load the Format_FastInfoset plugin.
      // This is needed if we write to format finf and the application does not load the plugin,
      // but also if we process finf format documents as input.
      // If we cannot load the plugin here, the thread will log the error and continue which
      // is good because the application could 
      // still load the plugin later - with normal GCP, this would be the only way to use that format.      
      if(invokedByGcpCli == false) {
        try {
          Gate.getCreoleRegister().registerPlugin(new Plugin.Maven("uk.ac.gate.plugins", "format-fastinfoset", gate.Main.version));
        } catch(Exception e) {
          log.warn("Couldn't load format-fastinfoset plugin, continuing anyway");
        }
      }
      
      // load any other plugins specified on the command line
      String[] pluginsToLoad = line.getOptionValues('p');
      if(pluginsToLoad != null) {
        Pattern mavenPluginPattern = Pattern.compile("([^/]+?):([^/]+?):([^/]+)");
        for(String plugin : pluginsToLoad) {
          plugin = plugin.trim();
          try {
            Matcher m = mavenPluginPattern.matcher(plugin);
            if(m.matches()) {
              // this looks like a Maven plugin
              log.info("Loading Maven plugin " + plugin);
              Gate.getCreoleRegister().registerPlugin(new Plugin.Maven(m.group(1), m.group(2), m.group(3)));
            } else {
              try {
                URL u = new URL(plugin);
                // succeeded in parsing as a URL, load that
                Gate.getCreoleRegister().registerPlugin(new Plugin.Directory(u));
              } catch(MalformedURLException e) {
                // not a URL, treat as a file
                File pluginFile = new File(plugin);
                Gate.getCreoleRegister().registerPlugin(new Plugin.Directory(pluginFile.toURI().toURL()));
              }
            }
          } catch(Exception e) {
            log.error("Failed to load plugin " + plugin, e);
            System.exit(1);
          }
        }
      }

      BatchRunner instance = new BatchRunner(numThreads);

      // depending on how we got invoked, create the batch from either 
      // the xml file or the info we got via the command line arguments
      Batch aBatch = null;
      if(invokedByGcpCli) {
        aBatch = XMLBatchParser.fromXml(batchFile);        
      } else {
        if(batchFile != null) {
          aBatch = XMLBatchParser.fromXml(batchFile);
        } else {
          // collect the various parts of the batch based on the command line
          // settings
          aBatch = new Batch();
          if(line.hasOption('b')) {            
            aBatch.setBatchId(line.getOptionValue('b'));
          }
          aBatch.setGateApplication(
                    (CorpusController)PersistenceManager.loadObjectFromFile(
                            new File(line.getOptionValue('x'))));
          String reportFileName;
          if(line.hasOption('r')) {
            reportFileName = line.getOptionValue('r');
          } else {
            reportFileName = "report.xml";
          }
          File reportFile = new File(reportFileName);
          reportFile = reportFile.getCanonicalFile();
          aBatch.setReportFile(reportFile);
          if(line.hasOption('I')) {
            aBatch.setBatchId(line.getOptionValue('I'));
          } else {
            aBatch.setBatchId("GcpBatchId");
          }
          // set the input Handler, depending on the value of the option "i":
          // If this points to a directory, we process all matching files in that
          // directory, if it points to a file we process all files listed in
          // that file by interpreting each line as a file path relative to 
          // the directory where the specified file is located in.
          String fileOrDir = line.getOptionValue('i');
          File fileOrDirFile = new File(fileOrDir);
          if(!fileOrDirFile.exists()) {
            throw new RuntimeException("ERROR file or directory does not exist: "+fileOrDirFile.getAbsolutePath());
          }
          String inputHandlerClassName = "gate.cloud.io.file.FileInputHandler";
          Map<String,String> configData = new HashMap<String, String>();
          if(fileOrDirFile.isDirectory()) {
            configData.put(PARAM_DOCUMENT_ROOT, fileOrDir);
          } else {
            // if we have a file, use the parent directory
            configData.put(PARAM_DOCUMENT_ROOT, fileOrDirFile.getParent());
          }
          if(line.hasOption("ci")) {
            configData.put(PARAM_COMPRESSION,VALUE_COMPRESSION_GZIP);            
          } else if(line.hasOption("si"))  {
            configData.put(PARAM_COMPRESSION,VALUE_COMPRESSION_SNAPPY);
          } else  {
            configData.put(PARAM_COMPRESSION,VALUE_COMPRESSION_NONE);
          }
          configData.put(PARAM_ENCODING, "UTF-8");
          configData.put(PARAM_FILE_EXTENSION,"");
          Class<? extends InputHandler> inputHandlerClass =
                Class.forName(inputHandlerClassName, true, Gate.getClassLoader())
                        .asSubclass(InputHandler.class);
          InputHandler inputHandler = inputHandlerClass.newInstance();
          inputHandler.config(configData);
          inputHandler.init();
          // log.info("Have input handler: "+inputHandler);
          aBatch.setInputHandler(inputHandler);
          // set the output Handler, but only if the option -o has been specified,
          // otherwise, do not set an output handler (run for side-effects only)
          List<OutputHandler> outHandlers = new ArrayList<OutputHandler>();
          if(line.hasOption('o')) {
            String outputHandlerClassName = null;
            configData = new HashMap<String, String>();
            String outExt = ".finf";
            if(outFormat.equals("finf")) {
              outputHandlerClassName = "gate.cloud.io.file.FastInfosetOutputHandler";
            } else if(outFormat.equals("xml")) {
              outExt = ".xml";
              outputHandlerClassName = "gate.cloud.io.file.GATEStandOffFileOutputHandler";
            } else if(outFormat.equals("gatexml")) {
              outExt = ".gatexml";
              outputHandlerClassName = "gate.cloud.io.file.GATEStandOffFileOutputHandler";
            } else if(outFormat.equals("ser")) {
              outExt = ".ser";
              outputHandlerClassName = "gate.cloud.io.file.SerializedObjectOutputHandler";
            } else if(outFormat.equals("json")) {
              outExt = ".json";
              outputHandlerClassName = "gate.cloud.io.file.JSONOutputHandler";
              configData.put(PARAM_GROUP_ENTITIES_BY, "set");
              configData.put(PARAM_ANNOTATION_TYPE_PROPERTY, "annType");
            } else {
              // cannot get here, option contents is checked earlier...
            }
            configData.put(IOConstants.PARAM_DOCUMENT_ROOT, line.getOptionValue('o'));
            
            if(line.hasOption("co")) {
              configData.put(PARAM_COMPRESSION,VALUE_COMPRESSION_GZIP);            
              outExt = outExt + ".gz";
            } else if(line.hasOption("so")) {
              configData.put(PARAM_COMPRESSION,VALUE_COMPRESSION_SNAPPY);
              outExt = outExt + ".snappy";
            } else {
              configData.put(PARAM_COMPRESSION,VALUE_COMPRESSION_NONE);
            }
            configData.put(PARAM_FILE_EXTENSION,outExt);
            configData.put(PARAM_ENCODING, "UTF-8");
            configData.put(PARAM_REPLACE_EXTENSION, "true");
            Class<? extends OutputHandler> ouputHandlerClass =
            Class.forName(outputHandlerClassName, true, Gate.getClassLoader())
                 .asSubclass(OutputHandler.class);
            OutputHandler outHandler = ouputHandlerClass.newInstance();
            outHandler.config(configData);
            List<AnnotationSetDefinition> asDefs = new ArrayList<AnnotationSetDefinition>();
            outHandler.setAnnSetDefinitions(asDefs);
            outHandler.init();
            // log.info("Have output handler: "+outHandler);            
            outHandlers.add(outHandler);
          } else { // if option -o is given
            log.info("WARNING: no option -o, processed documents are discarded!");
          }
          aBatch.setOutputHandlers(outHandlers);
          String enumeratorClassName = null;
          configData = new HashMap<String, String>();
          if(fileOrDirFile.isDirectory()) {
            log.info("Enumerating all file IDs in directory: "+fileOrDirFile.getAbsolutePath());
            enumeratorClassName = "gate.cloud.io.file.FileDocumentEnumerator";
            configData.put(PARAM_DOCUMENT_ROOT, line.getOptionValue('i'));           
          } else {
            log.info("Reading file IDs from file: "+fileOrDirFile.getAbsolutePath());
            enumeratorClassName = "gate.cloud.io.ListDocumentEnumerator";
            configData.put(PARAM_BATCH_FILE_LOCATION,new File(".").getAbsolutePath());
            configData.put(PARAM_FILE_NAME, fileOrDir);
            configData.put(PARAM_ENCODING,"UTF-8");
          }
          Class<? extends DocumentEnumerator> enumeratorClass =
                Class.forName(enumeratorClassName, true, Gate.getClassLoader())
                        .asSubclass(DocumentEnumerator.class);
          DocumentEnumerator enumerator = enumeratorClass.newInstance();
          enumerator.config(configData);
          enumerator.init();
          // TODO: this should really not be done like this! 
          // Instead of reading the docIds in all at once, they should 
          // get streamed to the workers on demand, if at all possible?
          List<DocumentID> docIds = new LinkedList<DocumentID>();
          while(enumerator.hasNext()) {
            DocumentID id = enumerator.next();
            // log.info("Adding document: "+id);
            docIds.add(id);
          } 
          log.info("Number of documents found: "+docIds.size());
          aBatch.setDocumentIDs(docIds.toArray(new DocumentID[docIds.size()]));
          aBatch.init();
        }    
      }
      log.info("Loading finished");
      log.info("Total allocated memory: "+(runtime.totalMemory()/MB)+"M");
      log.info("Used memory: "+((runtime.totalMemory()-runtime.freeMemory())/MB)+"M");
      loadingFinishedTime = System.currentTimeMillis();
      log.info("Loading time (seconds): "+(loadingFinishedTime-startTime)/1000.0);
      log.info("Launching batch:\n" + aBatch);
      
      // if this is run from gcp-direct and there are no unprocessed documents, do nothing
      if(!invokedByGcpCli && aBatch.getUnprocessedDocumentIDs() != null && aBatch.getUnprocessedDocumentIDs().length == 0) {
        log.info("No documents to process, exiting");
      } else {
        instance.runBatch(aBatch);
        instance.exitWhenFinished(true);
      }
    } catch(Exception e) {
      log.error("Error starting up batch " + batchFile, e);
      System.exit(1);
    }
  
  }
  
  
}
