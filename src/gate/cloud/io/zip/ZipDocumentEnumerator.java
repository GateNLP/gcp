/*
 *  ZipDocumentEnumerator.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: ZipDocumentEnumerator.java 17349 2014-02-19 18:02:24Z ian_roberts $ 
 */
package gate.cloud.io.zip;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_FILE_NAME_ENCODING;
import static gate.cloud.io.IOConstants.PARAM_SOURCE_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_ZIP_FILE_LOCATION;
import gate.cloud.io.AntBasedDocumentEnumerator;
import gate.util.GateException;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.ZipFileSet;

/**
 * Enumerator that enumerates entries in a zip file.
 */
public class ZipDocumentEnumerator extends AntBasedDocumentEnumerator {

  /**
   * The zip file containing the document files.
   */
  protected File zipFile;

  /**
   * The directory containing the batch specification file, or
   * <code>null</code> if the batch specification did not come from a
   * file.
   */
  protected File batchDir;
  
  /**
   * The default encoding for file names in the zip file.  We read
   * zip entries using the Ant ZipFile implementation rather than
   * the java.util.zip one, so it will look for the flags that
   * specify that a certain entry name is UTF-8, but if these flags
   * are not set, the default encoding here will be used.  If
   * unspecified, the platform default is used.
   */
  protected String fileNameEncoding;

  @SuppressWarnings("deprecation")
  public void config(Map<String, String> configData) throws GateException,
          IOException {
    super.config(configData);

    // zip file
    // Backwards compatibility
    if(configData.containsKey(PARAM_ZIP_FILE_LOCATION)
            && !configData.containsKey(PARAM_SOURCE_FILE_LOCATION)) {
      configData.put(PARAM_SOURCE_FILE_LOCATION,
              configData.get(PARAM_ZIP_FILE_LOCATION));
    }
    String zipFileStr = configData.get(PARAM_SOURCE_FILE_LOCATION);
    if(zipFileStr == null || zipFileStr.trim().length() == 0) {
      throw new IllegalArgumentException(
              "No value was provided for the required parameter \""
                      + PARAM_SOURCE_FILE_LOCATION + "\"!");
    }
    String batchFileStr = configData.get(PARAM_BATCH_FILE_LOCATION);
    if(batchFileStr != null) {
      batchDir = new File(batchFileStr).getParentFile();
    }
    zipFile = new File(zipFileStr);
    if(!zipFile.isAbsolute()) {
      zipFile = new File(batchDir, zipFileStr);
    }
    if(!zipFile.exists()) {
      throw new IllegalArgumentException("File \"" + zipFile
              + "\", provided as value for required parameter \""
              + PARAM_SOURCE_FILE_LOCATION + "\", does not exist!");
    }
    if(!zipFile.isFile()) {
      throw new IllegalArgumentException("File \"" + zipFile
              + "\", provided as value for required parameter \""
              + PARAM_SOURCE_FILE_LOCATION + "\", is not a file!");
    }
    // file name encoding
    fileNameEncoding = configData.get(PARAM_FILE_NAME_ENCODING);
  }

  protected FileSet createFileSet() {
    ZipFileSet fs = new ZipFileSet();
    fs.setSrc(zipFile);
    fs.setEncoding(fileNameEncoding);
    return fs;
  }

}
