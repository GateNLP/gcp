/*
 *  AbstractFileOutputHandler.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: AbstractFileOutputHandler.java 19679 2016-10-14 16:40:39Z johann_p $ 
 */
package gate.cloud.io.file;

import gate.Gate;
import gate.cloud.batch.DocumentID;
import gate.cloud.io.AbstractOutputHandler;
import gate.cloud.io.IOConstants;
import gate.util.GateException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static gate.cloud.io.IOConstants.*;
import org.xerial.snappy.SnappyOutputStream;

public abstract class AbstractFileOutputHandler extends AbstractOutputHandler {
  /**
   * The file naming strategy.
   */
  protected NamingStrategy namingStrategy;
  
  /**
   * What type of compression should be used
   */
  protected String compression;

  /**
   * The encoding used when writing the document content.
   */
  protected String encoding;

  /**
   * Sets the various internal flags (made available as protected fields) based
   * on the config map. Subclasses that need to extract their own values from
   * the configuration map, should override this method, parse the config
   * values, then call <code>super.config()</code> providing the config data.
   */
  protected void configImpl(Map<String, String> configData) throws IOException,
          GateException {
    // naming strategy
    String namingStrategyClassName = configData.get(PARAM_NAMING_STRATEGY);
    if(namingStrategyClassName == null || namingStrategyClassName.length() == 0) {
      namingStrategyClassName = SimpleNamingStrategy.class.getName();
    }
    try {
      Class<? extends NamingStrategy> namingStrategyClass =
        Class.forName(namingStrategyClassName, true, Gate.getClassLoader())
               .asSubclass(NamingStrategy.class);
      namingStrategy = namingStrategyClass.newInstance();
      namingStrategy.config(true, configData);
    } catch(Exception e) {
      throw new GateException(
              "Could not instantiate specified naming strategy", e);
    }

    // get the compression value
    compression = configData.get(PARAM_COMPRESSION);
    if(compression == null) {
      // default
      compression = IOConstants.VALUE_COMPRESSION_NONE;
    }
    // get the encoding value
    encoding = configData.get(PARAM_ENCODING);
  }

  /**
   * Utility method for subclasses that provides an open output stream that can
   * be used to write data to a file. The file to be written is determined from
   * the {@link #documentRoot}, the <code>docId</code> parameter, and the
   * <code>extension</code> parameter, following the following rules:
   * <ul>
   * <li>the file will be a descendant of the directory denoted by
   * <code>baseDirectory</code></li>
   * <li>the <code>docId</code> string is treated as a &quot;/&quot;-separated
   * path, inside the base directory, with the last element being the file name.
   * </li>
   * <li>if the <code>extension</code> parameter is <code>null</code>, then the
   * value of the <code>fileExtension<code> field (obtained from the 
   *  handler configuration) is used instead.</li>
   * <li>if the <code>extension</code> value is non-<code>null</code> , then
   * that value is appended to the file name.</li>
   * </ul>
   * The file thus identified is then opened for writing. If the
   * <code>compression</code> field is set to a supported compression algorithm,
   * then the file output stream is wrapped into an output stream that performs
   * the appropriate compression. It is the responsibility of the client code to
   * close the output stream provided after it is no longer required.
   * 
   * @param docId
   *          the identifier for the document
   * @param extension
   *          the file extension to be used. Supply an empty string value to
   *          suppress extension, or <code>null</code> to use the default value
   *          (obtained from the configuration data of this handler).
   * @return an output stream to which data can be written.
   */
  protected OutputStream getFileOutputStream(DocumentID docId)
          throws IOException {
    File docFile = namingStrategy.toFile(docId);
    File parent = docFile.getParentFile();
    if(!parent.exists()) {
      // target directory does not exist: create it
      parent.mkdirs();
      // check again
      if(!parent.exists()) {
        // could not create target dir
        throw new IOException("Could not create " + parent.getAbsolutePath()
                + " destination directory!");
      }
    }
    OutputStream os = new FileOutputStream(docFile);
    // apply compression is required.
    if(compression != null) {
      if(compression.equals(VALUE_COMPRESSION_GZIP)) {
        os = new GZIPOutputStream(os);
      } else if(compression.equals(VALUE_COMPRESSION_SNAPPY)) {
        os = new SnappyOutputStream(os);
      }
    }
    return new BufferedOutputStream(os);
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder();
    text.append("\n\tClass:         " + this.getClass().getName() + "\n");
    text.append("\tNaming strategy: " + namingStrategy + "\n");
    text.append("\tCompression:     " + compression + "\n");
    text.append("\tEncoding:        " + encoding + "\n");
    text.append("\tAnnotations:     " + getAnnSetDefinitions());
    return text.toString();
  }
}
