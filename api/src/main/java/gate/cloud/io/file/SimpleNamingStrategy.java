/*
 *  SimpleNamingStrategy.java
 *  Copyright (c) 2007-2011, The University of Sheffield.
 *
 *  This file is part of GCP (see http://gate.ac.uk/), and is free
 *  software, licenced under the GNU Affero General Public License,
 *  Version 3, November 2007.
 *
 *
 *  $Id: SimpleNamingStrategy.java 18875 2015-08-17 23:12:19Z johann_p $ 
 */
package gate.cloud.io.file;

import static gate.cloud.io.IOConstants.PARAM_BATCH_FILE_LOCATION;
import static gate.cloud.io.IOConstants.PARAM_DOCUMENT_ROOT;
import static gate.cloud.io.IOConstants.PARAM_FILE_EXTENSION;
import static gate.cloud.io.IOConstants.PARAM_REPLACE_EXTENSION;
import gate.cloud.batch.DocumentID;
import gate.util.GateException;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class SimpleNamingStrategy implements NamingStrategy {
  
  /**
   * The directory containing the batch specification file, or <code>null</code>
   * if the batch specification did not come from a file.
   */
  protected File batchDir;
  
  /**
   * The top-level directory containing the document files.   
   */
  protected URI documentRoot;
  
  /**
   * The file extension that should be appended to the ID.
   */  
  protected String fileExtension;
  
  /**
   * If this is true, any given extension is used to replace any existing file extension.
   * If this configuration option is true, then PARAM_FILE_EXTENSION is not empty and the 
   * file name already does have an extension (something following a dot but not including a dot),
   * then the existing extension is replaced with the new extension. 
   * <p>
   * Currently, if the input extension is .gz then if this is preced by another extension,
   * both extensions are replaced; for example if the name is filename.xml.gz then ".xml.gz" 
   * is replaced. 
   */
  protected boolean replaceExtension = false;
  
  protected boolean isOutput = false;

  public void config(boolean isOutput, Map<String, String> configData) throws IOException,
          GateException {
    
    this.isOutput = isOutput;
    //doc root
    String docRootStr = configData.get(PARAM_DOCUMENT_ROOT);
    if(docRootStr == null || docRootStr.trim().length() == 0){
      throw new IllegalArgumentException(
              "No value was provided for the required parameter \"" +
              PARAM_DOCUMENT_ROOT + "\"!");
    }
    String batchFileStr = configData.get(PARAM_BATCH_FILE_LOCATION);
    if(batchFileStr != null) {
      batchDir = new File(batchFileStr).getParentFile();
    }
    File documentRootFile = new File(docRootStr);
    if(!documentRootFile.isAbsolute()) {
      documentRootFile = new File(batchDir, docRootStr);
    }
    
    // sanity checks
    if(isOutput) {
      if(documentRootFile.exists()) {
        if(documentRootFile.isDirectory()) {
          // all OK
        } else {
          throw new IOException(documentRootFile.getAbsolutePath()
                  + " already exists and is not a directory!");
        }
      } else {
        // try and create it
        if(!documentRootFile.mkdirs()) { throw new IOException("Could not create "
                + documentRootFile.getAbsolutePath() + " directory!"); }
      } 
    } else {
      if(!documentRootFile.exists()){
        throw new IllegalArgumentException(
                "Directory \"" + documentRootFile + 
                "\", provided as value for required parameter \"" +
                PARAM_DOCUMENT_ROOT + "\", does not exist!");
      }
      if(!documentRootFile.isDirectory()){
        throw new IllegalArgumentException(
                "File \"" + documentRootFile + 
                "\", provided as value for required parameter \"" +
                PARAM_DOCUMENT_ROOT + "\", is not a directory!");
      }
    }
    
    documentRoot = documentRootFile.toURI();
    // sanity check - directory URIs must end with a slash
    if(!documentRoot.toString().endsWith("/")) {
      documentRoot = URI.create(documentRoot.toString() + "/");
    }
    
    //extension
    fileExtension = configData.get(PARAM_FILE_EXTENSION);
    
    replaceExtension = Boolean.parseBoolean(configData.get(PARAM_REPLACE_EXTENSION));
    
  }

  public File toFile(DocumentID id) throws IOException {
    try {
      String path = relativePathFor(id);
      if(fileExtension != null && !fileExtension.equals("")) {
        if(isOutput && replaceExtension) {
          // first off, strip away any .gz extension
          if(path.endsWith(".gz") && path.length() > 3) {
            path = path.substring(0,path.length()-3);
          }
          int dotIndex = path.lastIndexOf('.');
          // it is only an extension if it is not the first character          
          if(dotIndex > 0) {
            // replace the existing extension with the new one
            path = path.substring(0,dotIndex) + fileExtension;
          } else {
            // if it is a file without a dot or starting with a dot, append the new extension
            path += fileExtension;
          }
        } else {
          path += fileExtension;
        }
      }
      URI u = new URI(null, null, path, null);
      URI docUri = documentRoot.resolve(u);
      return new File(docUri);
    } catch(URISyntaxException e) {
      throw (IOException)new IOException(
              "Could not determine file for ID " + id).initCause(e);
    }
  }
  
  /**
   * Converts a document ID into relative file path.  The value returned
   * from this method will have the configured file extension (if any)
   * appended to it and will then be treated as a relative URI and
   * resolved against the configured document root.
   * 
   * This implementation simply returns the document ID unchanged,
   * subclasses may wish to implement more sophisticated mappings.
   * 
   * @param id the document ID
   * @return a path suitable for use as a relative URI.
   */
  protected String relativePathFor(DocumentID id) {
    return id.getIdText();
  }
  
  public String toString() {
    StringBuilder text = new StringBuilder();
    text.append("\n\t\tClass:         " + this.getClass().getName() + "\n");
    text.append("\t\tDocument root:  " + documentRoot + "\n");
    text.append("\t\tFile extension: " + fileExtension+ "\n");
    text.append("\t\tReplace extension: " + replaceExtension);
    return text.toString();
  }
}
